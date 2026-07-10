package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WarpDAO {

    private final CantalePlugin plugin;

    public WarpDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createWarp(Warp warp) {
        String sql = "INSERT INTO warps (name, world, x, y, z, yaw, pitch, is_event, is_active, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, warp.getName());
            stmt.setString(2, warp.getLocation().getWorld().getName());
            stmt.setDouble(3, warp.getLocation().getX());
            stmt.setDouble(4, warp.getLocation().getY());
            stmt.setDouble(5, warp.getLocation().getZ());
            stmt.setFloat(6, warp.getLocation().getYaw());
            stmt.setFloat(7, warp.getLocation().getPitch());
            stmt.setBoolean(8, warp.isEvent());
            stmt.setBoolean(9, warp.isActive());
            stmt.setLong(10, warp.getCreatedAt());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createWarp : " + e.getMessage());
        }
        return -1;
    }

    public Warp getWarp(int id) {
        String sql = "SELECT * FROM warps WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractWarp(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getWarp : " + e.getMessage());
        }
        return null;
    }

    public List<Warp> getAllWarps() {
        List<Warp> warps = new ArrayList<>();
        String sql = "SELECT * FROM warps";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                warps.add(extractWarp(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getAllWarps : " + e.getMessage());
        }
        return warps;
    }

    public void updateWarp(Warp warp) {
        String sql = "UPDATE warps SET is_active = ? WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, warp.isActive());
            stmt.setInt(2, warp.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateWarp : " + e.getMessage());
        }
    }

    public void deleteWarp(int id) {
        String sql = "DELETE FROM warps WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteWarp : " + e.getMessage());
        }
    }

    private Warp extractWarp(ResultSet rs) throws SQLException {
        return new Warp(
                rs.getInt("id"),
                rs.getString("name"),
                new Location(
                        plugin.getServer().getWorld(rs.getString("world")),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                ),
                rs.getBoolean("is_event"),
                rs.getBoolean("is_active"),
                rs.getLong("created_at")
        );
    }
}
