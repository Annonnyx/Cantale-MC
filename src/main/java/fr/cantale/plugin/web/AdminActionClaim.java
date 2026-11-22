package fr.cantale.plugin.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Un message admin Redis est livré à chaque nœud. La ligne {@code pending} ne passe
 * en {@code processing} qu'une fois : le second nœud ne rejoue pas le crédit.
 */
public final class AdminActionClaim {

    public static final String CLAIM_SQL =
            "UPDATE web_admin_actions SET status = 'processing' WHERE id = ? AND status = 'pending'";

    public static final String CHAT_CLAIM_SQL =
            "UPDATE web_chat_outbox SET status = 'processing' WHERE id = ? AND status = 'pending'";

    private AdminActionClaim() {
    }

    public static boolean claim(Connection conn, long id) throws SQLException {
        return update(conn, CLAIM_SQL, id);
    }

    public static boolean claimChatArchive(Connection conn, long id) throws SQLException {
        return update(conn, CHAT_CLAIM_SQL, id);
    }

    private static boolean update(Connection conn, String sql, long id) throws SQLException {
        if (conn == null || id <= 0) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }
}
