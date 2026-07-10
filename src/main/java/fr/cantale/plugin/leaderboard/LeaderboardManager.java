package fr.cantale.plugin.leaderboard;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardManager {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public LeaderboardManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public List<LeaderboardEntry> getTopKills(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT username, kills FROM players ORDER BY kills DESC LIMIT ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            int rank = 1;
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                    rank++,
                    rs.getString("username"),
                    rs.getInt("kills")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération des top kills : " + e.getMessage());
        }
        
        return entries;
    }

    public List<LeaderboardEntry> getTopDeaths(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT username, deaths FROM players ORDER BY deaths DESC LIMIT ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            int rank = 1;
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                    rank++,
                    rs.getString("username"),
                    rs.getInt("deaths")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération des top morts : " + e.getMessage());
        }
        
        return entries;
    }

    public List<LeaderboardEntry> getTopRichest(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT username, balance FROM players ORDER BY balance DESC LIMIT ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            int rank = 1;
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                    rank++,
                    rs.getString("username"),
                    (int) rs.getDouble("balance")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération des plus riches : " + e.getMessage());
        }
        
        return entries;
    }

    public List<FactionLeaderboardEntry> getTopFactions(int limit) {
        List<FactionLeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT name, balance, power FROM factions ORDER BY balance DESC LIMIT ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            int rank = 1;
            while (rs.next()) {
                entries.add(new FactionLeaderboardEntry(
                    rank++,
                    rs.getString("name"),
                    (int) rs.getDouble("balance"),
                    rs.getInt("power")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la récupération des top factions : " + e.getMessage());
        }
        
        return entries;
    }

    public static class LeaderboardEntry {
        private final int rank;
        private final String name;
        private final int value;

        public LeaderboardEntry(int rank, String name, int value) {
            this.rank = rank;
            this.name = name;
            this.value = value;
        }

        public int getRank() {
            return rank;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    public static class FactionLeaderboardEntry {
        private final int rank;
        private final String name;
        private final int balance;
        private final int power;

        public FactionLeaderboardEntry(int rank, String name, int balance, int power) {
            this.rank = rank;
            this.name = name;
            this.balance = balance;
            this.power = power;
        }

        public int getRank() {
            return rank;
        }

        public String getName() {
            return name;
        }

        public int getBalance() {
            return balance;
        }

        public int getPower() {
            return power;
        }
    }
}
