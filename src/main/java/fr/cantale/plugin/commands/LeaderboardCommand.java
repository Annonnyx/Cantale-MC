package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.leaderboard.LeaderboardManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final LeaderboardManager leaderboardManager;

    public LeaderboardCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.leaderboardManager = new LeaderboardManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new fr.cantale.plugin.ui.inventory.LeaderboardUI(plugin, player).open();
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String type = args[0].toLowerCase();
        int limit = 10;
        
        if (args.length >= 2) {
            try {
                limit = Math.min(Math.max(Integer.parseInt(args[1]), 1), 50);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cLe nombre doit être entre 1 et 50."));
                return true;
            }
        }

        switch (type) {
            case "kills":
            case "kill":
                showTopKills(sender, limit);
                break;
            case "deaths":
            case "death":
                showTopDeaths(sender, limit);
                break;
            case "richest":
            case "money":
            case "balance":
                showTopRichest(sender, limit);
                break;
            case "factions":
            case "faction":
                showTopFactions(sender, limit);
                break;
            case "chat_reactions":
            case "reactions":
            case "reaction":
            case "chat":
                showTopChatReactions(sender, limit);
                break;
            default:
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cType inconnu : &e" + type));
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void showTopKills(CommandSender sender, int limit) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Top " + limit + " Kills ==="));
        
        for (LeaderboardManager.LeaderboardEntry entry : leaderboardManager.getTopKills(limit)) {
            String medal = getMedal(entry.getRank());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                medal + " &e#" + entry.getRank() + " &7- &f" + entry.getName() + " &7: &a" + entry.getValue() + " kills"
            ));
        }
    }

    private void showTopDeaths(CommandSender sender, int limit) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Top " + limit + " Morts ==="));
        
        for (LeaderboardManager.LeaderboardEntry entry : leaderboardManager.getTopDeaths(limit)) {
            String medal = getMedal(entry.getRank());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                medal + " &e#" + entry.getRank() + " &7- &f" + entry.getName() + " &7: &c" + entry.getValue() + " morts"
            ));
        }
    }

    private void showTopRichest(CommandSender sender, int limit) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Top " + limit + " Plus Riches ==="));
        
        for (LeaderboardManager.LeaderboardEntry entry : leaderboardManager.getTopRichest(limit)) {
            String medal = getMedal(entry.getRank());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                medal + " &e#" + entry.getRank() + " &7- &f" + entry.getName() + " &7: &6" + entry.getValue() + " Cantox"
            ));
        }
    }

    private void showTopFactions(CommandSender sender, int limit) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Top " + limit + " Factions ==="));
        
        for (LeaderboardManager.FactionLeaderboardEntry entry : leaderboardManager.getTopFactions(limit)) {
            String medal = getMedal(entry.getRank());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                medal + " &e#" + entry.getRank() + " &7- &f" + entry.getName() + " &7: &6" + entry.getBalance() + " Cantox &7(Power: &b" + entry.getPower() + "&7)"
            ));
        }
    }

    private void showTopChatReactions(CommandSender sender, int limit) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Top " + limit + " Réactions Chat ==="));

        for (LeaderboardManager.LeaderboardEntry entry : leaderboardManager.getTopChatReactions(limit)) {
            String medal = getMedal(entry.getRank());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                medal + " &e#" + entry.getRank() + " &7- &f" + entry.getName() + " &7: &a" + entry.getValue() + " victoires"
            ));
        }
    }

    private String getMedal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "";
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[§6Cantale§8] §7=== Leaderboard ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Usage : /leaderboard <type> [limite]"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Types disponibles :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7  - &ekills &7: Top kills"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7  - &edeaths &7: Top morts"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7  - &erichest &7: Plus riches"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7  - &efactions &7: Top factions"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7  - &echat_reactions &7: Victoires réactions chat"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Limite : 1-50 (défaut: 10)"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("kills", "deaths", "richest", "factions", "chat_reactions");
        }
        if (args.length == 2) {
            return Arrays.asList("5", "10", "15", "20", "25", "50");
        }
        return Collections.emptyList();
    }
}
