package fr.cantale.plugin.anticheat.data;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persiste chaque violation anti-cheat (native ou Grim) dans la table
 * {@code anticheat_violations} — preuve exploitable par le staff.
 *
 * Les insertions sont asynchrones pour ne pas impacter le tick principal ;
 * les lectures (ex. {@code /ac info}) sont synchrones et courtes.
 */
public class ViolationLogger {

    private final CantalePlugin plugin;

    public ViolationLogger(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre une violation de façon asynchrone.
     */
    public void logAsync(Player player, String source, String checkName, int vl, int trustAfter, String details) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        int ping = player.getPing();
        double tps;
        try {
            tps = Bukkit.getTPS()[0];
        } catch (Throwable t) {
            tps = 20.0;
        }
        final double tpsFinal = tps;
        // Convention du plugin : timestamps epoch en SECONDES dans des colonnes INTEGER
        // (comme factions.created_at, faction_bank_log.created_at, diplomacy.expires_at, ...).
        // Des millis (~1,7e12) dépassent la capacité d'un INT MySQL (~2,1e9) → insertion rejetée.
        long now = System.currentTimeMillis() / 1000L;
        String safeDetails = details == null ? "" : (details.length() > 500 ? details.substring(0, 500) : details);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                if (conn == null) return;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO anticheat_violations (player_uuid, player_name, source, check_name, vl, trust_after, ping, tps, details, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, uuid);
                    ps.setString(2, name);
                    ps.setString(3, source);
                    ps.setString(4, checkName);
                    ps.setInt(5, vl);
                    ps.setInt(6, trustAfter);
                    ps.setInt(7, ping);
                    ps.setDouble(8, tpsFinal);
                    ps.setString(9, safeDetails);
                    ps.setLong(10, now);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[AntiCheat] Échec log violation BDD : " + e.getMessage());
            }
        });
    }

    /**
     * Retourne les violations les plus récentes d'un joueur (toutes sources).
     */
    public List<ViolationEntry> getRecentViolations(UUID playerId, int limit) {
        List<ViolationEntry> entries = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return entries;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT source, check_name, vl, trust_after, ping, tps, details, created_at "
                            + "FROM anticheat_violations WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, playerId.toString());
                ps.setInt(2, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new ViolationEntry(
                                rs.getString("source"),
                                rs.getString("check_name"),
                                rs.getInt("vl"),
                                rs.getInt("trust_after"),
                                rs.getInt("ping"),
                                rs.getDouble("tps"),
                                rs.getString("details"),
                                rs.getLong("created_at")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiCheat] Échec lecture violations BDD : " + e.getMessage());
        }
        return entries;
    }

    /**
     * Une violation persistée en BDD.
     * {@code createdAt} est un timestamp epoch en SECONDES (convention du plugin).
     */
    public record ViolationEntry(String source, String checkName, int vl, int trustAfter,
                                 int ping, double tps, String details, long createdAt) {
    }
}
