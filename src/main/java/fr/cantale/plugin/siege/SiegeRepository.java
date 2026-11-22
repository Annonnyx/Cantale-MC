package fr.cantale.plugin.siege;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Sièges et blocs réellement modifiés. Même style de migration que le reste du plugin.
 */
public final class SiegeRepository {

    private SiegeRepository() {
    }

    public static String createSessionsSql(boolean sqlite) {
        String key = sqlite ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INTEGER PRIMARY KEY AUTO_INCREMENT";
        return """
                CREATE TABLE IF NOT EXISTS siege_sessions (
                    id %s,
                    attacker_faction_id INTEGER NOT NULL,
                    defender_faction_id INTEGER NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    started_at INTEGER NOT NULL,
                    ends_at INTEGER NOT NULL,
                    regen_at INTEGER NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL
                )
                """.formatted(key);
    }

    public static String createBlocksSql(boolean sqlite) {
        String key = sqlite ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INTEGER PRIMARY KEY AUTO_INCREMENT";
        return """
                CREATE TABLE IF NOT EXISTS siege_block_changes (
                    id %s,
                    siege_id INTEGER NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    before_material VARCHAR(64) NOT NULL,
                    before_data TEXT NOT NULL,
                    restored INTEGER NOT NULL DEFAULT 0,
                    UNIQUE (siege_id, world, x, y, z)
                )
                """.formatted(key);
    }

    public static int insertSession(Connection conn, int attackerId, int defenderId, String world,
                                     int x, int y, int z, long startedAt, long endsAt) throws SQLException {
        String sql = """
                INSERT INTO siege_sessions
                    (attacker_faction_id, defender_faction_id, world, x, y, z, started_at, ends_at, regen_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, attackerId);
            ps.setInt(2, defenderId);
            ps.setString(3, world);
            ps.setInt(4, x);
            ps.setInt(5, y);
            ps.setInt(6, z);
            ps.setLong(7, startedAt);
            ps.setLong(8, endsAt);
            ps.setString(9, SiegeRules.ACTIVE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    public static void deleteSession(Connection conn, int id) throws SQLException {
        try (PreparedStatement blocks = conn.prepareStatement(
                "DELETE FROM siege_block_changes WHERE siege_id = ?")) {
            blocks.setInt(1, id);
            blocks.executeUpdate();
        }
        try (PreparedStatement session = conn.prepareStatement(
                "DELETE FROM siege_sessions WHERE id = ?")) {
            session.setInt(1, id);
            session.executeUpdate();
        }
    }

    public static void updateSession(Connection conn, int id, String status, long regenAt) throws SQLException {
        String sql = "UPDATE siege_sessions SET status = ?, regen_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, regenAt);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public static List<SiegeSession> loadOpen(Connection conn) throws SQLException {
        String sql = """
                SELECT id, attacker_faction_id, defender_faction_id, world, x, y, z,
                       started_at, ends_at, regen_at, status
                FROM siege_sessions
                WHERE status IN (?, ?, ?)
                """;
        List<SiegeSession> sessions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, SiegeRules.ACTIVE);
            ps.setString(2, SiegeRules.ENDED);
            ps.setString(3, SiegeRules.REGENERATING);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new SiegeSession(
                            rs.getInt("id"),
                            rs.getInt("attacker_faction_id"),
                            rs.getInt("defender_faction_id"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getLong("started_at"),
                            rs.getLong("ends_at"),
                            rs.getLong("regen_at"),
                            rs.getString("status")
                    ));
                }
            }
        }
        return sessions;
    }

    /** Garde le premier état connu du bloc. Les changements suivants ne l'écrasent pas. */
    public static void insertBlockIfAbsent(Connection conn, int siegeId, String world, int x, int y, int z,
                                            String material, String data) throws SQLException {
        String check = """
                SELECT id FROM siege_block_changes
                WHERE siege_id = ? AND world = ? AND x = ? AND y = ? AND z = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setInt(1, siegeId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String insert = """
                INSERT INTO siege_block_changes
                    (siege_id, world, x, y, z, before_material, before_data, restored)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setInt(1, siegeId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.setString(6, material);
            ps.setString(7, data == null ? material : data);
            ps.executeUpdate();
        }
    }

    public static List<BlockRow> nextUnrestored(Connection conn, int siegeId, int limit) throws SQLException {
        String sql = """
                SELECT id, world, x, y, z, before_material, before_data
                FROM siege_block_changes
                WHERE siege_id = ? AND restored = 0
                ORDER BY id ASC
                LIMIT ?
                """;
        List<BlockRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, siegeId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BlockRow(
                            rs.getLong("id"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getString("before_material"),
                            rs.getString("before_data")
                    ));
                }
            }
        }
        return rows;
    }

    public static void markRestored(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE siege_block_changes SET restored = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public record BlockRow(long id, String world, int x, int y, int z, String material, String data) {
    }
}
