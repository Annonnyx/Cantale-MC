package fr.cantale.plugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Mise à jour arithmétique du solde. Une seule requête, refus si le résultat serait négatif.
 * L'ordre des affectations (pic puis solde) reste correct sur MySQL, qui évalue les SET de gauche à droite.
 */
public final class PlayerBalanceRepository {

    public static final String APPLY_DELTA_SQL = """
            UPDATE players SET
                balance_peak = CASE
                    WHEN balance + ? > COALESCE(balance_peak, 0) THEN balance + ?
                    ELSE balance_peak
                END,
                balance = balance + ?
            WHERE uuid = ? AND balance + ? >= 0
            """;

    private PlayerBalanceRepository() {
    }

    /**
     * @return {@code true} si exactement une ligne a été modifiée
     */
    public static boolean applyDelta(Connection conn, UUID uuid, double delta) throws SQLException {
        if (conn == null || uuid == null) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(APPLY_DELTA_SQL)) {
            ps.setDouble(1, delta);
            ps.setDouble(2, delta);
            ps.setDouble(3, delta);
            ps.setString(4, uuid.toString());
            ps.setDouble(5, delta);
            return ps.executeUpdate() == 1;
        }
    }
}
