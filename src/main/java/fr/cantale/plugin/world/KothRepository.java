package fr.cantale.plugin.world;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class KothRepository {

    private KothRepository() {
    }

    public static String createSql() {
        return """
                CREATE TABLE IF NOT EXISTS koth_rewards (
                    faction_id INTEGER NOT NULL,
                    day_key INTEGER NOT NULL,
                    captures INTEGER NOT NULL,
                    last_reward_at INTEGER NOT NULL,
                    PRIMARY KEY (faction_id, day_key)
                )
                """;
    }

    public static RewardState read(Connection conn, int factionId, long dayKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT captures, last_reward_at FROM koth_rewards
                WHERE faction_id = ? AND day_key = ?
                """)) {
            ps.setInt(1, factionId);
            ps.setLong(2, dayKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RewardState(rs.getInt("captures"), rs.getLong("last_reward_at"));
                }
            }
        }
        return new RewardState(0, 0L);
    }

    public static void write(Connection conn, int factionId, long dayKey, int captures, long lastRewardAt)
            throws SQLException {
        int updated;
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE koth_rewards SET captures = ?, last_reward_at = ?
                WHERE faction_id = ? AND day_key = ?
                """)) {
            ps.setInt(1, captures);
            ps.setLong(2, lastRewardAt);
            ps.setInt(3, factionId);
            ps.setLong(4, dayKey);
            updated = ps.executeUpdate();
        }
        if (updated > 0) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO koth_rewards (faction_id, day_key, captures, last_reward_at)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setInt(1, factionId);
            ps.setLong(2, dayKey);
            ps.setInt(3, captures);
            ps.setLong(4, lastRewardAt);
            ps.executeUpdate();
        }
    }

    /**
     * Réserve une prise. L'UPDATE conditionnel puis l'INSERT ne laissent qu'un gagnant
     * si deux serveurs lisent la même ligne.
     */
    public static boolean claimSlot(Connection conn, int factionId, long dayKey, int maxCaptures,
                                    long now, long cooldownSeconds) throws SQLException {
        if (updateSlot(conn, factionId, dayKey, maxCaptures, now, cooldownSeconds)) {
            return true;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO koth_rewards (faction_id, day_key, captures, last_reward_at)
                VALUES (?, ?, 1, ?)
                """)) {
            ps.setInt(1, factionId);
            ps.setLong(2, dayKey);
            ps.setLong(3, now);
            ps.executeUpdate();
            return true;
        } catch (SQLException insertFailed) {
            return updateSlot(conn, factionId, dayKey, maxCaptures, now, cooldownSeconds);
        }
    }

    private static boolean updateSlot(Connection conn, int factionId, long dayKey, int maxCaptures,
                                      long now, long cooldownSeconds) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE koth_rewards
                SET captures = captures + 1, last_reward_at = ?
                WHERE faction_id = ? AND day_key = ?
                  AND captures < ?
                  AND (last_reward_at <= 0 OR (? - last_reward_at) >= ?)
                """)) {
            ps.setLong(1, now);
            ps.setInt(2, factionId);
            ps.setLong(3, dayKey);
            ps.setInt(4, maxCaptures);
            ps.setLong(5, now);
            ps.setLong(6, cooldownSeconds);
            return ps.executeUpdate() == 1;
        }
    }

    public record RewardState(int captures, long lastRewardAt) {
    }
}
