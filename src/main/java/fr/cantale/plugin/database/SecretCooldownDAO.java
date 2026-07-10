package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.UUID;

public class SecretCooldownDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public SecretCooldownDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public long getLastUsed(UUID playerUuid) {
        String sql = "SELECT last_used FROM secret_cooldowns WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_used");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastUsed : " + e.getMessage());
        }
        return 0;
    }

    public void setLastUsed(UUID playerUuid, long timestamp) {
        String sql = "INSERT OR REPLACE INTO secret_cooldowns (player_uuid, last_used) VALUES (?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLastUsed : " + e.getMessage());
        }
    }

    public boolean canUseSecret(UUID playerUuid, long cooldownMillis) {
        long lastUsed = getLastUsed(playerUuid);
        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastUsed) >= cooldownMillis / 1000;
    }
}
