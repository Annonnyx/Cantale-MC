package fr.cantale.plugin.collections;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CollectionDAO {

    private final CantalePlugin plugin;

    public CollectionDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void saveAmount(UUID uuid, String category, String key, long amount) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO collection_progress (player_uuid, category, progress_key, amount) VALUES (?, ?, ?, ?)"
                : "INSERT INTO collection_progress (player_uuid, category, progress_key, amount) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, category);
            stmt.setString(3, key);
            stmt.setLong(4, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Collections] saveAmount : " + e.getMessage());
        }
    }

    public Map<String, Long> loadAmounts(UUID uuid) {
        Map<String, Long> map = new HashMap<>();
        String sql = "SELECT category, progress_key, amount FROM collection_progress WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("category") + ":" + rs.getString("progress_key"), rs.getLong("amount"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Collections] loadAmounts : " + e.getMessage());
        }
        return map;
    }

    public void claim(UUID uuid, String category, String key, int tier) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR IGNORE INTO collection_claimed (player_uuid, category, progress_key, tier) VALUES (?, ?, ?, ?)"
                : "INSERT IGNORE INTO collection_claimed (player_uuid, category, progress_key, tier) VALUES (?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, category);
            stmt.setString(3, key);
            stmt.setInt(4, tier);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Collections] claim : " + e.getMessage());
        }
    }

    public Set<String> loadClaimed(UUID uuid) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT category, progress_key, tier FROM collection_claimed WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                set.add(rs.getString("category") + ":" + rs.getString("progress_key") + ":" + rs.getInt("tier"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Collections] loadClaimed : " + e.getMessage());
        }
        return set;
    }
}
