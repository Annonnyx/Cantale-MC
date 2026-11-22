package fr.cantale.plugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Débit / crédit arithmétique du pouvoir de faction. Une seule requête, refus si le solde passerait sous zéro.
 */
public final class FactionPowerRepository {

    public static final String DEBIT_SQL = """
            UPDATE factions SET power = power - ?
            WHERE id = ? AND power >= ?
            """;

    public static final String CREDIT_SQL = """
            UPDATE factions SET power = power + ?
            WHERE id = ?
            """;

    private FactionPowerRepository() {
    }

    /**
     * @return {@code true} si exactement une ligne a été débitée
     */
    public static boolean debit(Connection conn, int factionId, int cost) throws SQLException {
        if (conn == null || factionId <= 0 || cost <= 0) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(DEBIT_SQL)) {
            ps.setInt(1, cost);
            ps.setInt(2, factionId);
            ps.setInt(3, cost);
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean credit(Connection conn, int factionId, int amount) throws SQLException {
        if (conn == null || factionId <= 0 || amount <= 0) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(CREDIT_SQL)) {
            ps.setInt(1, amount);
            ps.setInt(2, factionId);
            return ps.executeUpdate() == 1;
        }
    }

    public static int readPower(Connection conn, int factionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT power FROM factions WHERE id = ?")) {
            ps.setInt(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("faction " + factionId + " introuvable");
    }
}
