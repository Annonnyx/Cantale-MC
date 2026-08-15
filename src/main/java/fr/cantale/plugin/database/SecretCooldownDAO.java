package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    /**
     * Persiste le cooldown {@code /f secret}.
     * @return {@code true} si l'écriture a réussi (MariaDB {@code ON DUPLICATE KEY} / SQLite {@code INSERT OR REPLACE})
     */
    public boolean setLastUsed(UUID playerUuid, long timestamp) {
        String sql = databaseManager.upsertSql(
                "secret_cooldowns (player_uuid, last_used)",
                "?, ?",
                "last_used = VALUES(last_used)");
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, timestamp);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLastUsed : " + e.getMessage());
            return false;
        }
    }

    public boolean canUseSecret(UUID playerUuid, long cooldownMillis) {
        long lastUsed = getLastUsed(playerUuid);
        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastUsed) >= cooldownMillis / 1000;
    }
}
