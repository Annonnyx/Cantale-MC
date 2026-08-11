package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Alimente la table server_status (partagée avec le site Next.js, Site-Cantale/web).
 *
 * La table ne contient qu'une seule ligne (id = 1), upsertée toutes les 60 secondes :
 * online_count, max_players, tps, updated_at. Le site ignore les lignes de plus de
 * 3 minutes, donc un serveur arrêté apparaît hors-ligne sans écriture supplémentaire.
 *
 * L'écriture JDBC est entièrement asynchrone : elle ne bloque jamais le thread
 * principal. Les lectures Bukkit (getOnlinePlayers, getMaxPlayers, getTPS) sont
 * des accès thread-safe sur Paper, comme déjà fait dans WantedTrackerTask.
 */
public class ServerStatusTask {

    private static final int ROW_ID = 1;
    private static final long INTERVAL_TICKS = 20L * 60; // 60 secondes
    private static final long INITIAL_DELAY_TICKS = 20L * 10; // première écriture 10s après le boot

    private final CantalePlugin plugin;
    private BukkitTask task;

    public ServerStatusTask(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::writeStatus, INITIAL_DELAY_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[Web] ServerStatusTask démarrée (upsert server_status toutes les 60s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void writeStatus() {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0;

        // Upsert compatible MySQL/MariaDB (ON DUPLICATE KEY UPDATE) et SQLite (INSERT OR REPLACE)
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
            if (conn == null) {
                return; // getConnection() a déjà loggé la tentative de reconnexion
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, onlineCount);
                ps.setInt(2, maxPlayers);
                ps.setDouble(3, tps);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] ServerStatusTask : écriture server_status impossible : " + e.getMessage());
        }
    }
}
