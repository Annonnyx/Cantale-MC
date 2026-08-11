package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Consomme la file web_faction_actions (partagée avec le site Next.js, Site-Cantale/web).
 *
 * Règle absolue : le site n'écrit JAMAIS directement dans faction_members. Il dépose
 * une ligne (type, faction_id, player_uuid, payload, status='pending') ; cette tâche
 * la rejoue toutes les 15 secondes via FactionManager, donc toute la logique métier
 * (cache faction, power, promotion, sync du rôle Discord "a une faction", suppression
 * des claims à la dissolution, ...) se déclenche naturellement.
 *
 * Types supportés :
 *  - join     : ajoute player_uuid à la faction au rang RECRUIT (payload ignoré)
 *  - leave    : retire player_uuid de la faction (refusé pour le leader)
 *  - set_rank : change le rang du membre (payload = RECRUIT|MEMBER|VETERAN|OFFICER,
 *               jamais LEADER : le leadership ne se transfère pas par le web)
 *  - disband  : dissout la faction (player_uuid doit être le leader)
 *
 * La logique faction n'est pas thread-safe : chaque action est exécutée sur le thread
 * principal, puis la ligne est marquée done/failed (result + processed_at) en asynchrone.
 */
public class WebFactionBridge {

    private static final long INTERVAL_TICKS = 20L * 15; // 15 secondes
    private static final long INITIAL_DELAY_TICKS = 20L * 10; // première passe 10s après le boot
    private static final int BATCH_SIZE = 10;
    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 10;
    private static final int RESULT_MAX_LENGTH = 255;

    private final CantalePlugin plugin;
    private BukkitTask task;

    public WebFactionBridge(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollPendingActions, INITIAL_DELAY_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[Web] WebFactionBridge démarré (consommation web_faction_actions toutes les 15s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private record PendingAction(long id, String type, int factionId, String playerUuid, String payload) {
    }

    private void pollPendingActions() {
        for (PendingAction action : fetchPendingActions()) {
            process(action);
        }
    }

    private List<PendingAction> fetchPendingActions() {
        List<PendingAction> actions = new ArrayList<>();
        String sql = "SELECT id, type, faction_id, player_uuid, payload FROM web_faction_actions " +
                "WHERE status = 'pending' ORDER BY id LIMIT " + BATCH_SIZE;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return actions; // getConnection() a déjà loggé la tentative de reconnexion
            }
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    actions.add(new PendingAction(
                            rs.getLong("id"),
                            rs.getString("type"),
                            rs.getInt("faction_id"),
                            rs.getString("player_uuid"),
                            rs.getString("payload")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebFactionBridge : lecture web_faction_actions impossible : " + e.getMessage());
        }
        return actions;
    }

    private void process(PendingAction action) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(execute(action));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            // Scheduler indisponible (arrêt du plugin) : la ligne reste 'pending'
            // et sera reprise au prochain démarrage.
            plugin.getLogger().fine("[Web] WebFactionBridge : action #" + action.id() + " reportée (scheduler indisponible).");
            return;
        }

        String failure;
        try {
            failure = future.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return; // la ligne reste 'pending'
        } catch (ExecutionException e) {
            failure = "erreur interne : " + e.getCause().getMessage();
        } catch (TimeoutException e) {
            failure = "délai dépassé sur le thread principal";
        }

        boolean success = failure == null;
        markProcessed(action.id(), success, success ? null : failure);
        plugin.getLogger().info("[Web] Action faction #" + action.id() + " (" + action.type()
                + ", faction " + action.factionId() + ", joueur " + action.playerUuid() + ") : "
                + (success ? "done" : "failed — " + failure));
    }

    /**
     * Exécuté sur le thread principal. Retourne null en cas de succès,
     * sinon la raison métier de l'échec (stockée dans la colonne result).
     */
    private String execute(PendingAction action) {
        FactionManager factions = plugin.getFactionManager();

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(action.playerUuid());
        } catch (IllegalArgumentException | NullPointerException e) {
            return "player_uuid invalide : " + action.playerUuid();
        }

        Faction faction = factions.getFaction(action.factionId());
        if (faction == null) {
            return "faction introuvable (id " + action.factionId() + ")";
        }

        String type = action.type() == null ? "" : action.type().toLowerCase(Locale.ROOT);
        switch (type) {
            case "join" -> {
                if (faction.isMember(playerUuid)) {
                    return "le joueur est déjà membre de cette faction";
                }
                Faction current = factions.getPlayerFaction(playerUuid);
                if (current != null) {
                    return "le joueur est déjà dans une faction (" + current.getName() + ")";
                }
                return factions.addMember(faction.getId(), playerUuid, FactionRank.RECRUIT)
                        ? null : "échec de l'ajout du membre";
            }
            case "leave" -> {
                if (!faction.isMember(playerUuid)) {
                    return "le joueur n'est pas membre de cette faction";
                }
                if (faction.isLeader(playerUuid)) {
                    return "le leader ne peut pas quitter sa faction via le web";
                }
                return factions.removeMember(faction.getId(), playerUuid)
                        ? null : "échec du retrait du membre";
            }
            case "set_rank" -> {
                if (!faction.isMember(playerUuid)) {
                    return "le joueur n'est pas membre de cette faction";
                }
                FactionRank target = parseRank(action.payload());
                if (target == null) {
                    return "rang cible invalide : " + action.payload()
                            + " (attendu : RECRUIT, MEMBER, VETERAN ou OFFICER)";
                }
                if (target == FactionRank.LEADER) {
                    return "le leadership ne se transfère pas via le web";
                }
                if (faction.isLeader(playerUuid)) {
                    return "le rang du leader ne peut pas être modifié via le web";
                }
                return factions.setMemberRank(faction.getId(), playerUuid, target)
                        ? null : "échec du changement de rang";
            }
            case "disband" -> {
                if (!faction.isLeader(playerUuid)) {
                    return "seul le leader peut dissoudre la faction via le web";
                }
                return factions.deleteFaction(faction.getId())
                        ? null : "échec de la dissolution";
            }
            default -> {
                return "type d'action inconnu : " + action.type();
            }
        }
    }

    /**
     * Parsing strict du rang, contrairement à FactionRank.fromString qui retombe sur
     * RECRUIT : un payload inconnu doit faire échouer l'action, pas rétrograder
     * silencieusement un membre en recrue.
     */
    private FactionRank parseRank(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String normalized = payload.trim();
        for (FactionRank rank : FactionRank.values()) {
            if (rank.name().equalsIgnoreCase(normalized)) {
                return rank;
            }
        }
        return null;
    }

    private void markProcessed(long id, boolean success, String result) {
        String sql = "UPDATE web_faction_actions SET status = ?, result = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, success ? "done" : "failed");
                ps.setString(2, result == null || result.length() <= RESULT_MAX_LENGTH
                        ? result : result.substring(0, RESULT_MAX_LENGTH));
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebFactionBridge : impossible de marquer l'action #" + id + " : " + e.getMessage());
        }
    }
}
