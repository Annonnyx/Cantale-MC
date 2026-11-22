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
import org.bukkit.scheduler.BukkitTask;

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
 * Protocole JSON UTF-8, versionné ({@code "v":1} dans chaque message). Les champs et types
 * ajoutés sont optionnels : un client v1 ancien ignore les types inconnus, un serveur v1
 * ancien reste lisible (claims sans {@code fid}).
 * <ul>
 *   <li>C→S : {@code HELLO 1} (texte brut) ou {@code {"v":1,"type":"hello","protocol":1}}</li>
 *   <li>C→S : {@code {"v":1,"type":"inspect","world","x","z","seq"}} — intention d'affichage,
 *       jamais une mutation. Toute autre intention est ignorée.</li>
 *   <li>S→C : {@code welcome} / {@code claims_reset} / {@code claims} (paginé, chunk {@code fid} optionnel) /
 *       {@code claim_add} / {@code claim_remove} / {@code markers} /
 *       {@code faction_index} / {@code hud} / {@code claim_info}</li>
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
     * Chunks par page d'un message {@code claims}. Chaque chunk porte x, z, rel et fid
     * (≈ 55 octets). 400 × 55 ≈ 22 Ko, sous la limite de 32 Ko des messages plugin.
     */
    private static final int CHUNKS_PER_PAGE = 400;
    /** Fiches faction par page de {@code faction_index} (nom + chef + relation). */
    private static final int FACTIONS_PER_PAGE = 40;
    /** Intentions client : HELLO et inspect tiennent largement sous ce plafond. */
    private static final int MAX_CLIENT_INTENT_BYTES = 1024;
    /** Coordonnée de chunk hors carte vanilla : intention rejetée. */
    private static final int MAX_CHUNK_COORD = 1_875_000;
    /** Plafond d'affichage des cœurs, aligné sur le cap de {@code LifeManager#addLives}. */
    private static final int DISPLAY_MAX_LIVES = 10;
    /** Les vies sont en base : on ne les relit pas à chaque tick HUD. */
    private static final long LIVES_CACHE_MS = 5_000L;
    private static final int INSPECT_PER_SECOND = 4;

    private final CantalePlugin plugin;
    private final boolean enabled;

    /** UUIDs des joueurs connectés avec le mod client Cantale (handshake reçu). */
    private final Set<UUID> moddedPlayers = ConcurrentHashMap.newKeySet();
    /** Tâche périodique : vies, combat tag, et rafraîchissement de l'index factions. */
    private BukkitTask hudTask;
    private int hudPulse;
    /** Vies lues en base, mises en cache pour ne pas interroger SQL à chaque envoi. */
    private final Map<UUID, CachedLives> livesCache = new ConcurrentHashMap<>();
    /** Noms de chefs déjà résolus (évite de relire le usercache à chaque index). */
    private final Map<UUID, String> leaderNames = new ConcurrentHashMap<>();
    /** Fenêtre glissante anti-flood des intentions {@code inspect}. */
    private final Map<UUID, long[]> inspectWindows = new ConcurrentHashMap<>();

    private static final class CachedLives {
        final int lives;
        final long at;

        CachedLives(int lives, long at) {
            this.lives = lives;
            this.at = at;
        }
    }

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
        this.hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastHud, 20L, 10L);
        plugin.getLogger().info("[MapBridge] Canal cantale:map enregistré — mod client pris en charge.");
    }

    /** Désenregistre proprement le canal et oublie les clients (arrêt / reload du plugin). */
    public void shutdown() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        hudPulse = 0;
        moddedPlayers.clear();
        livesCache.clear();
        leaderNames.clear();
        inspectWindows.clear();
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
        // Les intentions client sont courtes. Un payload volumineux ne peut pas être un HELLO
        // ni un inspect : on le refuse avant même de le décoder.
        if (message.length == 0 || message.length > MAX_CLIENT_INTENT_BYTES) return;

        String payload = new String(message, StandardCharsets.UTF_8).trim();
        if (payload.regionMatches(true, 0, "HELLO", 0, 5)) {
            int clientProtocol = parseHelloProtocol(payload);
            if (clientProtocol < 1) return;
            acceptHello(player, clientProtocol);
            return;
        }

        JsonObject obj;
        try {
            obj = JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception ignored) {
            return;
        }
        String type = optString(obj, "type");
        if (type == null || type.length() > 32) return;
        if ("hello".equalsIgnoreCase(type)) {
            int clientProtocol = optInt(obj, "protocol", 1);
            if (clientProtocol < 1) return;
            acceptHello(player, clientProtocol);
            return;
        }
        if (!moddedPlayers.contains(player.getUniqueId())) {
            // Pas de handshake : aucune intention n'est honorée.
            return;
        }
        if ("inspect".equals(type)) {
            handleInspect(player, obj);
            return;
        }
        // Claim, économie, monde, ou rejeu d'un message serveur : le client n'est pas source de vérité.
        plugin.getLogger().fine("[MapBridge] Intention refusée (" + type + ") de " + player.getName());
    }

    /** Handshake accepté : état initial (claims, fiches, marqueurs) puis HUD. */
    private void acceptHello(Player player, int clientProtocol) {
        boolean firstContact = moddedPlayers.add(player.getUniqueId());
        int negotiated = Math.min(clientProtocol, PROTOCOL_VERSION);
        sendWelcome(player, negotiated);
        sendClaimsReset(player);
        sendClaims(player);
        sendFactionIndex(player);
        sendMarkers(player);
        sendHud(player);
        sendCosmetics(player);
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
        UUID uuid = event.getPlayer().getUniqueId();
        moddedPlayers.remove(uuid);
        livesCache.remove(uuid);
        inspectWindows.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!enabled || !moddedPlayers.contains(player.getUniqueId())) return;
        // Renvoi complet paginé (v1 : pas de tracking spatial fin)
        sendClaimsReset(player);
        sendClaims(player);
        sendFactionIndex(player);
        sendHud(player);
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
                chunk.addProperty("fid", faction.getId());
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
        // Warps publics de faction (/fw) : marqueur warp avec indicateur "faction"
        if (plugin.getFactionWarpManager() != null) {
            for (fr.cantale.plugin.factions.FactionWarp fw : plugin.getFactionWarpManager().getAll()) {
                JsonObject w = marker(fw.getName(), fw.getWorld(), fw.getX(), fw.getY(), fw.getZ());
                w.addProperty("faction", true);
                warps.add(w);
            }
        }
        if (plugin.getFWarpManager() != null) {
            for (fr.cantale.plugin.teleport.FWarp fwarp : plugin.getFWarpManager().getFWarps()) {
                if (fwarp.getLocation() == null || fwarp.getLocation().getWorld() == null) continue;
                JsonObject w = marker(fwarp.getName(), fwarp.getLocation().getWorld().getName(),
                        fwarp.getLocation().getX(), fwarp.getLocation().getY(), fwarp.getLocation().getZ());
                w.addProperty("faction", true);
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
            msg.addProperty("fid", faction.getId());
            msg.addProperty("faction", sanitize(faction.getName(), 16));
            msg.addProperty("leader", leaderName(faction));
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
            sendFactionIndex(player);
        }
    }

    /** Hook mode secret (activation / expiration / désactivation) : la visibilité change globalement. */
    public void onSecretToggled() {
        if (!enabled) return;
        for (Player player : onlineModdedPlayers()) {
            sendClaimsReset(player);
            sendClaims(player);
            sendFactionIndex(player);
        }
    }

    /** Hook arrivée / départ de membre : ses relations OWN et son /f spawn changent. */
    public void onMemberFactionChanged(UUID playerUuid) {
        if (!enabled || playerUuid == null || !moddedPlayers.contains(playerUuid)) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;
        sendClaimsReset(player);
        sendClaims(player);
        sendFactionIndex(player);
        sendMarkers(player);
        sendHud(player);
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

    // ─── HUD, index factions, intention inspect ──────────────────

    /** Toutes les 10 ticks : combat (mémoire) et vies (cache SQL). Index factions toutes les 30 s. */
    private void broadcastHud() {
        if (!enabled) return;
        hudPulse++;
        boolean refreshIndex = hudPulse % 60 == 0;
        boolean refreshCosmetics = hudPulse % 4 == 0;
        for (Player player : onlineModdedPlayers()) {
            sendHud(player);
            if (refreshCosmetics) sendCosmetics(player);
            if (refreshIndex) sendFactionIndex(player);
        }
    }

    public void broadcastCosmetics() {
        if (!enabled) return;
        for (Player player : onlineModdedPlayers()) {
            sendCosmetics(player);
        }
    }

    private void sendCosmetics(Player player) {
        if (!enabled || player == null || !player.isOnline() || !moddedPlayers.contains(player.getUniqueId())) return;
        JsonObject msg = base("cosmetics");
        fr.cantale.plugin.cosmetics.SeasonPassService pass = plugin.getSeasonPass();
        msg.add("players", pass == null ? new JsonArray() : pass.snapshot(player));
        send(player, msg);
    }

    private void sendHud(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        boolean unlimited = player.hasPermission("cantale.admin");
        int lives = unlimited ? 0 : livesFor(player);
        int combatMaxSec = 20;
        int combatSec = 0;
        String opponent = null;
        fr.cantale.plugin.combat.CombatTagManager combat = plugin.getCombatTagManager();
        if (combat != null) {
            combatMaxSec = Math.max(1, combat.getCombatDurationSeconds());
            combatSec = Math.max(0, combat.getRemainingSeconds(player.getUniqueId()));
            if (combatSec > 0) opponent = combat.getOpponentName(player.getUniqueId());
        }
        JsonObject msg = base("hud");
        msg.addProperty("lives", lives);
        msg.addProperty("maxLives", DISPLAY_MAX_LIVES);
        msg.addProperty("unlimited", unlimited);
        msg.addProperty("combatMs", combatSec * 1000);
        msg.addProperty("combatMaxMs", combatMaxSec * 1000);
        if (opponent != null && !opponent.isEmpty()) {
            msg.addProperty("opponent", sanitize(opponent, 16));
        }
        send(player, msg);
    }

    private int livesFor(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        CachedLives cached = livesCache.get(uuid);
        if (cached != null && now - cached.at < LIVES_CACHE_MS) return cached.lives;
        int lives = 0;
        try {
            if (plugin.getLifeManager() != null) {
                lives = Math.max(0, plugin.getLifeManager().getLives(uuid));
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[MapBridge] Vies indisponibles pour " + player.getName() + " : " + e.getMessage());
            if (cached != null) return cached.lives;
        }
        livesCache.put(uuid, new CachedLives(lives, now));
        return lives;
    }

    /** Index nom / chef / relation diplomatique, filtré comme les claims (secret inclus). */
    private void sendFactionIndex(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;
        if (plugin.getFactionManager() == null) return;
        Faction viewer = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        List<JsonObject> entries = new ArrayList<>();
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            if (faction == null || faction.getClaims().isEmpty()) continue;
            boolean own = viewer != null && faction.getId() == viewer.getId();
            if (faction.isSecret() && !own) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", faction.getId());
            entry.addProperty("name", sanitize(faction.getName(), 16));
            entry.addProperty("leader", leaderName(faction));
            entry.addProperty("rel", diplomacyRel(faction, viewer));
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            JsonObject msg = base("faction_index");
            msg.addProperty("page", 0);
            msg.addProperty("pages", 1);
            msg.add("entries", new JsonArray());
            send(player, msg);
            return;
        }
        int total = entries.size();
        int pages = Math.max(1, (total + FACTIONS_PER_PAGE - 1) / FACTIONS_PER_PAGE);
        for (int page = 0; page < pages; page++) {
            JsonObject msg = base("faction_index");
            msg.addProperty("page", page);
            msg.addProperty("pages", pages);
            JsonArray slice = new JsonArray();
            int from = page * FACTIONS_PER_PAGE;
            int to = Math.min(total, from + FACTIONS_PER_PAGE);
            for (int i = from; i < to; i++) slice.add(entries.get(i));
            msg.add("entries", slice);
            send(player, msg);
        }
    }

    /**
     * Intention d'affichage : relit le claim côté serveur (visibilité secret comprise)
     * et répond par {@code claim_info}. Ne modifie ni le monde, ni l'économie, ni le claim.
     */
    private void handleInspect(Player player, JsonObject obj) {
        if (plugin.getFactionManager() == null) return;
        if (!allowInspect(player.getUniqueId())) return;
        int seq = optInt(obj, "seq", -1);
        if (seq <= 0) return;

        String world = optString(obj, "world");
        if (!isSafeWorldName(world)) {
            sendClaimInfo(player, seq, false, "", 0, 0, "", "", "");
            return;
        }
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            sendClaimInfo(player, seq, false, "", 0, 0, "", "", "");
            return;
        }
        int x = optInt(obj, "x", Integer.MIN_VALUE);
        int z = optInt(obj, "z", Integer.MIN_VALUE);
        String canonical = bukkitWorld.getName();
        if (x < -MAX_CHUNK_COORD || x > MAX_CHUNK_COORD || z < -MAX_CHUNK_COORD || z > MAX_CHUNK_COORD) {
            sendClaimInfo(player, seq, false, canonical, 0, 0, "", "", "");
            return;
        }

        Faction viewer = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        Claim claim = plugin.getFactionManager().getClaimAt(canonical, x, z);
        if (claim == null) {
            sendClaimInfo(player, seq, false, canonical, x, z, "", "", "");
            return;
        }
        Faction faction = plugin.getFactionManager().getFaction(claim.getFactionId());
        if (faction == null) {
            sendClaimInfo(player, seq, false, canonical, x, z, "", "", "");
            return;
        }
        boolean own = viewer != null && viewer.getId() == faction.getId();
        if (faction.isSecret() && !own) {
            // Même silence que la carte : un claim secret n'existe pas pour les autres.
            sendClaimInfo(player, seq, false, canonical, x, z, "", "", "");
            return;
        }
        sendClaimInfo(player, seq, true, canonical, x, z,
                relationFor(faction, viewer, claim),
                sanitize(faction.getName(), 16),
                leaderName(faction));
    }

    private void sendClaimInfo(Player player, int seq, boolean found, String world, int x, int z,
                               String rel, String faction, String leader) {
        JsonObject msg = base("claim_info");
        msg.addProperty("seq", seq);
        msg.addProperty("found", found);
        if (found) {
            msg.addProperty("world", world);
            msg.addProperty("x", x);
            msg.addProperty("z", z);
            msg.addProperty("rel", rel);
            msg.addProperty("faction", faction == null ? "" : faction);
            msg.addProperty("leader", leader == null ? "" : leader);
        }
        send(player, msg);
    }

    private boolean allowInspect(UUID uuid) {
        long now = System.currentTimeMillis();
        long[] window = inspectWindows.computeIfAbsent(uuid, k -> new long[2]);
        synchronized (window) {
            if (now - window[0] >= 1000L) {
                window[0] = now;
                window[1] = 1L;
                return true;
            }
            if (window[1] >= INSPECT_PER_SECOND) return false;
            window[1]++;
            return true;
        }
    }

    /** Relation diplomatique de la faction, sans le surcharge PASDIC propre au chunk. */
    private String diplomacyRel(Faction claimFaction, Faction viewerFaction) {
        if (viewerFaction == null || claimFaction == null) return "OTHER";
        if (claimFaction.getId() == viewerFaction.getId()) return "OWN";
        DiplomacyManager diplomacy = plugin.getDiplomatieManager();
        if (diplomacy != null) {
            if (diplomacy.isAllied(viewerFaction.getId(), claimFaction.getId())) return "ALLY";
            if (diplomacy.isAtWar(viewerFaction.getId(), claimFaction.getId())) return "WAR";
        }
        return "OTHER";
    }

    private String leaderName(Faction faction) {
        if (faction == null) return "";
        UUID id = faction.getLeaderUuid();
        if (id == null) return "";
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            String name = sanitize(online.getName(), 16);
            if (!name.isEmpty()) {
                leaderNames.put(id, name);
                return name;
            }
        }
        String cached = leaderNames.get(id);
        if (cached != null) return cached;
        String resolved = "";
        try {
            resolved = sanitize(Bukkit.getOfflinePlayer(id).getName(), 16);
        } catch (Exception ignored) {
            resolved = "";
        }
        leaderNames.put(id, resolved);
        return resolved;
    }

    private static boolean isSafeWorldName(String world) {
        if (world == null || world.isEmpty() || world.length() > 64) return false;
        for (int i = 0; i < world.length(); i++) {
            char c = world.charAt(i);
            if (c <= 32 || c == '"' || c == '\\' || c == 127) return false;
        }
        return true;
    }

    private static String sanitize(String raw, int max) {
        if (raw == null || raw.isEmpty() || max <= 0) return "";
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), max));
        int n = Math.min(raw.length(), max);
        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127) sb.append(c);
        }
        return sb.toString();
    }

    private static String optString(JsonObject obj, String key) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsInt();
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

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
