package fr.cantale.plugin.skills;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkillRepository {

    private SkillRepository() {
    }

    public static String createSql() {
        return """
                CREATE TABLE IF NOT EXISTS player_skills (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    skill VARCHAR(16) NOT NULL,
                    changed_at INTEGER NOT NULL
                )
                """;
    }

    public static Map<UUID, StoredSkill> loadAll(Connection conn) throws SQLException {
        Map<UUID, StoredSkill> skills = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_uuid, skill, changed_at FROM player_skills");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Skill skill = Skill.fromArgument(rs.getString("skill"));
                if (skill == null) {
                    continue;
                }
                skills.put(UUID.fromString(rs.getString("player_uuid")),
                        new StoredSkill(skill, rs.getLong("changed_at")));
            }
        }
        return skills;
    }

    public static void upsert(Connection conn, UUID playerId, Skill skill, long changedAt, boolean sqlite)
            throws SQLException {
        String sql = sqlite
                ? """
                INSERT INTO player_skills (player_uuid, skill, changed_at) VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET skill = excluded.skill, changed_at = excluded.changed_at
                """
                : """
                INSERT INTO player_skills (player_uuid, skill, changed_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE skill = VALUES(skill), changed_at = VALUES(changed_at)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, skill.argument());
            ps.setLong(3, changedAt);
            ps.executeUpdate();
        }
    }

    public record StoredSkill(Skill skill, long changedAt) {
    }
}
