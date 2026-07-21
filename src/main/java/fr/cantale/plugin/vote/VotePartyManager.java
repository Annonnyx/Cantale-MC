package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

/**
 * Gère la Vote Party : objectif de votes journalier pour récompenser un joueur en ligne.
 */
public class VotePartyManager {

    private final CantalePlugin plugin;
    private final Random random = new Random();

    public VotePartyManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void onVote(Player trigger) {
        if (!plugin.getConfig().getBoolean("vote.vote-party.enabled", true)) return;

        long currentDay = System.currentTimeMillis() / 1000L / 86_400L;
        int goal = plugin.getConfig().getInt("vote.vote-party.goal", 50);

        VotePartyState state = loadState();
        if (state == null) state = new VotePartyState(currentDay, 0, goal);

        if (state.day != currentDay) {
            state = new VotePartyState(currentDay, 0, goal);
        }

        int newCount = state.count + 1;
        saveState(currentDay, newCount, goal);

        if (newCount >= goal && state.count < goal) {
            triggerParty(goal);
        }
    }

    private void triggerParty(int goal) {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;

        Player winner = online.get(random.nextInt(online.size()));
        String rewardCmd = plugin.getConfig().getString("vote.vote-party.reward-command", "give %player% cantale:crate_vote 1");
        String message = plugin.getConfig().getString("vote.vote-party.message",
                "&6&lVOTE PARTY ! &eLe serveur a atteint %goal% votes aujourd'hui ! Un Cadeau du Roi a été offert à %player% !");

        message = message.replace("%goal%", String.valueOf(goal)).replace("%player%", winner.getName());

        if (rewardCmd != null && !rewardCmd.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rewardCmd.replace("%player%", winner.getName()));
        }

        Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }

    private VotePartyState loadState() {
        String sql = "SELECT day, count, goal FROM vote_party WHERE id = 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new VotePartyState(rs.getLong("day"), rs.getInt("count"), rs.getInt("goal"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("VotePartyManager.loadState : " + e.getMessage());
        }
        return null;
    }

    private void saveState(long day, int count, int goal) {
        String sql = "UPDATE vote_party SET day = ?, count = ?, goal = ? WHERE id = 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, day);
            stmt.setInt(2, count);
            stmt.setInt(3, goal);
            if (stmt.executeUpdate() == 0) {
                insertState(day, count, goal);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("VotePartyManager.saveState : " + e.getMessage());
        }
    }

    private void insertState(long day, int count, int goal) {
        String sql = "INSERT INTO vote_party (id, day, count, goal) VALUES (1, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, day);
            stmt.setInt(2, count);
            stmt.setInt(3, goal);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("VotePartyManager.insertState : " + e.getMessage());
        }
    }

    private record VotePartyState(long day, int count, int goal) {}
}
