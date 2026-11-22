package fr.cantale.plugin.vote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.config.SecretResolver;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Polling de l'API officielle ServeurListe.com (site 3).
 *
 * <p>FAQ : {@code GET /api/vote?ip_address={ip}&api_token={token_api}} — état de vote,
 * pseudo, prochain vote, date du dernier vote. Pas d'endpoint liste
 * ({@code GET /api/votes} → 404). Auth : {@code api_token} en query (le {@code token_api}
 * du dashboard), pas en header Bearer.
 *
 * <p>Votifier reste optionnel (Top-Serveurs en a besoin). Si le panneau ServeurListe
 * affiche « connexion impossible », le TCP Votifier n'atteint pas NuVotifier ;
 * ce poller récompense quand même dès qu'un joueur en ligne a la même IP publique.
 */
public final class ServeurListeVotePoller extends BukkitRunnable {

    static final String SITE_SERVICE = "ServeurListe.com";
    static final String TOKEN_CONFIG_KEY = "serveurliste.com";
    /** Intervalle de vote annoncé par ServeurListe (FAQ : 1 h 30). */
    static final int SITE_INTERVAL_MINUTES = 90;

    private static final String[] USERNAME_KEYS = {
            "username", "user", "name", "pseudo", "player", "mc_username", "nickname"
    };
    private static final String[] ID_KEYS = {"id", "uuid", "vote_id", "voteId"};
    private static final String[] TIME_KEYS = {
            "lastvote", "last_vote", "last_vote_at", "voted_at", "votedAt",
            "created_at", "date", "time"
    };
    private static final String[] NEXT_KEYS = {
            "nextvote", "next_vote", "next_vote_at", "nextVote", "can_vote_at"
    };

    private final CantalePlugin plugin;
    private final VoteListener voteListener;
    private final VoteDAO voteDAO;
    private final OkHttpClient httpClient;

    private boolean started;
    private long lastErrorLogAt;
    private int consecutiveErrors;

    public ServeurListeVotePoller(CantalePlugin plugin, VoteListener voteListener) {
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
        if (!plugin.getConfig().getBoolean("vote.serveur-liste.enabled", true)) {
            plugin.getLogger().info("[Vote] ServeurListe API : polling désactivé (vote.serveur-liste.enabled=false).");
            return;
        }
        String token = resolveToken();
        if (token.isEmpty()) {
            plugin.getLogger().warning("[Vote] ServeurListe API : token manquant. "
                    + "Définir CANTALE_VOTE_SERVEURLISTE_TOKEN ou vote.tokens.serveurliste.com. "
                    + "Aucun secret n'est écrit dans les logs.");
            return;
        }
        int seconds = Math.max(30, plugin.getConfig().getInt("vote.serveur-liste.poll-interval-seconds", 60));
        runTaskTimerAsynchronously(plugin, 20L * 20L, seconds * 20L);
        started = true;
        plugin.getLogger().info("[Vote] ServeurListe API : polling toutes les " + seconds
                + "s (GET /api/vote?ip_address=&api_token=). Indépendant de Votifier — docs/Votes.md.");
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
            pollOnlinePlayerIps();
            consecutiveErrors = 0;
        } catch (Exception e) {
            consecutiveErrors++;
            logErrorThrottled("erreur polling ServeurListe : " + e.getMessage(), e);
        }
    }

    private void pollOnlinePlayerIps() {
        String token = resolveToken();
        if (token.isEmpty()) return;

        List<PlayerSnapshot> online = snapshotOnlinePlayers();
        for (PlayerSnapshot player : online) {
            try {
                JsonObject root = getJson(voteByIpUrl(token, player.ip()));
                if (root == null) continue;
                ApiVote vote = parseVotePayload(root, player.ip(), player.name());
                if (vote == null) continue;
                considerVote(vote);
            } catch (Exception e) {
                plugin.getLogger().fine("[Vote] ServeurListe check IP " + player.name() + " : " + e.getMessage());
            }
        }
    }

    private List<PlayerSnapshot> snapshotOnlinePlayers() {
        List<PlayerSnapshot> snapshot = new ArrayList<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String ip = ServeurPriveVotePoller.publicIpOf(player);
                    if (ip != null) snapshot.add(new PlayerSnapshot(player.getName(), ip));
                }
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().fine("[Vote] ServeurListe : timeout snapshot joueurs en ligne.");
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
        int maxAgeMin = Math.max(SITE_INTERVAL_MINUTES,
                plugin.getConfig().getInt("vote.serveur-liste.max-age-minutes", 120));
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
                // Pas de pré-check cooldown ici : l'intervalle du site (1 h 30) est plus court
                // qu'un cooldown en heures entières → tout vote à 1h30–2h était avalé puis
                // marqué trop vieux (perdu). La dédup atomique par external_id + le cooldown
                // site de handleVote (1 h, config vote.sites) suffisent.
                if (!voteDAO.tryMarkExternalVoteProcessed(SITE_SERVICE, externalId, username)) {
                    return;
                }
                voteListener.handleVote(username, SITE_SERVICE, address);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Vote] ServeurListe : échec grant pour " + username, e);
            }
        });
    }

    String resolveToken() {
        return SecretResolver.system(message -> plugin.getLogger().warning(message)).firstUsable(
                "CANTALE_VOTE_SERVEURLISTE_TOKEN",
                "vote.tokens.serveurliste.com",
                false,
                plugin.getConfig().getString("vote.serveur-liste.api-token", ""),
                plugin.getConfig().getString("vote.tokens." + TOKEN_CONFIG_KEY, ""));
    }

    private String apiVoteUrl() {
        String base = plugin.getConfig().getString("vote.serveur-liste.api-url",
                "https://www.serveurliste.com/api/vote");
        if (base == null || base.isBlank()) base = "https://www.serveurliste.com/api/vote";
        return base.trim();
    }

    private String voteByIpUrl(String token, String ip) {
        HttpUrl parsed = HttpUrl.parse(apiVoteUrl());
        if (parsed == null) {
            return apiVoteUrl() + "?ip_address=" + ip + "&api_token=" + token;
        }
        return parsed.newBuilder()
                .setQueryParameter("ip_address", ip)
                .setQueryParameter("api_token", token)
                .build()
                .toString();
    }

    private JsonObject getJson(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "CantalePlugin/1.0 (ServeurListe vote poller)")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 401 || response.code() == 403) {
                logErrorThrottled("HTTP " + response.code() + " (token_api invalide ?) sur " + redact(url), null);
                return null;
            }
            if (!response.isSuccessful()) {
                logErrorThrottled("HTTP " + response.code() + " sur " + redact(url), null);
                return null;
            }
            if (body.isBlank()) return null;
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                logErrorThrottled("réponse JSON inattendue (non-objet) sur " + redact(url), null);
                return null;
            }
            return parsed.getAsJsonObject();
        }
    }

    /**
     * Interprète {@code {"data":{"voted":true|false,"nextvote":...,"username":...}}} (FAQ ServeurListe).
     * @return un vote à considérer, ou {@code null} si l'IP n'a pas voté récemment
     */
    static ApiVote parseVotePayload(JsonObject root, String fallbackIp, String fallbackName) {
        if (root == null) return null;
        JsonObject data = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            data = root.getAsJsonObject("data");
        }

        long nextVote = firstTimestamp(data, NEXT_KEYS);
        long lastVote = firstTimestamp(data, TIME_KEYS);
        boolean voted = parseVotedFlag(data, nextVote);

        if (!voted) return null;

        String username = firstString(data, USERNAME_KEYS);
        if (username == null || username.isBlank()) username = fallbackName;
        if (username != null) username = username.trim();
        if (username == null || username.isBlank()) return null;

        String address = firstString(data, new String[]{"ip", "address", "user_ip", "ipv4"});
        if (address == null || address.isBlank()) address = fallbackIp;

        long votedAt = lastVote;
        if (votedAt <= 0 && nextVote > 1_000_000_000L) {
            votedAt = nextVote - (SITE_INTERVAL_MINUTES * 60L);
        } else if (votedAt <= 0 && nextVote > 0 && nextVote < 172_800L) {
            votedAt = (System.currentTimeMillis() / 1000L) - ((SITE_INTERVAL_MINUTES * 60L) - nextVote);
        }

        String id = firstString(data, ID_KEYS);
        String externalId = (id != null && !id.isBlank())
                ? id.trim()
                : fingerprint(username, votedAt > 0 ? votedAt : nextVote, address);

        return new ApiVote(username, votedAt, address, externalId);
    }

    static boolean parseVotedFlag(JsonObject data, long nextVote) {
        if (data.has("voted") && !data.get("voted").isJsonNull()) {
            JsonElement el = data.get("voted");
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
                if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt() != 0;
                String s = el.getAsString();
                if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
                if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
            }
        }
        if (data.has("has_voted") && data.get("has_voted").isJsonPrimitive()) {
            try {
                return data.get("has_voted").getAsBoolean();
            } catch (Exception ignored) {
                return "1".equals(data.get("has_voted").getAsString());
            }
        }
        if (data.has("can_vote") && data.get("can_vote").isJsonPrimitive()) {
            try {
                return !data.get("can_vote").getAsBoolean();
            } catch (Exception ignored) {
                return "false".equalsIgnoreCase(data.get("can_vote").getAsString())
                        || "0".equals(data.get("can_vote").getAsString());
            }
        }
        if (data.has("nextvote") && !data.get("nextvote").isJsonNull()) {
            return nextVote > 0 || data.get("nextvote").isJsonPrimitive();
        }
        return false;
    }

    private static String fingerprint(String username, long votedAt, String address) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        long window = SITE_INTERVAL_MINUTES * 60L;
        long bucket = votedAt > 0 ? votedAt : (System.currentTimeMillis() / 1000L / window);
        return name + ":" + bucket + ":" + (address == null ? "" : address);
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
            long parsed = ServeurPriveVotePoller.parseTimestamp(obj.get(key));
            if (parsed > 0) return parsed;
        }
        return 0L;
    }

    private String redact(String url) {
        String token = resolveToken();
        if (token.isEmpty()) return url;
        return url.replace(token, "***");
    }

    private void logErrorThrottled(String message, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogAt < 5 * 60_000L && consecutiveErrors > 0) return;
        lastErrorLogAt = now;
        if (t != null) {
            plugin.getLogger().log(Level.WARNING, "[Vote] ServeurListe API : " + message, t);
        } else {
            plugin.getLogger().warning("[Vote] ServeurListe API : " + message);
        }
    }

    record ApiVote(String username, long votedAtSeconds, String address, String externalId) {}

    record PlayerSnapshot(String name, String ip) {}
}
