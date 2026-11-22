package fr.cantale.plugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mise à jour arithmétique du solde de faction. Une seule requête, refus si le résultat serait négatif.
 */
public final class FactionBalanceRepository {

    public static final String APPLY_DELTA_SQL = """
            UPDATE factions SET balance = balance + ?
            WHERE id = ? AND balance + ? >= 0
            """;

    private FactionBalanceRepository() {
    }

    /**
     * @return {@code true} si exactement une ligne a été modifiée
     */
    public static boolean applyDelta(Connection conn, int factionId, double delta) throws SQLException {
        if (conn == null || factionId <= 0) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(APPLY_DELTA_SQL)) {
            ps.setDouble(1, delta);
            ps.setInt(2, factionId);
            ps.setDouble(3, delta);
            return ps.executeUpdate() == 1;
        }
    }

    public static double readBalance(Connection conn, int factionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM factions WHERE id = ?")) {
            ps.setInt(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        throw new SQLException("faction " + factionId + " introuvable");
    }
}
