package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MuteDAO {

    private final CantalePlugin plugin;

    public MuteDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createMute(Mute mute) {
        String sql = "INSERT INTO mutes (player_uuid, player_name, muted_by, reason, duration, muted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, mute.getPlayerUuid().toString());
            stmt.setString(2, mute.getPlayerName());
            stmt.setLong(3, mute.getMutedBy());
            stmt.setString(4, mute.getReason());
            stmt.setLong(5, mute.getDuration());
            stmt.setLong(6, mute.getMutedAt());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createMute : " + e.getMessage());
        }
        return -1;
    }

    public List<Mute> getActiveMutes() {
        List<Mute> mutes = new ArrayList<>();
        String sql = "SELECT * FROM mutes";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                mutes.add(extractMute(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getActiveMutes : " + e.getMessage());
        }
        return mutes;
    }

    public void deleteMute(UUID uuid) {
        String sql = "DELETE FROM mutes WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteMute : " + e.getMessage());
        }
    }

    private Mute extractMute(ResultSet rs) throws SQLException {
        return new Mute(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getLong("muted_by"),
                rs.getString("reason"),
                rs.getLong("duration"),
                rs.getLong("muted_at")
        );
    }
}
