package fr.cantale.plugin.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.teleport.Home;
import fr.cantale.plugin.teleport.Warp;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge serveur ↔ mod client Cantale (canal plugin {@value #CHANNEL}).
 *
 * Principe : le serveur n'envoie RIEN aux clients vanilla. Un client équipé du mod
 * se signale par un handshake {@code HELLO <version>} ; il reçoit alors l'état initial
 * (claims paginés + marqueurs) puis des deltas (claim_add / claim_remove) et des
 * rafraîchissements ciblés (diplomatie, mode secret, warps, homes, /f spawn).
 *
 * Protocole JSON UTF-8, versionné ({@code "v":1} dans chaque message) :
 * <ul>
 *   <li>C→S : {@code HELLO 1} (texte brut) ou {@code {"v":1,"type":"hello","protocol":1}}</li>
 *   <li>S→C : {@code welcome} / {@code claims_reset} / {@code claims} (paginé par monde) /
 *       {@code claim_add} / {@code claim_remove} / {@code markers}</li>
 * </ul>
 *
 * Filtrage identique à la map in-game ({@code ui/map/MapImageBuilder}) : les factions en
 * mode secret sont exclues pour tout le monde sauf leurs membres ; la relation est
 * calculée pour CE joueur (OWN / ALLY / WAR / OTHER, PASDIC prioritaire).
 */
public class ClientMapBridge implements Listener, PluginMessageListener {

    /** Canal plugin message partagé avec le mod Fabric. */
    public static final String CHANNEL = "cantale:map";
    /** Version du protocole supportée par ce serveur. */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * Chunks par page d'un message {@code claims}. ≈ 400 × 40 octets ≈ 16 Ko :
     * large sous la limite de 32 Ko des messages plugin (et du codec client).
     */
    private static final int CHUNKS_PER_PAGE = 400;

    private final CantalePlugin plugin;
    private final boolean enabled;

    /** UUIDs des joueurs connectés avec le mod client Cantale (handshake reçu). */
    private final Set<UUID> moddedPlayers = ConcurrentHashMap.newKeySet();

    public ClientMapBridge(CantalePlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("map.client-mod.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Enregistre le canal (sortant + entrant) et les événements. Appelé à l'activation du plugin. */
    public void register() {
        if (!enabled) {
            plugin.getLogger().info("[MapBridge] Désactivé (map.client-mod.enabled: false) — canal cantale:map non enregistré.");
            return;
        }
        getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[MapBridge] Canal cantale:map enregistré — mod client pris en charge.");
    }

    /** Désenregistre proprement le canal et oublie les clients (arrêt / reload du plugin). */
    public void shutdown() {
        moddedPlayers.clear();
        if (!enabled) return;
        try {
            getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
            getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
            // Canal déjà désenregistré (reload partiel) : sans gravité
        }
    }

    private org.bukkit.Server getServer() {
        return plugin.getServer();
    }

    // ─── Handshake ───────────────────────────────────────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled || !CHANNEL.equals(channel) || player == null || message == null) return;

        String payload = new String(message, StandardCharsets.UTF_8).trim();
        int clientProtocol = parseHelloProtocol(payload);
        if (clientProtocol < 0) {
            // Message inconnu sur notre canal : ignorer sans bruit (jamais d'erreur côté vanilla)
            return;
        }

        boolean firstContact = moddedPlayers.add(player.getUniqueId());
        int negotiated = Math.min(clientProtocol, PROTOCOL_VERSION);
        sendWelcome(player, negotiated);
        sendClaimsReset(player);
        sendClaims(player);
        sendMarkers(player);
        if (firstContact) {
            plugin.getLogger().info("[MapBridge] " + player.getName()
                    + " utilise le mod client Cantale (protocole v" + clientProtocol + ").");
        }
    }

    /**
     * Extrait la version du handshake : texte {@code HELLO <v>} (ou {@code HELLO} nu = v1),
     * ou JSON {@code {"type":"hello","protocol":N}}.
     *
     * @return version du protocole client, ou {@code -1} si le message n'est pas un handshake
     */
    private int parseHelloProtocol(String payload) {
        if (payload.regionMatches(true, 0, "HELLO", 0, 5)) {
            String[] parts = payload.split("\\s+");
            if (parts.length >= 2) {
                try {
                    return Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return 1;
        }
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            if (obj.has("type") && "hello".equalsIgnoreCase(obj.get("type").getAsString())) {
                return obj.has("protocol") ? obj.get("protocol").getAsInt() : 1;
            }
        } catch (Exception ignored) {
            // Pas du JSON ou structure inattendue : pas un handshake valide
        }
        return -1;
    }

    // ─── Événements Bukkit ───────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!enabled || !moddedPlayers.contains(player.getUniqueId())) return;
        // Renvoi complet paginé (v1 : pas de tracking spatial fin)
        sendClaimsReset(player);
        sendClaims(player);
    }

    // ─── État initial ────────────────────────────────────────────

    private void sendWelcome(Player player, int negotiatedProtocol) {
        JsonObject msg = base("welcome");
        msg.addProperty("protocol", negotiatedProtocol);
        msg.addProperty("server", "Cantale");
        // Correspondance monde Bukkit ↔ dimension client (ex. « world » ↔ « overworld »)
        JsonArray worlds = new JsonArray();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            JsonObject w = new JsonObject();
            w.addProperty("name", world.getName());
            w.addProperty("dim", switch (world.getEnvironment()) {
                case NETHER -> "the_nether";
                case THE_END -> "the_end";
                case NORMAL -> "overworld";
                default -> world.getName();
            });
            worlds.add(w);
        }
        msg.add("worlds", worlds);
        send(player, msg);
    }

    /** Vide côté client avant un renvoi complet (gère aussi les mondes devenus sans claims). */
    private void sendClaimsReset(Player player) {
        send(player, base("claims_reset"));
    }

    /**
     * Envoie tous les claims visibles pour ce joueur, groupés par monde et paginés.
     * Filtrage et relations : même logique que la map in-game.
     */
    public void sendClaims(Player player) {
        if (!enabled) return;
        Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        // Regroupement par monde, relation déjà résolue par chunk
        Map<String, JsonArray> chunksByWorld = new LinkedHashMap<>();
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            boolean own = viewerFaction != null && faction.getId() == viewerFaction.getId();
            if (faction.isSecret() && !own) continue;
            for (Claim claim : faction.getClaims()) {
                JsonObject chunk = new JsonObject();
                chunk.addProperty("x", claim.getChunkX());
                chunk.addProperty("z", claim.getChunkZ());
                chunk.addProperty("rel", relationFor(faction, viewerFaction, claim));
                chunksByWorld.computeIfAbsent(claim.getWorld(), k -> new JsonArray()).add(chunk);
            }
        }

        for (Map.Entry<String, JsonArray> entry : chunksByWorld.entrySet()) {
            JsonArray all = entry.getValue();
            int total = all.size();
            int pages = Math.max(1, (total + CHUNKS_PER_PAGE - 1) / CHUNKS_PER_PAGE);
            for (int page = 0; page < pages; page++) {
                JsonObject msg = base("claims");
                msg.addProperty("world", entry.getKey());
                msg.addProperty("page", page);
                msg.addProperty("pages", pages);
                JsonArray slice = new JsonArray();
                int from = page * CHUNKS_PER_PAGE;
                int to = Math.min(total, from + CHUNKS_PER_PAGE);
                for (int i = from; i < to; i++) {
                    slice.add(all.get(i));
                }
                msg.add("chunks", slice);
                send(player, msg);
            }
        }
    }

    /** Envoie les marqueurs personnalisés du joueur : warps publics actifs, ses homes, son /f spawn. */
    public void sendMarkers(Player player) {
        if (!enabled) return;
        JsonObject msg = base("markers");

        JsonArray warps = new JsonArray();
        if (plugin.getWarpManager() != null) {
            for (Warp warp : plugin.getWarpManager().getWarps()) {
                if (!warp.isActive() || warp.getLocation() == null || warp.getLocation().getWorld() == null) continue;
                JsonObject w = marker(warp.getName(), warp.getLocation().getWorld().getName(),
                        warp.getLocation().getX(), warp.getLocation().getY(), warp.getLocation().getZ());
                w.addProperty("event", warp.isEvent());
                warps.add(w);
            }
        }
        msg.add("warps", warps);

        JsonArray homes = new JsonArray();
        if (plugin.getHomeManager() != null) {
            for (Home home : plugin.getHomeManager().ensureHomesLoaded(player.getUniqueId()).values()) {
                if (home.getLocation() == null || home.getLocation().getWorld() == null) continue;
                homes.add(marker(home.getName(), home.getLocation().getWorld().getName(),
                        home.getLocation().getX(), home.getLocation().getY(), home.getLocation().getZ()));
            }
        }
        msg.add("homes", homes);

        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction != null && faction.hasSpawn() && faction.getSpawnWorld() != null) {
            msg.add("fspawn", marker(faction.getName(), faction.getSpawnWorld(),
                    faction.getSpawnX(), faction.getSpawnY(), faction.getSpawnZ()));
        } else {
            msg.add("fspawn", null);
        }
        send(player, msg);
    }

    private JsonObject marker(String name, String world, double x, double y, double z) {
        JsonObject m = new JsonObject();
        m.addProperty("name", name);
        m.addProperty("world", world);
        m.addProperty("x", x);
        m.addProperty("y", y);
        m.addProperty("z", z);
        return m;
    }

    // ─── Deltas (hooks appelés par les managers) ─────────────────

    /** Hook {@code FactionManager.addClaim} : diffuse le nouveau claim aux clients du mod. */
    public void onClaimAdded(Faction faction, Claim claim) {
        if (!enabled || faction == null || claim == null) return;
        for (Player player : onlineModdedPlayers()) {
            Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            boolean own = viewerFaction != null && faction.getId() == viewerFaction.getId();
            if (faction.isSecret() && !own) continue;
            JsonObject msg = base("claim_add");
            msg.addProperty("world", claim.getWorld());
            msg.addProperty("x", claim.getChunkX());
            msg.addProperty("z", claim.getChunkZ());
            msg.addProperty("rel", relationFor(faction, viewerFaction, claim));
            send(player, msg);
        }
    }

    /** Hook {@code FactionManager.removeClaim} : retire le chunk chez tous ceux qui le voyaient. */
    public void onClaimRemoved(String world, int chunkX, int chunkZ, int factionId) {
        if (!enabled) return;
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        for (Player player : onlineModdedPlayers()) {
            // Une faction secrète n'a jamais été envoyée aux non-membres : ne rien leur signaler
            if (faction != null && faction.isSecret()) {
                Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
                boolean own = viewerFaction != null && viewerFaction.getId() == factionId;
                if (!own) continue;
            }
            JsonObject msg = base("claim_remove");
            msg.addProperty("world", world);
            msg.addProperty("x", chunkX);
            msg.addProperty("z", chunkZ);
            send(player, msg);
        }
    }

    /** Hook {@code PasdicService} : la relation du chunk change (PASDIC ↔ relation normale). */
    public void onClaimPasdicToggled(Claim claim) {
        if (!enabled || claim == null) return;
        Faction faction = plugin.getFactionManager().getFaction(claim.getFactionId());
        if (faction != null) {
            onClaimAdded(faction, claim);
        }
    }

    /**
     * Hook {@code DiplomacyManager} (guerre / paix / alliance) : les couleurs de relation
     * changent potentiellement pour tout le monde → renvoi complet aux clients du mod.
     * Événement rare, coût acceptable (claims paginés).
     */
    public void onDiplomacyChanged() {
        if (!enabled) return;
        for (Player player : onlineModdedPlayers()) {
            sendClaimsReset(player);
            sendClaims(player);
        }
    }

    /** Hook mode secret (activation / expiration / désactivation) : la visibilité change globalement. */
    public void onSecretToggled() {
        if (!enabled) return;
        for (Player player : onlineModdedPlayers()) {
            sendClaimsReset(player);
            sendClaims(player);
        }
    }

    /** Hook arrivée / départ de membre : ses relations OWN et son /f spawn changent. */
    public void onMemberFactionChanged(UUID playerUuid) {
        if (!enabled || playerUuid == null || !moddedPlayers.contains(playerUuid)) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;
        sendClaimsReset(player);
        sendClaims(player);
        sendMarkers(player);
    }

    /** Renvoie les marqueurs à tous les clients du mod (warp créé / supprimé / (dés)activé). */
    public void refreshMarkersForAll() {
        if (!enabled) return;
        for (Player player : onlineModdedPlayers()) {
            sendMarkers(player);
        }
    }

    /** Renvoie les marqueurs d'un joueur (home créé / supprimé). */
    public void refreshMarkers(UUID playerUuid) {
        if (!enabled || playerUuid == null || !moddedPlayers.contains(playerUuid)) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            sendMarkers(player);
        }
    }

    /** Renvoie les marqueurs aux membres en ligne d'une faction (/f setspawn déplacé). */
    public void refreshMarkersForFaction(Faction faction) {
        if (!enabled || faction == null) return;
        for (UUID memberUuid : faction.getMembers()) {
            refreshMarkers(memberUuid);
        }
    }

    // ─── Utilitaires ─────────────────────────────────────────────

    /**
     * Relation d'un claim avec le joueur — mêmes règles que {@code MapImageBuilder} :
     * PASDIC prioritaire, puis OWN / ALLY / WAR (guerre mutuelle via {@code isAtWar}),
     * sinon OTHER. ({@code ENEMY} est réservé au protocole pour un futur mode de guerre
     * unilatérale.)
     */
    private String relationFor(Faction claimFaction, Faction viewerFaction, Claim claim) {
        if (claim.isPasdic()) return "PASDIC";
        if (viewerFaction == null) return "OTHER";
        if (claimFaction.getId() == viewerFaction.getId()) return "OWN";
        DiplomacyManager diplomacy = plugin.getDiplomatieManager();
        if (diplomacy != null) {
            if (diplomacy.isAllied(viewerFaction.getId(), claimFaction.getId())) return "ALLY";
            if (diplomacy.isAtWar(viewerFaction.getId(), claimFaction.getId())) return "WAR";
        }
        return "OTHER";
    }

    /** Joueurs en ligne ayant fait le handshake mod (instantané sûr sur le set concurrent). */
    private List<Player> onlineModdedPlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : moddedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private JsonObject base(String type) {
        JsonObject msg = new JsonObject();
        msg.addProperty("v", PROTOCOL_VERSION);
        msg.addProperty("type", type);
        return msg;
    }

    /** Envoi JSON UTF-8 ; tout échec est silencieux (un client ne doit jamais casser le serveur). */
    private void send(Player player, JsonObject msg) {
        try {
            player.sendPluginMessage(plugin, CHANNEL, msg.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().fine("[MapBridge] Échec envoi à " + player.getName() + " : " + e.getMessage());
        }
    }
}
