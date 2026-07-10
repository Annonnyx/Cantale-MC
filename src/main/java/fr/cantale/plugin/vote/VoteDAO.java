package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        String sql = "INSERT INTO votes (player_uuid, player_name, site, address, tier, reward_summary, delivered, delivered_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, site);
            stmt.setString(4, address);
            stmt.setString(5, tier);
            stmt.setString(6, rewardSummary);
            stmt.setInt(7, delivered ? 1 : 0);
            if (delivered) stmt.setLong(8, System.currentTimeMillis() / 1000L);
            else stmt.setNull(8, Types.INTEGER);
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

        // Upsert (SQLite friendly)
        String sql = "INSERT OR REPLACE INTO vote_stats (player_uuid, player_name, total_votes, streak_days, last_vote_day, last_vote_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setInt(3, newTotal);
            stmt.setInt(4, newStreak);
            stmt.setLong(5, currentDay);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("VoteDAO.applyVote : " + e.getMessage());
        }

        return new VoteStats(playerUuid, playerName, newTotal, newStreak, currentDay, now);
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
        String sql = "SELECT * FROM vote_stats ORDER BY total_votes DESC LIMIT ?";
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

    /** Vote livré au joueur quand il revient en ligne. */
    public record PendingVote(long id, String site, String tier, String rewardSummary, long votedAt) {}

    /** Stats agrégées par joueur. */
    public record VoteStats(UUID uuid, String name, int totalVotes, int streakDays,
                            long lastVoteDay, long lastVoteAt) {}
}
