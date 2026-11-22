package fr.cantale.plugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Réserve le daily du jour avant le crédit. Deux nœuds qui lisent la même ligne
 * ne gagnent qu'une fois : l'UPDATE conditionnel est la barrière, pas un verrou de server-id.
 */
public final class DailyClaimRepository {

    private DailyClaimRepository() {
    }

    public static boolean tryClaim(Connection conn, boolean sqlite, String uuid, String role, String today, int nowEpoch)
            throws SQLException {
        if (conn == null || uuid == null || today == null) {
            return false;
        }
        String insert = sqlite
                ? "INSERT OR IGNORE INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?)"
                : "INSERT INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE uuid = uuid";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, uuid);
            ps.setString(2, role == null || role.isBlank() ? "NONE" : role);
            ps.setInt(3, nowEpoch);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE player_permissions SET last_cantox_claim = ?
                WHERE uuid = ? AND (last_cantox_claim IS NULL OR last_cantox_claim <> ?)
                """)) {
            ps.setString(1, today);
            ps.setString(2, uuid);
            ps.setString(3, today);
            return ps.executeUpdate() == 1;
        }
    }
}
