package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;

public class ReinforcedBlockDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public ReinforcedBlockDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public boolean isReinforced(String world, int x, int y, int z) {
        String sql = "SELECT 1 FROM reinforced_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur isReinforced : " + e.getMessage());
        }
        return false;
    }

    public int getFactionId(String world, int x, int y, int z) {
        String sql = "SELECT faction_id FROM reinforced_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("faction_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFactionId : " + e.getMessage());
        }
        return -1;
    }

    public boolean addReinforcedBlock(String world, int x, int y, int z, int factionId) {
        String sql = "INSERT INTO reinforced_blocks (world, x, y, z, faction_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.setInt(5, factionId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur addReinforcedBlock : " + e.getMessage());
        }
        return false;
    }

    public boolean removeReinforcedBlock(String world, int x, int y, int z) {
        String sql = "DELETE FROM reinforced_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeReinforcedBlock : " + e.getMessage());
        }
        return false;
    }
}
