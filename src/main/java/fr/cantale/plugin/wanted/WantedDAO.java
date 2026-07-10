package fr.cantale.plugin.wanted;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WantedDAO {

    private final CantalePlugin plugin;

    public WantedDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createWanted(Wanted wanted) {
        String sql = "INSERT INTO wanted (target_uuid, target_name, issuer_uuid, issuer_name, reward, issued_at, reason, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, wanted.getTargetUuid().toString());
            stmt.setString(2, wanted.getTargetName());
            stmt.setString(3, wanted.getIssuerUuid().toString());
            stmt.setString(4, wanted.getIssuerName());
            stmt.setDouble(5, wanted.getReward());
            stmt.setLong(6, wanted.getIssuedAt());
            stmt.setString(7, wanted.getReason());
            stmt.setBoolean(8, wanted.isActive());
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createWanted: " + e.getMessage());
        }
        return -1;
    }

    public List<Wanted> getActiveWanted() {
        List<Wanted> list = new ArrayList<>();
        String sql = "SELECT * FROM wanted WHERE active = 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(extractWanted(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getActiveWanted: " + e.getMessage());
        }
        return list;
    }

    public Wanted getWantedByTarget(UUID targetUuid) {
        String sql = "SELECT * FROM wanted WHERE target_uuid = ? AND active = 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return extractWanted(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getWantedByTarget: " + e.getMessage());
        }
        return null;
    }

    public void completeWanted(int id) {
        String sql = "UPDATE wanted SET active = 0 WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur completeWanted: " + e.getMessage());
        }
    }

    private Wanted extractWanted(ResultSet rs) throws SQLException {
        return new Wanted(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                UUID.fromString(rs.getString("issuer_uuid")),
                rs.getString("issuer_name"),
                rs.getDouble("reward"),
                rs.getLong("issued_at"),
                rs.getString("reason"),
                rs.getBoolean("active")
        );
    }
}
