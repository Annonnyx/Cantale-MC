package fr.cantale.plugin.vote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cantale.plugin.CantalePlugin;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Polling de l'API officielle Serveur-Prive.net (site 2).
 *
 * <p>Votifier n'est pas le canal prévu par ce listing. L'API est en lecture seule
 * (GET only, pas de webhook / claim documenté) :
 * <ul>
 *   <li>{@code GET /v1/servers/{token}/votes} — operationId {@code serverVotesList}
 *       (canal de récompense)</li>
 *   <li>{@code GET /v1/servers/{token}/votes/{ip}} — vérif IP documentée sur la
 *       page marketing, mais absente de l'OpenAPI developers et 404 en live.
 *       Si 404, le check IP est désactivé pour la session.</li>
 * </ul>
 * Auth : le token dashboard (onglet Paramètres) est dans le <strong>chemin</strong>,
 * pas en header Bearer.
 *
 * <p>HTTP uniquement en async ({@link #runTaskTimerAsynchronously}).
 */
public final class ServeurPriveVotePoller extends BukkitRunnable {

    static final String SITE_SERVICE = "Serveur-Prive.net";
    static final String TOKEN_CONFIG_KEY = "serveur-prive.net";

    private static final int MAX_PAGES = 10;
    private static final String[] USERNAME_KEYS = {
            "username", "user", "name", "pseudo", "player", "mc_username", "nickname"
    };
    private static final String[] ID_KEYS = {"id", "uuid", "vote_id", "voteId"};
    private static final String[] IP_KEYS = {"ip", "address", "user_ip", "ipv4", "ipv6"};
    private static final String[] TIME_KEYS = {
            "voted_at", "votedAt", "created_at", "createdAt", "timestamp", "date", "time"
    };

    private final CantalePlugin plugin;
    private final VoteListener voteListener;
    private final VoteDAO voteDAO;
    private final OkHttpClient httpClient;

    private boolean started;
    private long lastErrorLogAt;
    private int consecutiveErrors;
    /** Session-only : 404 sur {@code /votes/{ip}} → on s'en tient à {@code serverVotesList}. */
    private volatile boolean ipCheckDisabled;

    public ServeurPriveVotePoller(CantalePlugin plugin, VoteListener voteListener) {
        this.plugin = plugin;
        this.voteListener = voteListener;
        this.voteDAO = plugin.getVoteDAO();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("vote.serveur-prive.enabled", true)) {
            plugin.getLogger().info("[Vote] Serveur-Prive API : polling désactivé (vote.serveur-prive.enabled=false).");
            return;
        }
        String token = resolveToken();
        if (token.isEmpty()) {
            plugin.getLogger().warning("[Vote] Serveur-Prive API : token manquant. "
                    + "Colle le token dashboard (Paramètres) dans vote.tokens.serveur-prive.net "
                    + "ou vote.serveur-prive.api-token — voir docs/Votes.md.");
            return;
        }
        int seconds = Math.max(30, plugin.getConfig().getInt("vote.serveur-prive.poll-interval-seconds", 60));
        long ticks = seconds * 20L;
        runTaskTimerAsynchronously(plugin, 20L * 20L, ticks);
        started = true;
        plugin.getLogger().info("[Vote] Serveur-Prive API : polling toutes les " + seconds
                + "s (GET /v1/servers/{token}/votes). Indépendant de Votifier — docs/Votes.md.");
    }

    public void stop() {
        if (started) {
            cancel();
            started = false;
        }
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    @Override
    public void run() {
        try {
            pollVoteList();
            if (!ipCheckDisabled
                    && plugin.getConfig().getBoolean("vote.serveur-prive.check-online-ips", false)) {
                pollOnlinePlayerIps();
            }
            consecutiveErrors = 0;
        } catch (Exception e) {
            consecutiveErrors++;
            logErrorThrottled("erreur polling Serveur-Prive : " + e.getMessage(), e);
        }
    }

    private void pollVoteList() throws IOException {
        String token = resolveToken();
        if (token.isEmpty()) return;

        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = votesListUrl(token, page);
            JsonObject root = getJson(url, VoteEndpoint.LIST);
            if (root == null) return;
            if (!isSuccess(root)) {
                logErrorThrottled("liste votes refusée : " + extractError(root), null);
                return;
            }
            List<ApiVote> votes = extractVotes(root);
            for (ApiVote vote : votes) {
                considerVote(vote);
            }
            if (votes.isEmpty() || !hasNextPage(root, page, votes.size())) {
                break;
            }
        }
    }

    private void pollOnlinePlayerIps() {
        String token = resolveToken();
        if (token.isEmpty() || ipCheckDisabled) return;

        List<PlayerSnapshot> online = snapshotOnlinePlayers();
        for (PlayerSnapshot player : online) {
            if (ipCheckDisabled) return;
            try {
                JsonObject root = getJson(voteByIpUrl(token, player.ip()), VoteEndpoint.BY_IP);
                if (root == null || !isSuccess(root)) continue;
                ApiVote vote = extractSingleVote(root.get("data"), player.ip(), player.name());
                if (vote == null) continue;
                if (vote.username == null || vote.username.isBlank()) {
                    vote = new ApiVote(player.name(), vote.votedAtSeconds, player.ip(), vote.externalId);
                }
                considerVote(vote);
            } catch (Exception e) {
                plugin.getLogger().fine("[Vote] Serveur-Prive check IP " + player.name() + " : " + e.getMessage());
            }
        }
    }

    private List<PlayerSnapshot> snapshotOnlinePlayers() {
        List<PlayerSnapshot> snapshot = new ArrayList<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String ip = publicIpOf(player);
                    if (ip != null) snapshot.add(new PlayerSnapshot(player.getName(), ip));
                }
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().fine("[Vote] Serveur-Prive : timeout snapshot joueurs en ligne.");
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        return List.copyOf(snapshot);
    }

    private void considerVote(ApiVote vote) {
        if (vote == null || vote.username == null || vote.username.isBlank()) return;
        if (vote.username.length() > 16) return;

        long now = System.currentTimeMillis() / 1000L;
        int maxAgeMin = Math.max(30, plugin.getConfig().getInt("vote.serveur-prive.max-age-minutes", 120));
        boolean tooOld = vote.votedAtSeconds > 0 && vote.votedAtSeconds < now - (maxAgeMin * 60L);

        String username = vote.username;
        String address = vote.address == null ? "" : vote.address;
        String externalId = vote.externalId;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (tooOld) {
                    voteDAO.tryMarkExternalVoteProcessed(SITE_SERVICE, externalId, username);
                    return;
                }
                if (!voteDAO.tryMarkExternalVoteProcessed(SITE_SERVICE, externalId, username)) {
                    return;
                }
                voteListener.handleVote(username, SITE_SERVICE, address);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Vote] Serveur-Prive : échec grant pour " + username, e);
            }
        });
    }

    String resolveToken() {
        String override = plugin.getConfig().getString("vote.serveur-prive.api-token", "");
        if (override != null && !override.isBlank()) return override.trim();
        String fromTokens = plugin.getConfig().getString("vote.tokens." + TOKEN_CONFIG_KEY, "");
        return fromTokens == null ? "" : fromTokens.trim();
    }

    private String apiBase() {
        String base = plugin.getConfig().getString("vote.serveur-prive.api-url", "https://serveur-prive.net/api");
        if (base == null || base.isBlank()) base = "https://serveur-prive.net/api";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base.trim();
    }

    private String votesListUrl(String token, int page) {
        HttpUrl parsed = HttpUrl.parse(apiBase());
        if (parsed == null) {
            return apiBase() + "/v1/servers/" + token + "/votes?page=" + page;
        }
        return parsed.newBuilder()
                .addPathSegments("v1/servers")
                .addPathSegment(token)
                .addPathSegment("votes")
                .setQueryParameter("page", Integer.toString(page))
                .build()
                .toString();
    }

    private String voteByIpUrl(String token, String ip) {
        HttpUrl parsed = HttpUrl.parse(apiBase());
        if (parsed == null) {
            return apiBase() + "/v1/servers/" + token + "/votes/" + ip;
        }
        return parsed.newBuilder()
                .addPathSegments("v1/servers")
                .addPathSegment(token)
                .addPathSegment("votes")
                .addPathSegment(ip)
                .build()
                .toString();
    }

    private JsonObject getJson(String url, VoteEndpoint endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "CantalePlugin/1.0 (Serveur-Prive vote poller)")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            int code = response.code();
            if (!response.isSuccessful()) {
                if (endpoint == VoteEndpoint.BY_IP && isMissingIpEndpoint(code)) {
                    disableIpCheck();
                    return null;
                }
                logErrorThrottled("HTTP " + code + " sur " + endpointLabel(endpoint), null);
                return null;
            }
            if (body.isBlank()) return null;
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                logErrorThrottled("réponse JSON inattendue (non-objet) sur " + endpointLabel(endpoint), null);
                return null;
            }
            return parsed.getAsJsonObject();
        }
    }

    private void disableIpCheck() {
        if (ipCheckDisabled) return;
        ipCheckDisabled = true;
        plugin.getLogger().info("[Vote] Serveur-Prive API : " + endpointLabel(VoteEndpoint.BY_IP)
                + " répond 404 (route absente). Check IP désactivé pour cette session ; "
                + "récompenses via " + endpointLabel(VoteEndpoint.LIST) + " uniquement.");
    }

    static boolean isMissingIpEndpoint(int httpCode) {
        return httpCode == 404;
    }

    static String endpointLabel(VoteEndpoint endpoint) {
        return endpoint == VoteEndpoint.BY_IP
                ? "GET /v1/servers/{token}/votes/{ip}"
                : "GET /v1/servers/{token}/votes";
    }

    private static boolean isSuccess(JsonObject root) {
        if (root.has("success") && root.get("success").isJsonPrimitive()) {
            try {
                return root.get("success").getAsBoolean();
            } catch (Exception ignored) {
                return "true".equalsIgnoreCase(root.get("success").getAsString())
                        || "1".equals(root.get("success").getAsString());
            }
        }
        return root.has("data");
    }

    private static String extractError(JsonObject root) {
        if (root.has("message") && root.get("message").isJsonPrimitive()) {
            return root.get("message").getAsString();
        }
        return "réponse API sans message";
    }

    private List<ApiVote> extractVotes(JsonObject root) {
        List<ApiVote> out = new ArrayList<>();
        JsonElement data = root.get("data");
        if (data == null || data.isJsonNull()) return out;
        if (data.isJsonArray()) {
            collectFromArray(data.getAsJsonArray(), out);
            return out;
        }
        if (data.isJsonObject()) {
            JsonObject obj = data.getAsJsonObject();
            for (String key : List.of("votes", "items", "data", "results")) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    collectFromArray(obj.getAsJsonArray(key), out);
                    return out;
                }
            }
            ApiVote single = extractSingleVote(obj, "", null);
            if (single != null) out.add(single);
        }
        return out;
    }

    private void collectFromArray(JsonArray array, List<ApiVote> out) {
        for (JsonElement el : array) {
            ApiVote vote = extractSingleVote(el, "", null);
            if (vote != null) out.add(vote);
        }
    }

    private ApiVote extractSingleVote(JsonElement data, String fallbackIp, String fallbackName) {
        if (data == null || data.isJsonNull()) return null;
        if (!data.isJsonObject()) return null;
        JsonObject obj = data.getAsJsonObject();

        String username = firstString(obj, USERNAME_KEYS);
        if (username == null || username.isBlank()) username = fallbackName;
        if (username != null) username = username.trim();

        String address = firstString(obj, IP_KEYS);
        if (address == null || address.isBlank()) address = fallbackIp;

        long votedAt = firstTimestamp(obj, TIME_KEYS);
        String id = firstString(obj, ID_KEYS);
        String externalId = (id != null && !id.isBlank())
                ? id.trim()
                : fingerprint(username, votedAt, address);

        if (username == null || username.isBlank()) return null;
        return new ApiVote(username, votedAt, address, externalId);
    }

    private static String fingerprint(String username, long votedAt, String address) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return name + ":" + votedAt + ":" + (address == null ? "" : address);
    }

    private static String firstString(JsonObject obj, String[] keys) {
        for (String key : keys) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                String v = el.getAsString();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private static long firstTimestamp(JsonObject obj, String[] keys) {
        for (String key : keys) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
            long parsed = parseTimestamp(obj.get(key));
            if (parsed > 0) return parsed;
        }
        return 0L;
    }

    static long parseTimestamp(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0L;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            double n = el.getAsDouble();
            if (n > 1_000_000_000_000D) return (long) (n / 1000D);
            return (long) n;
        }
        if (!el.isJsonPrimitive()) return 0L;
        String raw = el.getAsString();
        if (raw == null || raw.isBlank()) return 0L;
        try {
            long n = Long.parseLong(raw.trim());
            if (n > 1_000_000_000_000L) return n / 1000L;
            return n;
        } catch (NumberFormatException ignored) {
        }
        try {
            return Instant.parse(raw.trim()).getEpochSecond();
        } catch (DateTimeParseException ignored) {
        }
        return 0L;
    }

    private static boolean hasNextPage(JsonObject root, int page, int pageSize) {
        JsonObject meta = asObject(root.get("meta"));
        if (meta == null) meta = asObject(root.get("pagination"));
        if (meta != null) {
            int last = intOr(meta, "last_page", intOr(meta, "total_pages", page));
            return page < last;
        }
        return pageSize >= 20;
    }

    private static JsonObject asObject(JsonElement el) {
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    static String publicIpOf(Player player) {
        InetSocketAddress socket = player.getAddress();
        if (socket == null) return null;
        InetAddress addr = socket.getAddress();
        if (addr == null) return null;
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isSiteLocalAddress()) {
            return null;
        }
        return addr.getHostAddress();
    }

    private void logErrorThrottled(String message, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogAt < 5 * 60_000L) return;
        lastErrorLogAt = now;
        if (t != null) {
            plugin.getLogger().log(Level.WARNING, "[Vote] Serveur-Prive API : " + message, t);
        } else {
            plugin.getLogger().warning("[Vote] Serveur-Prive API : " + message);
        }
    }

    enum VoteEndpoint {
        LIST,
        BY_IP
    }

    record ApiVote(String username, long votedAtSeconds, String address, String externalId) {}

    record PlayerSnapshot(String name, String ip) {}
}
