package fr.cantale.plugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Accorde des vies seulement si le joueur est à 0 (ou moins). Un second appel ne duplique pas.
 */
public final class PlayerLifeRepository {

    public static final String GRANT_IF_DEPLETED_SQL = """
            UPDATE players SET lives = ? WHERE uuid = ? AND lives <= 0
            """;

    private PlayerLifeRepository() {
    }

    /**
     * @return {@code true} si cette invocation a bien fait passer le joueur de 0 à {@code lives}
     */
    public static boolean grantIfDepleted(Connection conn, UUID uuid, int lives) throws SQLException {
        if (conn == null || uuid == null || lives <= 0) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(GRANT_IF_DEPLETED_SQL)) {
            ps.setInt(1, lives);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() == 1;
        }
    }

    public static int readLives(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT lives FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("joueur " + uuid + " introuvable");
    }
}
