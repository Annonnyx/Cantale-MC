package fr.cantale.plugin.trade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistance des items de /trade à rendre à un joueur hors ligne.
 * <p>
 * Livraison : {@code pending} → {@code delivering} (claim exclusif), don, puis
 * {@code handed_off = 1} et suppression. Un crash entre le don et le DELETE
 * ne redonne pas la ligne si le don a été accusé ou si l'objet est déjà chez
 * le joueur. Un {@code delivering} dont le don n'a pas eu lieu est réessayé
 * après abandon du claim (redémarrage).
 */
public final class TradePendingReturnRepository {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DELIVERING = "delivering";

    public static final String CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_trade_pending_player ON trade_pending_returns (player_uuid)";

    public enum Delivery {
        /** Cet appelant doit donner l'objet une fois, puis accuser réception. */
        GIVE,
        /** Déjà donné, ou claim tenu par un autre essai encore vivant. */
        SKIP
    }

    private TradePendingReturnRepository() {
    }

    public static String createTableSql(boolean sqlite) {
        String primaryKey = sqlite
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "INTEGER PRIMARY KEY AUTO_INCREMENT";
        return """
                CREATE TABLE IF NOT EXISTS trade_pending_returns (
                    id %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_data TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'pending',
                    handed_off INTEGER NOT NULL DEFAULT 0,
                    lock_token VARCHAR(36)
                )
                """.formatted(primaryKey);
    }

    /** Colonnes ajoutées après la première version de la table. */
    public static List<String> upgradeStatements() {
        return List.of(
                "ALTER TABLE trade_pending_returns ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'pending'",
                "ALTER TABLE trade_pending_returns ADD COLUMN handed_off INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE trade_pending_returns ADD COLUMN lock_token VARCHAR(36)");
    }

    public record Row(long id, UUID playerId, String itemData, long createdAt,
                      String status, boolean handedOff, String lockToken) {
    }

    public static long insert(Connection conn, UUID playerId, String itemData, long createdAt) throws SQLException {
        String sql = """
                INSERT INTO trade_pending_returns
                    (player_uuid, item_data, created_at, status, handed_off)
                VALUES (?, ?, ?, 'pending', 0)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, itemData);
            ps.setLong(3, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    /**
     * Décide qui a le droit de donner cette ligne.
     *
     * @param witnessed l'objet de ce retour est déjà en possession du joueur
     */
    public static Delivery resolve(Connection conn, long id, String token, boolean witnessed) throws SQLException {
        if (conn == null || id <= 0 || token == null || token.isBlank()) {
            return Delivery.SKIP;
        }
        if (witnessed) {
            markHandedOff(conn, id);
            deleteIfHandedOff(conn, id);
            return Delivery.SKIP;
        }
        int claimed = update(conn, """
                UPDATE trade_pending_returns
                SET status = 'delivering', lock_token = ?
                WHERE id = ? AND status = 'pending' AND handed_off = 0
                """, token, id);
        if (claimed == 1) {
            return Delivery.GIVE;
        }
        Row row = select(conn, id);
        if (row == null) {
            return Delivery.SKIP;
        }
        if (row.handedOff()) {
            deleteIfHandedOff(conn, id);
            return Delivery.SKIP;
        }
        if (STATUS_DELIVERING.equals(row.status()) && (row.lockToken() == null || row.lockToken().isBlank())) {
            int took = update(conn, """
                    UPDATE trade_pending_returns
                    SET lock_token = ?
                    WHERE id = ? AND status = 'delivering' AND handed_off = 0 AND lock_token IS NULL
                    """, token, id);
            if (took == 1) {
                return Delivery.GIVE;
            }
        }
        return Delivery.SKIP;
    }

    /** Le don a réussi. La ligne ne sera plus jamais redonnée. */
    public static boolean acknowledge(Connection conn, long id) throws SQLException {
        return markHandedOff(conn, id);
    }

    /**
     * Le don n'a pas eu lieu : la ligne redevient {@code pending} et reste en base.
     * N'efface pas un accusé déjà enregistré.
     */
    public static boolean release(Connection conn, long id, String token) throws SQLException {
        if (conn == null || id <= 0 || token == null) {
            return false;
        }
        return update(conn, """
                UPDATE trade_pending_returns
                SET status = 'pending', lock_token = NULL
                WHERE id = ? AND lock_token = ? AND handed_off = 0
                """, id, token) == 1;
    }

    public static void deleteIfHandedOff(Connection conn, long id) throws SQLException {
        if (conn == null || id <= 0) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM trade_pending_returns WHERE id = ? AND handed_off = 1")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Au redémarrage, un claim {@code delivering} sans accusé n'a plus de propriétaire.
     * Le prochain essai peut le reprendre. Un accusé déjà posé n'est pas touché.
     */
    public static int abandonOpenClaims(Connection conn) throws SQLException {
        if (conn == null) {
            return 0;
        }
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    UPDATE trade_pending_returns
                    SET lock_token = NULL
                    WHERE status = 'delivering' AND handed_off = 0
                    """);
        }
    }

    public static List<Row> loadAll(Connection conn) throws SQLException {
        String sql = """
                SELECT id, player_uuid, item_data, created_at, status, handed_off, lock_token
                FROM trade_pending_returns ORDER BY id
                """;
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(read(rs));
            }
        }
        return rows;
    }

    public static List<Row> loadByPlayer(Connection conn, UUID playerId) throws SQLException {
        String sql = """
                SELECT id, player_uuid, item_data, created_at, status, handed_off, lock_token
                FROM trade_pending_returns WHERE player_uuid = ? ORDER BY id
                """;
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(read(rs));
                }
            }
        }
        return rows;
    }

    public static void delete(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM trade_pending_returns WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static boolean markHandedOff(Connection conn, long id) throws SQLException {
        if (conn == null || id <= 0) {
            return false;
        }
        return update(conn, """
                UPDATE trade_pending_returns
                SET handed_off = 1
                WHERE id = ? AND handed_off = 0
                """, id) == 1;
    }

    private static Row select(Connection conn, long id) throws SQLException {
        String sql = """
                SELECT id, player_uuid, item_data, created_at, status, handed_off, lock_token
                FROM trade_pending_returns WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return read(rs);
                }
            }
        }
        return null;
    }

    private static int update(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (arg instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else if (arg instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setObject(i + 1, arg);
                }
            }
            return ps.executeUpdate();
        }
    }

    private static Row read(ResultSet rs) throws SQLException {
        return new Row(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("item_data"),
                rs.getLong("created_at"),
                rs.getString("status"),
                rs.getInt("handed_off") == 1,
                rs.getString("lock_token"));
    }
}
