package fr.cantale.plugin.cosmetics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tables idempotentes du pass. La progression n'écrit jamais {@code players.balance} ni {@code players.lives}.
 */
public final class SeasonPassRepository {

    private SeasonPassRepository() {
    }

    public static List<String> schema() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS cosmetic_seasons (
                    id INTEGER PRIMARY KEY,
                    name VARCHAR(64) NOT NULL,
                    starts_at INTEGER NOT NULL,
                    ends_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS cosmetic_tiers (
                    season_id INTEGER NOT NULL,
                    tier INTEGER NOT NULL,
                    xp_required INTEGER NOT NULL,
                    cosmetic_id VARCHAR(64) NOT NULL,
                    kind VARCHAR(16) NOT NULL,
                    PRIMARY KEY (season_id, tier)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_season_progress (
                    uuid VARCHAR(36) NOT NULL,
                    season_id INTEGER NOT NULL,
                    xp INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, season_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_cosmetics (
                    uuid VARCHAR(36) NOT NULL,
                    cosmetic_id VARCHAR(64) NOT NULL,
                    kind VARCHAR(16) NOT NULL,
                    equipped INTEGER NOT NULL DEFAULT 0,
                    unlocked_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, cosmetic_id)
                )
                """
        );
    }

    public static void ensureSeason(Connection conn, long nowSeconds) throws SQLException {
        long end = nowSeconds + SeasonPassRules.SEASON_DAYS * 86_400L;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO cosmetic_seasons (id, name, starts_at, ends_at)
                SELECT ?, 'Saison 1', ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM cosmetic_seasons WHERE id = ?)
                """)) {
            ps.setInt(1, SeasonPassRules.SEASON_ID);
            ps.setLong(2, nowSeconds);
            ps.setLong(3, end);
            ps.setInt(4, SeasonPassRules.SEASON_ID);
            ps.executeUpdate();
        }
        for (CosmeticDefinition definition : CosmeticDefinition.SEASON_ONE) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO cosmetic_tiers (season_id, tier, xp_required, cosmetic_id, kind)
                    SELECT ?, ?, ?, ?, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM cosmetic_tiers WHERE season_id = ? AND tier = ?
                    )
                    """)) {
                ps.setInt(1, SeasonPassRules.SEASON_ID);
                ps.setInt(2, definition.tier());
                ps.setInt(3, SeasonPassRules.xpRequired(definition.tier()));
                ps.setString(4, definition.id());
                ps.setString(5, definition.kind().name());
                ps.setInt(6, SeasonPassRules.SEASON_ID);
                ps.setInt(7, definition.tier());
                ps.executeUpdate();
            }
        }
    }

    public static int addXp(Connection conn, boolean sqlite, UUID uuid, int amount) throws SQLException {
        if (amount <= 0) {
            return readXp(conn, uuid);
        }
        String sql = sqlite
                ? """
                INSERT INTO player_season_progress (uuid, season_id, xp) VALUES (?, ?, ?)
                ON CONFLICT(uuid, season_id) DO UPDATE SET xp = xp + excluded.xp
                """
                : """
                INSERT INTO player_season_progress (uuid, season_id, xp) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, SeasonPassRules.SEASON_ID);
            ps.setInt(3, amount);
            ps.executeUpdate();
        }
        return readXp(conn, uuid);
    }

    public static int readXp(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT xp FROM player_season_progress WHERE uuid = ? AND season_id = ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, SeasonPassRules.SEASON_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public static void unlockThrough(Connection conn, UUID uuid, int tier, long nowSeconds) throws SQLException {
        for (CosmeticDefinition definition : CosmeticDefinition.SEASON_ONE) {
            if (definition.tier() > tier) {
                continue;
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO player_cosmetics (uuid, cosmetic_id, kind, equipped, unlocked_at)
                    SELECT ?, ?, ?, 0, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM player_cosmetics WHERE uuid = ? AND cosmetic_id = ?
                    )
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, definition.id());
                ps.setString(3, definition.kind().name());
                ps.setLong(4, nowSeconds);
                ps.setString(5, uuid.toString());
                ps.setString(6, definition.id());
                ps.executeUpdate();
            }
        }
    }

    public static boolean owns(Connection conn, UUID uuid, String cosmeticId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM player_cosmetics WHERE uuid = ? AND cosmetic_id = ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, cosmeticId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Équipe seulement si la ligne existe. Le second UPDATE est la revalidation serveur.
     */
    public static boolean equip(Connection conn, UUID uuid, CosmeticDefinition definition) throws SQLException {
        if (!owns(conn, uuid, definition.id())) {
            return false;
        }
        try (PreparedStatement clear = conn.prepareStatement("""
                UPDATE player_cosmetics SET equipped = 0 WHERE uuid = ? AND kind = ?
                """)) {
            clear.setString(1, uuid.toString());
            clear.setString(2, definition.kind().name());
            clear.executeUpdate();
        }
        try (PreparedStatement set = conn.prepareStatement("""
                UPDATE player_cosmetics SET equipped = 1 WHERE uuid = ? AND cosmetic_id = ?
                """)) {
            set.setString(1, uuid.toString());
            set.setString(2, definition.id());
            return set.executeUpdate() == 1;
        }
    }

    public static boolean unequip(Connection conn, UUID uuid, CosmeticKind kind) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE player_cosmetics SET equipped = 0 WHERE uuid = ? AND kind = ? AND equipped = 1
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kind.name());
            return ps.executeUpdate() > 0;
        }
    }

    public record Equipped(String id, CosmeticKind kind) {
    }

    public static List<Equipped> equipped(Connection conn, UUID uuid) throws SQLException {
        List<Equipped> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT cosmetic_id, kind FROM player_cosmetics WHERE uuid = ? AND equipped = 1
                """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CosmeticKind kind = CosmeticKind.valueOf(rs.getString("kind"));
                    out.add(new Equipped(rs.getString("cosmetic_id"), kind));
                }
            }
        }
        return out;
    }
}
