package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Gère le classement mensuel des votes.
 * Se réinitialise automatiquement le premier jour de chaque mois et récompense le top N.
 */
public class VoteMonthlyManager {

    private static int lastKnownMonth = -1;

    public static void trackVote(CantalePlugin plugin, UUID playerUuid, String playerName) {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        int currentMonth = now.getYear() * 100 + now.getMonthValue();

        // Réinitialisation mensuelle si nécessaire
        if (lastKnownMonth != -1 && lastKnownMonth != currentMonth) {
            rewardTopAndReset(plugin, lastKnownMonth);
        }
        lastKnownMonth = currentMonth;

        VoteDAO.VoteStats stats = plugin.getVoteDAO().getStats(playerUuid);
        int monthlyVotes = (stats == null || stats.lastMonth() != currentMonth) ? 0 : stats.monthlyVotes();
        plugin.getVoteDAO().updateMonthlyVotes(playerUuid, playerName, currentMonth, monthlyVotes + 1);
    }

    /**
     * Force la réinitialisation du classement du mois précédent (appelée au boot et au changement de mois).
     */
    public static void onStartup(CantalePlugin plugin) {
        if (!plugin.getConfig().getBoolean("vote.monthly-leaderboard.enabled", true)) return;
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        int currentMonth = now.getYear() * 100 + now.getMonthValue();
        lastKnownMonth = currentMonth;
    }

    private static void rewardTopAndReset(CantalePlugin plugin, int month) {
        if (!plugin.getConfig().getBoolean("vote.monthly-leaderboard.enabled", true)) return;

        int topCount = plugin.getConfig().getInt("vote.monthly-leaderboard.top-reward-count", 3);
        List<VoteDAO.VoteStats> top = plugin.getVoteDAO().getMonthlyTop(month, topCount);

        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("vote.monthly-leaderboard.rewards");
        if (rewards == null) return;

        int rank = 1;
        for (VoteDAO.VoteStats entry : top) {
            String path = String.valueOf(rank);
            if (!rewards.contains(path)) continue;

            String playerName = entry.name();
            String message = plugin.getConfig().getString("vote.monthly-leaderboard.rewards." + path + ".message",
                    "&e%player% termine " + rank + "ème du classement mensuel des votes !");
            String command = plugin.getConfig().getString("vote.monthly-leaderboard.rewards." + path + ".reward-command");

            if (command != null && !command.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName));
            }

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    message.replace("%player%", playerName).replace("%rank%", String.valueOf(rank))));

            rank++;
        }
    }
}
