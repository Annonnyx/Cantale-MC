package fr.cantale.plugin.world;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class WorldCycleRepository {

    private WorldCycleRepository() {
    }

    public static String createSql() {
        return """
                CREATE TABLE IF NOT EXISTS world_cycles (
                    world VARCHAR(64) PRIMARY KEY,
                    cycle INTEGER NOT NULL,
                    last_reset_at INTEGER NOT NULL
                )
                """;
    }

    public static Cycle read(Connection conn, String world) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT cycle, last_reset_at FROM world_cycles WHERE world = ?")) {
            ps.setString(1, world);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Cycle(rs.getLong("cycle"), rs.getLong("last_reset_at"));
                }
            }
        }
        return new Cycle(0L, 0L);
    }

    public static void write(Connection conn, String world, long cycle, long lastResetAt) throws SQLException {
        int updated;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE world_cycles SET cycle = ?, last_reset_at = ? WHERE world = ?")) {
            ps.setLong(1, cycle);
            ps.setLong(2, lastResetAt);
            ps.setString(3, world);
            updated = ps.executeUpdate();
        }
        if (updated > 0) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO world_cycles (world, cycle, last_reset_at) VALUES (?, ?, ?)")) {
            ps.setString(1, world);
            ps.setLong(2, cycle);
            ps.setLong(3, lastResetAt);
            ps.executeUpdate();
        }
    }

    public record Cycle(long cycle, long lastResetAt) {
    }
}
