package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.UUID;

public class PlayerDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public PlayerDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void createOrUpdatePlayer(UUID uuid, String username) {
        String sql = databaseManager.isSQLite()
                ? "INSERT INTO players (uuid, username, lives, balance, deaths, kills, kill_streak, last_death, tutorial_progress) " +
                  "VALUES (?, ?, ?, ?, ?, 0, 0, ?, 0) " +
                  "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username"
                : "INSERT INTO players (uuid, username, lives, balance, deaths, kills, kill_streak, last_death, tutorial_progress) " +
                  "VALUES (?, ?, ?, ?, ?, 0, 0, ?, 0) " +
                  "ON DUPLICATE KEY UPDATE username = VALUES(username)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int defaultLives = plugin.getConfigManager().getDefaultLives();
            double startBalance = plugin.getConfigManager().getStartBalance();

            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setInt(3, defaultLives);
            stmt.setDouble(4, startBalance);
            stmt.setInt(5, 0);
            stmt.setLong(6, 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createOrUpdatePlayer : " + e.getMessage());
        }
    }

    public int getLives(UUID uuid) {
        String sql = "SELECT lives FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("lives");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLives : " + e.getMessage());
        }
        return plugin.getConfigManager().getDefaultLives();
    }

    public void setLives(UUID uuid, int lives) {
        String sql = "UPDATE players SET lives = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.max(lives, plugin.getConfigManager().getMinLives()));
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLives : " + e.getMessage());
        }
    }

    public void decrementLives(UUID uuid) {
        int current = getLives(uuid);
        setLives(uuid, current - 1);
    }

    public void incrementLives(UUID uuid) {
        int current = getLives(uuid);
        setLives(uuid, current + 1);
    }

    public double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getBalance : " + e.getMessage());
        }
        return plugin.getConfigManager().getStartBalance();
    }

    public void setBalance(UUID uuid, double balance) {
        String sql = "UPDATE players SET balance = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, balance);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setBalance : " + e.getMessage());
        }
    }

    public void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public void removeBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) - amount);
    }

    public int getDeaths(UUID uuid) {
        String sql = "SELECT deaths FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("deaths");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getDeaths : " + e.getMessage());
        }
        return 0;
    }

    public void incrementDeaths(UUID uuid) {
        String sql = "UPDATE players SET deaths = deaths + 1, last_death = ?, kill_streak = 0 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur incrementDeaths : " + e.getMessage());
        }
    }

    public long getLastDeath(UUID uuid) {
        String sql = "SELECT last_death FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_death");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastDeath : " + e.getMessage());
        }
        return 0;
    }

    public int getKills(UUID uuid) {
        String sql = "SELECT kills FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("kills");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getKills : " + e.getMessage());
        }
        return 0;
    }

    public void incrementKills(UUID uuid) {
        String sql = "UPDATE players SET kills = kills + 1, kill_streak = kill_streak + 1 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur incrementKills : " + e.getMessage());
        }
    }

    public int getKillStreak(UUID uuid) {
        String sql = "SELECT kill_streak FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("kill_streak");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getKillStreak : " + e.getMessage());
        }
        return 0;
    }

    public void resetKillStreak(UUID uuid) {
        String sql = "UPDATE players SET kill_streak = 0 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur resetKillStreak : " + e.getMessage());
        }
    }

    public boolean playerExists(UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur playerExists : " + e.getMessage());
        }
        return false;
    }

    public int getTutorialProgress(UUID uuid) {
        String sql = "SELECT tutorial_progress FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("tutorial_progress");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getTutorialProgress : " + e.getMessage());
        }
        return 0;
    }

    public void setTutorialProgress(UUID uuid, int progress) {
        String sql = "UPDATE players SET tutorial_progress = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, progress);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setTutorialProgress : " + e.getMessage());
        }
    }

    public long getCreatedAt(UUID uuid) {
        String sql = "SELECT created_at FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("created_at");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getCreatedAt : " + e.getMessage());
        }
        return 0;
    }

    /**
     * Récupère la liste des joueurs morts définitivement (0 vies) triés par date de mort
     */
    public java.util.List<DeadPlayer> getPermanentlyDeadPlayers() {
        java.util.List<DeadPlayer> deadPlayers = new java.util.ArrayList<>();
        String sql = "SELECT uuid, username, deaths, last_death FROM players WHERE lives <= 0 AND last_death > 0 ORDER BY last_death DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                DeadPlayer player = new DeadPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("username"),
                    rs.getInt("deaths"),
                    rs.getLong("last_death")
                );
                deadPlayers.add(player);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPermanentlyDeadPlayers : " + e.getMessage());
        }
        return deadPlayers;
    }

    public void updatePlaytime(UUID uuid, long playtimeSeconds) {
        String sql = "UPDATE players SET playtime = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, (int) playtimeSeconds);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updatePlaytime : " + e.getMessage());
        }
    }

    /**
     * Classe interne représentant un joueur mort définitivement
     */
    public static class DeadPlayer {
        private final UUID uuid;
        private final String username;
        private final int deaths;
        private final long lastDeath;

        public DeadPlayer(UUID uuid, String username, int deaths, long lastDeath) {
            this.uuid = uuid;
            this.username = username;
            this.deaths = deaths;
            this.lastDeath = lastDeath;
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public int getDeaths() { return deaths; }
        public long getLastDeath() { return lastDeath; }
    }
}
