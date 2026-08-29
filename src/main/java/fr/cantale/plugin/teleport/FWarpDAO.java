package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FWarpDAO {

    private final CantalePlugin plugin;

    public FWarpDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createFWarp(FWarp fwarp) {
        String sql = "INSERT INTO fwarps (name, faction_id, creator_uuid, world, x, y, z, yaw, pitch, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, fwarp.getName());
            stmt.setInt(2, fwarp.getFactionId());
            stmt.setString(3, fwarp.getCreatorUuid().toString());
            stmt.setString(4, fwarp.getLocation().getWorld().getName());
            stmt.setDouble(5, fwarp.getLocation().getX());
            stmt.setDouble(6, fwarp.getLocation().getY());
            stmt.setDouble(7, fwarp.getLocation().getZ());
            stmt.setFloat(8, fwarp.getLocation().getYaw());
            stmt.setFloat(9, fwarp.getLocation().getPitch());
            stmt.setLong(10, fwarp.getCreatedAt());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createFWarp : " + e.getMessage());
        }
        return -1;
    }

    public List<FWarp> getAllFWarps() {
        List<FWarp> fwarps = new ArrayList<>();
        String sql = "SELECT * FROM fwarps";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                fwarps.add(extractFWarp(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getAllFWarps : " + e.getMessage());
        }
        return fwarps;
    }

    public void deleteFWarp(int id) {
        String sql = "DELETE FROM fwarps WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteFWarp : " + e.getMessage());
        }
    }

    private FWarp extractFWarp(ResultSet rs) throws SQLException {
        UUID creatorUuid;
        try {
            creatorUuid = UUID.fromString(rs.getString("creator_uuid"));
        } catch (IllegalArgumentException e) {
            creatorUuid = new UUID(0L, 0L);
        }
        return new FWarp(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getInt("faction_id"),
                creatorUuid,
                new Location(
                        plugin.getServer().getWorld(rs.getString("world")),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                ),
                rs.getLong("created_at")
        );
    }
}
