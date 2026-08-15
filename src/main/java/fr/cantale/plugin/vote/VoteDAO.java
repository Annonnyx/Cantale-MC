package fr.cantale.plugin.vote;

import fr.cantale.plugin.util.PublicRankingExclusions;
import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * DAO du système de votes (Votifier).
 * Gère la persistance des votes individuels, des stats agrégées par joueur,
 * et la file des votes en attente (pour livraison à la prochaine connexion).
 */
public class VoteDAO {

    private final CantalePlugin plugin;

    public VoteDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /** Enregistre un vote (delivered=0 si offline). Retourne l'ID inséré ou -1. */
    public long recordVote(UUID playerUuid, String playerName, String site, String address,
                           String tier, String rewardSummary, boolean delivered) {
        return recordVote(playerUuid, playerName, site, address, tier, rewardSummary, delivered,
                System.currentTimeMillis() / 1000L);
    }

    public long recordVote(UUID playerUuid, String playerName, String site, String address,
                           String tier, String rewardSummary, boolean delivered, long votedAtSeconds) {
        String sql = "INSERT INTO votes (player_uuid, player_name, site, address, tier, reward_summary, delivered, delivered_at, voted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, site);
            stmt.setString(4, address);
            stmt.setString(5, tier);
            stmt.setString(6, rewardSummary);
            stmt.setInt(7, delivered ? 1 : 0);
            long now = System.currentTimeMillis() / 1000L;
            if (delivered) stmt.setLong(8, now);
            else stmt.setNull(8, Types.INTEGER);
            stmt.setLong(9, votedAtSeconds > 0 ? votedAtSeconds : now);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.recordVote : " + e.getMessage());
        }
        return -1L;
    }

    /** Récupère tous les votes non délivrés d'un joueur (typiquement à la connexion). */
    public List<PendingVote> getPendingVotes(UUID playerUuid) {
        List<PendingVote> result = new ArrayList<>();
        String sql = "SELECT id, site, tier, reward_summary, voted_at FROM votes WHERE player_uuid = ? AND delivered = 0 ORDER BY voted_at ASC";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new PendingVote(
                        rs.getLong("id"),
                        rs.getString("site"),
                        rs.getString("tier"),
                        rs.getString("reward_summary"),
                        rs.getLong("voted_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.getPendingVotes : " + e.getMessage());
        }
        return result;
    }

    public void markDelivered(long voteId) {
        String sql = "UPDATE votes SET delivered = 1, delivered_at = ? WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000L);
            stmt.setLong(2, voteId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.markDelivered : " + e.getMessage());
        }
    }

    public int countPending(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM votes WHERE player_uuid = ? AND delivered = 0";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.countPending : " + e.getMessage());
        }
        return 0;
    }

    /**
     * Met à jour les stats du joueur après un vote.
     * Calcule la streak basée sur le jour du dernier vote (UTC days since epoch).
     * @return les stats à jour
     */
    public VoteStats applyVote(UUID playerUuid, String playerName) {
        long now = System.currentTimeMillis() / 1000L;
        long currentDay = now / 86_400L;

        VoteStats current = getStats(playerUuid);
        int newStreak;
        if (current == null) {
            newStreak = 1;
        } else if (current.lastVoteDay == currentDay) {
            // Déjà voté aujourd'hui : streak inchangée
            newStreak = current.streakDays;
        } else if (current.lastVoteDay == currentDay - 1) {
            // Vote consécutif
            newStreak = current.streakDays + 1;
        } else {
            // Streak cassée
            newStreak = 1;
        }

        int newTotal = (current == null ? 0 : current.totalVotes) + 1;

        // Upsert compatible MariaDB (ON DUPLICATE KEY UPDATE) et SQLite (INSERT OR REPLACE)
        String sql;
        boolean isMariaDB = isMariaDB();
        if (isMariaDB) {
            sql = "INSERT INTO vote_stats (player_uuid, player_name, total_votes, streak_days, last_vote_day, last_vote_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), total_votes=VALUES(total_votes), " +
                    "streak_days=VALUES(streak_days), last_vote_day=VALUES(last_vote_day), last_vote_at=VALUES(last_vote_at)";
        } else {
            sql = "INSERT OR REPLACE INTO vote_stats (player_uuid, player_name, total_votes, streak_days, last_vote_day, last_vote_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setInt(3, newTotal);
            stmt.setInt(4, newStreak);
            stmt.setLong(5, currentDay);
            stmt.setLong(6, now);
            stmt.executeUpdate();
            return new VoteStats(playerUuid, playerName, newTotal, newStreak, currentDay, now);
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.applyVote : " + e.getMessage());
            return null;
        }
    }

    public VoteStats getStats(UUID playerUuid) {
        String sql = "SELECT * FROM vote_stats WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new VoteStats(
                        playerUuid,
                        rs.getString("player_name"),
                        rs.getInt("total_votes"),
                        rs.getInt("streak_days"),
                        rs.getLong("last_vote_day"),
                        rs.getLong("last_vote_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.getStats : " + e.getMessage());
        }
        return null;
    }

    public List<VoteStats> getTopVoters(int limit) {
        List<VoteStats> result = new ArrayList<>();
        String sql = "SELECT * FROM vote_stats"
                + PublicRankingExclusions.whereExclude("player_name", "player_uuid")
                + " ORDER BY total_votes DESC LIMIT ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new VoteStats(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getInt("total_votes"),
                        rs.getInt("streak_days"),
                        rs.getLong("last_vote_day"),
                        rs.getLong("last_vote_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.getTopVoters : " + e.getMessage());
        }
        return result;
    }

    public int getTotalVotesCount() {
        String sql = "SELECT COUNT(*) FROM votes";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.getTotalVotesCount : " + e.getMessage());
        }
        return 0;
    }

    /**
     * Vérifie si le joueur a déjà été récompensé sur ce site dans la fenêtre de cooldown.
     * Empêche un double grant Votifier + API Serveur-Prive.
     */
    public boolean hasRecentVoteOnSite(UUID playerUuid, String site, int cooldownHours) {
        if (playerUuid == null || site == null || site.isBlank() || cooldownHours <= 0) return false;
        long since = System.currentTimeMillis() / 1000L - (cooldownHours * 3600L);
        String sql = "SELECT COUNT(*) FROM votes WHERE player_uuid = ? AND site = ? AND voted_at > ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, site);
            stmt.setLong(3, since);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.hasRecentVoteOnSite : " + e.getMessage());
        }
        return false;
    }

    /**
     * Marque un identifiant de vote externe comme traité.
     * @return true si c'est un nouveau vote (à récompenser), false s'il était déjà vu
     */
    public boolean tryMarkExternalVoteProcessed(String site, String externalId, String playerName) {
        if (site == null || site.isBlank() || externalId == null || externalId.isBlank()) return false;
        String sql = isMariaDB()
                ? "INSERT IGNORE INTO vote_external_ids (site, external_id, player_name, claimed_at) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO vote_external_ids (site, external_id, player_name, claimed_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, site);
            stmt.setString(2, externalId);
            stmt.setString(3, playerName);
            stmt.setLong(4, System.currentTimeMillis() / 1000L);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.tryMarkExternalVoteProcessed : " + e.getMessage());
            return false;
        }
    }

    /**
     * Met à jour le compteur mensuel d'un joueur.
     */
    public void updateMonthlyVotes(UUID playerUuid, String playerName, int currentMonth, int monthlyVotes) {
        String sql;
        boolean isMariaDB = isMariaDB();
        if (isMariaDB) {
            sql = "INSERT INTO vote_stats " +
                    "(player_uuid, player_name, total_votes, streak_days, last_vote_day, last_vote_at, monthly_votes, last_month) " +
                    "VALUES (?, ?, COALESCE((SELECT total_votes FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT streak_days FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT last_vote_day FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT last_vote_at FROM vote_stats WHERE player_uuid = ?), 0), ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), total_votes=VALUES(total_votes), " +
                    "streak_days=VALUES(streak_days), last_vote_day=VALUES(last_vote_day), last_vote_at=VALUES(last_vote_at), " +
                    "monthly_votes=VALUES(monthly_votes), last_month=VALUES(last_month)";
        } else {
            sql = "INSERT OR REPLACE INTO vote_stats " +
                    "(player_uuid, player_name, total_votes, streak_days, last_vote_day, last_vote_at, monthly_votes, last_month) " +
                    "VALUES (?, ?, COALESCE((SELECT total_votes FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT streak_days FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT last_vote_day FROM vote_stats WHERE player_uuid = ?), 0), " +
                    "COALESCE((SELECT last_vote_at FROM vote_stats WHERE player_uuid = ?), 0), ?, ?)";
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, playerUuid.toString());
            stmt.setString(4, playerUuid.toString());
            stmt.setString(5, playerUuid.toString());
            stmt.setString(6, playerUuid.toString());
            stmt.setInt(7, monthlyVotes);
            stmt.setInt(8, currentMonth);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.updateMonthlyVotes : " + e.getMessage());
        }
    }

    /**
     * Récupère le top mensuel pour un mois donné (format YYYYMM).
     */
    public List<VoteStats> getMonthlyTop(int month, int limit) {
        List<VoteStats> result = new ArrayList<>();
        String sql = "SELECT * FROM vote_stats WHERE last_month = ?"
                + PublicRankingExclusions.andExclude("player_name", "player_uuid")
                + " ORDER BY monthly_votes DESC LIMIT ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, month);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new VoteStats(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getInt("total_votes"),
                        rs.getInt("streak_days"),
                        rs.getLong("last_vote_day"),
                        rs.getLong("last_vote_at"),
                        rs.getInt("monthly_votes"),
                        rs.getInt("last_month")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.getMonthlyTop : " + e.getMessage());
        }
        return result;
    }

    /**
     * Détecte si la base de données utilise le dialecte MariaDB/MySQL.
     */
    private boolean isMariaDB() {
        return plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isMariaDB();
    }

    /** Vote livré au joueur quand il revient en ligne. */
    public record PendingVote(long id, String site, String tier, String rewardSummary, long votedAt) {}

    /** Stats agrégées par joueur. */
    public record VoteStats(UUID uuid, String name, int totalVotes, int streakDays,
                            long lastVoteDay, long lastVoteAt, int monthlyVotes, int lastMonth) {

        public VoteStats(UUID uuid, String name, int totalVotes, int streakDays,
                         long lastVoteDay, long lastVoteAt) {
            this(uuid, name, totalVotes, streakDays, lastVoteDay, lastVoteAt, 0, 0);
        }
    }
}
