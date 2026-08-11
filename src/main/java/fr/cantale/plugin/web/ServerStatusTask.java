package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert {@code server_status} toutes les 60s : compteurs + JSON des joueurs en ligne
 * pour la page admin du site.
 */
public class ServerStatusTask {

    private static final int ROW_ID = 1;
    private static final long INTERVAL_TICKS = 20L * 60;
    private static final long INITIAL_DELAY_TICKS = 20L * 10;

    private final CantalePlugin plugin;
    private BukkitTask task;

    public ServerStatusTask(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::writeStatus, INITIAL_DELAY_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[Web] ServerStatusTask démarrée (server_status toutes les 60s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void writeStatus() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int onlineCount = online.size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0;
        String onlineJson = buildOnlineJson(online);

        String sql;
        if (plugin.getDatabaseManager().isSQLite()) {
            sql = "INSERT OR REPLACE INTO server_status (id, online_count, max_players, tps, online_json, updated_at) " +
                    "VALUES (" + ROW_ID + ", ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        } else {
            sql = "INSERT INTO server_status (id, online_count, max_players, tps, online_json, updated_at) " +
                    "VALUES (" + ROW_ID + ", ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE online_count = VALUES(online_count), " +
                    "max_players = VALUES(max_players), tps = VALUES(tps), " +
                    "online_json = VALUES(online_json), updated_at = VALUES(updated_at)";
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, onlineCount);
                ps.setInt(2, maxPlayers);
                ps.setDouble(3, tps);
                ps.setString(4, onlineJson);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Colonne online_json absente tant que la migration n'a pas tourné : fallback sans JSON.
            writeStatusLegacy(onlineCount, maxPlayers, tps);
            plugin.getLogger().warning("[Web] ServerStatusTask : " + e.getMessage());
        }
    }

    private void writeStatusLegacy(int onlineCount, int maxPlayers, double tps) {
        String sql;
        if (plugin.getDatabaseManager().isSQLite()) {
            sql = "INSERT OR REPLACE INTO server_status (id, online_count, max_players, tps, updated_at) " +
                    "VALUES (" + ROW_ID + ", ?, ?, ?, CURRENT_TIMESTAMP)";
        } else {
            sql = "INSERT INTO server_status (id, online_count, max_players, tps, updated_at) " +
                    "VALUES (" + ROW_ID + ", ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE online_count = VALUES(online_count), " +
                    "max_players = VALUES(max_players), tps = VALUES(tps), updated_at = VALUES(updated_at)";
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, onlineCount);
                ps.setInt(2, maxPlayers);
                ps.setDouble(3, tps);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    private String buildOnlineJson(List<Player> online) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Player player : online) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"uuid\":\"")
                    .append(player.getUniqueId())
                    .append("\",\"name\":\"")
                    .append(escapeJson(player.getName()))
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
