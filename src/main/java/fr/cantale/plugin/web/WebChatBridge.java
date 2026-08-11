package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Consomme {@code web_chat_outbox} : chat global ou faction selon {@code faction_id}.
 */
public class WebChatBridge {

    private static final long INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 20L * 5;
    private static final int BATCH_SIZE = 20;
    private static final int RESULT_MAX = 255;

    private final CantalePlugin plugin;
    private BukkitTask task;

    public WebChatBridge(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollPending, INITIAL_DELAY_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[Web] WebChatBridge démarré (web_chat_outbox toutes les 2s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private record Pending(long id, String playerUuid, String playerName, String message, Integer factionId) {}

    private void pollPending() {
        for (Pending pending : fetchPending()) {
            process(pending);
        }
    }

    private List<Pending> fetchPending() {
        List<Pending> list = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, message, faction_id FROM web_chat_outbox " +
                "WHERE status = 'pending' ORDER BY id LIMIT " + BATCH_SIZE;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return list;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int fid = rs.getInt("faction_id");
                    Integer factionId = rs.wasNull() ? null : fid;
                    list.add(new Pending(
                            rs.getLong("id"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("message"),
                            factionId));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebChatBridge lecture impossible : " + e.getMessage());
        }
        return list;
    }

    private void process(Pending pending) {
        UUID uuid;
        try {
            uuid = UUID.fromString(pending.playerUuid());
        } catch (IllegalArgumentException e) {
            mark(pending.id(), "failed", "uuid invalide");
            return;
        }

        if (plugin.getModerationCommand() != null
                && plugin.getModerationCommand().getMuteManager() != null
                && plugin.getModerationCommand().getMuteManager().isMuted(uuid)) {
            mark(pending.id(), "failed", "joueur mute");
            return;
        }

        String name = pending.playerName() == null || pending.playerName().isBlank()
                ? "Joueur" : pending.playerName().trim();
        String message = pending.message() == null ? "" : pending.message().trim();
        if (message.isEmpty()) {
            mark(pending.id(), "failed", "message vide");
            return;
        }

        Integer factionId = pending.factionId();
        if (factionId != null && factionId > 0) {
            processFaction(pending.id(), uuid, name, message, factionId);
            return;
        }

        String line = "§8[§bWeb§8] §f" + name + "§7: §f" + message;
        try {
            CompletableFuture<Void> done = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Bukkit.broadcastMessage(line);
                    done.complete(null);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            });
            done.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            mark(pending.id(), "failed", truncate("broadcast: " + e.getMessage()));
            return;
        }

        WebChatLog.append(plugin, "web", uuid, name, message, null);

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendMcMessage(name + " (web)", message);
        }

        mark(pending.id(), "done", null);
    }

    private void processFaction(long id, UUID uuid, String name, String message, int factionId) {
        try {
            CompletableFuture<String> done = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    var fm = plugin.getFactionManager();
                    if (fm == null) {
                        done.complete("faction manager indisponible");
                        return;
                    }
                    Faction faction = fm.getFaction(factionId);
                    if (faction == null) {
                        done.complete("faction introuvable");
                        return;
                    }
                    if (!faction.isMember(uuid)) {
                        done.complete("pas membre de la faction");
                        return;
                    }
                    fm.sendFactionMessage(uuid, name, faction, message, false);
                    fr.cantale.plugin.web.WebChatLog.append(
                            plugin, "web", uuid, name, message, faction.getId());
                    done.complete(null);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            });
            String err = done.get(5, TimeUnit.SECONDS);
            if (err != null) {
                mark(id, "failed", err);
                return;
            }
            mark(id, "done", null);
        } catch (Exception e) {
            mark(id, "failed", truncate("faction: " + e.getMessage()));
        }
    }

    private void mark(long id, String status, String result) {
        String sql = "UPDATE web_chat_outbox SET status = ?, result = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                if (result == null) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, truncate(result));
                }
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebChatBridge mark #" + id + " impossible : " + e.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= RESULT_MAX ? value : value.substring(0, RESULT_MAX);
    }
}
