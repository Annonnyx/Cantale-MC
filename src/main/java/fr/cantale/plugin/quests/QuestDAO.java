package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestDAO {

    private final CantalePlugin plugin;

    public QuestDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void savePool(String poolKey, String payload) {
        String sql = plugin.getDatabaseManager().upsertSql(
                "quest_pools (pool_key, payload)", "?, ?", "payload = VALUES(payload)");
        if (plugin.getDatabaseManager().isSQLite()) {
            sql = "INSERT OR REPLACE INTO quest_pools (pool_key, payload) VALUES (?, ?)";
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, poolKey);
            stmt.setString(2, payload);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] savePool : " + e.getMessage());
        }
    }

    public String loadPool(String poolKey) {
        String sql = "SELECT payload FROM quest_pools WHERE pool_key = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, poolKey);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("payload");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] loadPool : " + e.getMessage());
        }
        return null;
    }

    public void saveProgress(String ownerId, String instanceId, int amount, boolean accepted, boolean completed) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO quest_progress (owner_id, instance_id, amount, accepted, completed) VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO quest_progress (owner_id, instance_id, amount, accepted, completed) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount), accepted = VALUES(accepted), completed = VALUES(completed)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerId);
            stmt.setString(2, instanceId);
            stmt.setInt(3, amount);
            stmt.setInt(4, accepted ? 1 : 0);
            stmt.setInt(5, completed ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] saveProgress : " + e.getMessage());
        }
    }

    public List<ProgressRow> loadProgress(String ownerPrefix) {
        List<ProgressRow> rows = new ArrayList<>();
        String sql = "SELECT owner_id, instance_id, amount, accepted, completed FROM quest_progress WHERE owner_id LIKE ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerPrefix + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new ProgressRow(
                        rs.getString("owner_id"),
                        rs.getString("instance_id"),
                        rs.getInt("amount"),
                        rs.getInt("accepted") != 0,
                        rs.getInt("completed") != 0
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] loadProgress : " + e.getMessage());
        }
        return rows;
    }

    public void saveContribution(String instanceId, UUID uuid, int amount) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO quest_contributions (instance_id, player_uuid, amount) VALUES (?, ?, ?)"
                : "INSERT INTO quest_contributions (instance_id, player_uuid, amount) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instanceId);
            stmt.setString(2, uuid.toString());
            stmt.setInt(3, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] saveContribution : " + e.getMessage());
        }
    }

    public Map<UUID, Integer> loadContributions(String instanceId) {
        Map<UUID, Integer> map = new HashMap<>();
        String sql = "SELECT player_uuid, amount FROM quest_contributions WHERE instance_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instanceId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    map.put(UUID.fromString(rs.getString("player_uuid")), rs.getInt("amount"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] loadContributions : " + e.getMessage());
        }
        return map;
    }

    public void saveWinner(String instanceId, int factionId) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO quest_winners (instance_id, faction_id) VALUES (?, ?)"
                : "INSERT INTO quest_winners (instance_id, faction_id) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE faction_id = VALUES(faction_id)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instanceId);
            stmt.setInt(2, factionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] saveWinner : " + e.getMessage());
        }
    }

    public Map<String, Integer> loadWinners() {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT instance_id, faction_id FROM quest_winners";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("instance_id"), rs.getInt("faction_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Quêtes] loadWinners : " + e.getMessage());
        }
        return map;
    }

    public record ProgressRow(String ownerId, String instanceId, int amount, boolean accepted, boolean completed) {}
}
