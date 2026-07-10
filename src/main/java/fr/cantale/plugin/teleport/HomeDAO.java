package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;

import java.util.UUID;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HomeDAO {

    private final CantalePlugin plugin;

    public HomeDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createHome(Home home) {
        String sql = "INSERT INTO homes (player_uuid, name, world, x, y, z, yaw, pitch, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, home.getPlayerUuid().toString());
            stmt.setString(2, home.getName());
            stmt.setString(3, home.getLocation().getWorld().getName());
            stmt.setDouble(4, home.getLocation().getX());
            stmt.setDouble(5, home.getLocation().getY());
            stmt.setDouble(6, home.getLocation().getZ());
            stmt.setFloat(7, home.getLocation().getYaw());
            stmt.setFloat(8, home.getLocation().getPitch());
            stmt.setLong(9, home.getCreatedAt());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createHome : " + e.getMessage());
        }
        return -1;
    }

    public Home getHome(int id) {
        String sql = "SELECT * FROM homes WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractHome(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getHome : " + e.getMessage());
        }
        return null;
    }

    public List<Home> getPlayerHomes(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        String sql = "SELECT * FROM homes WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                homes.add(extractHome(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPlayerHomes : " + e.getMessage());
        }
        return homes;
    }

    public void deleteHome(int id) {
        String sql = "DELETE FROM homes WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteHome : " + e.getMessage());
        }
    }

    private Home extractHome(ResultSet rs) throws SQLException {
        return new Home(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("name"),
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
