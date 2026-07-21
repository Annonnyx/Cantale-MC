package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Gère les paliers de votes individuels (milestones) : 10, 50, 100 votes, etc.
 * Chaque palier ne peut être obtenu qu'une fois par joueur.
 */
public class VoteMilestoneManager {

    public static void checkAndReward(CantalePlugin plugin, UUID playerUuid, int totalVotes) {
        if (!plugin.getConfig().getBoolean("vote.milestones.enabled", true)) return;

        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("vote.milestones.tiers");
        if (tiers == null) return;

        Set<Integer> alreadyClaimed = loadClaimed(plugin, playerUuid);
        List<Integer> thresholds = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            try { thresholds.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
        }
        Collections.sort(thresholds);

        for (int threshold : thresholds) {
            if (totalVotes >= threshold && !alreadyClaimed.contains(threshold)) {
                String path = String.valueOf(threshold);
                String message = plugin.getConfig().getString("vote.milestones.tiers." + path + ".message", "&6%player% &ea atteint &6" + threshold + " votes &e!");
                String command = plugin.getConfig().getString("vote.milestones.tiers." + path + ".reward-command");

                String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
                if (playerName == null) playerName = playerUuid.toString();

                if (command != null && !command.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName));
                }

                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        message.replace("%player%", playerName).replace("%threshold%", String.valueOf(threshold))));

                markClaimed(plugin, playerUuid, threshold);
            }
        }
    }

    private static Set<Integer> loadClaimed(CantalePlugin plugin, UUID playerUuid) {
        Set<Integer> set = new HashSet<>();
        String sql = "SELECT milestone FROM vote_milestones WHERE player_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                set.add(rs.getInt("milestone"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("VoteMilestoneManager.loadClaimed : " + e.getMessage());
        }
        return set;
    }

    private static void markClaimed(CantalePlugin plugin, UUID playerUuid, int milestone) {
        String sql = "INSERT INTO vote_milestones (player_uuid, milestone) VALUES (?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, milestone);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("VoteMilestoneManager.markClaimed : " + e.getMessage());
        }
    }
}
