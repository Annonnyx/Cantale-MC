package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.vote.VoteDAO;
import fr.cantale.plugin.vote.VoteListener;
import fr.cantale.plugin.vote.VoteReward;
import fr.cantale.plugin.vote.VoteRewardManager;
import fr.cantale.plugin.vote.VoteSite;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /vote                  → liste des sites de vote (avec liens cliquables)
 * /vote claim            → délivre les récompenses en attente
 * /vote stats [pseudo]   → statistiques du joueur (total, streak)
 * /vote top [n]          → leaderboard des votants (top N, défaut 10)
 * /vote test <pseudo> <site> 🔒admin → simule un vote (debug)
 */
public class VoteCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final VoteDAO voteDAO;
    private final VoteRewardManager rewardManager;
    private final VoteListener voteListener;

    public VoteCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.voteDAO = plugin.getVoteDAO();
        this.rewardManager = plugin.getVoteRewardManager();
        this.voteListener = plugin.getVoteListener();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showSites(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "claim" -> handleClaim(sender);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "test" -> handleTest(sender, args);
            case "help" -> sendHelp(sender);
            default -> showSites(sender);
        }
        return true;
    }

    private void showSites(CommandSender sender) {
        sender.sendMessage(format("&8&m─────────&r &8[&6Vote Cantale&8] &8&m─────────"));
        sender.sendMessage(format("&7Votez pour Cantale et obtenez des récompenses !"));
        sender.sendMessage("");
        List<VoteSite> sites = VoteListener.loadSites();
        if (sites.isEmpty()) {
            sender.sendMessage(format("&cAucun site de vote configuré."));
        }
        for (VoteSite site : sites) {
            sender.sendMessage(format("&7▶ &e" + site.displayName() + " &8→ &b&n" + site.url()
                    + " &8(&7" + site.crateCount() + " Cadeau(x) du Roi&8)"));
        }
        sender.sendMessage("");
        sender.sendMessage(format("&7Récompenses : &7Commun &8| &9Rare &8| &5Épique &8| &6Légendaire"));
        sender.sendMessage(format("&7Tape &e/vote claim &7pour récupérer tes récompenses en attente."));
        sender.sendMessage(format("&7Tape &e/vote top &7pour le classement."));
        if (sender instanceof Player p) {
            int pending = voteDAO.countPending(p.getUniqueId());
            if (pending > 0) {
                sender.sendMessage(format("&a★ Tu as &e" + pending + " &arécompense(s) en attente — utilise &e/vote claim&a !"));
            }
        }
        sender.sendMessage(format("&8&m──────────────────────────────"));
    }

    private void handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(format("&cCette commande est réservée aux joueurs."));
            return;
        }
        UUID uuid = player.getUniqueId();
        List<VoteDAO.PendingVote> pending = voteDAO.getPendingVotes(uuid);
        if (pending.isEmpty()) {
            player.sendMessage(format("&7Aucune récompense en attente."));
            return;
        }
        for (VoteDAO.PendingVote pv : pending) {
            VoteReward.Tier tier;
            try { tier = VoteReward.Tier.valueOf(pv.tier()); }
            catch (Exception e) { tier = VoteReward.Tier.COMMUN; }
            VoteSite site = resolveSite(pv.site());
            VoteReward reward = rewardManager.roll(switch (tier) {
                case LEGENDAIRE -> 999;
                case EPIQUE -> 30;
                case RARE -> 7;
                case COMMUN -> 1;
            }, site);
            rewardManager.deliverTo(player, reward);
            voteDAO.markDelivered(pv.id());
            player.sendMessage(format("&aRécompense " + tier.getDisplayName() + " &a: &e" + reward.getSummary()
                    + " &7(via " + pv.site() + ")"));
        }
        player.sendMessage(format("&aTotal : &e" + pending.size() + " &arécompense(s) délivrée(s)."));
    }

    private void handleStats(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            target = offline.getUniqueId();
            name = offline.getName() != null ? offline.getName() : args[1];
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
            name = p.getName();
        } else {
            sender.sendMessage(format("&cUsage : /vote stats <pseudo>"));
            return;
        }
        VoteDAO.VoteStats stats = voteDAO.getStats(target);
        sender.sendMessage(format("&6=== Stats vote — " + name + " ==="));
        if (stats == null) {
            sender.sendMessage(format("&7Aucun vote enregistré."));
            return;
        }
        sender.sendMessage(format("&7Total des votes : &e" + stats.totalVotes()));
        sender.sendMessage(format("&7Votes ce mois : &e" + stats.monthlyVotes()));
        sender.sendMessage(format("&7Streak actuelle : &6" + stats.streakDays() + " jour(s)"));
        sender.sendMessage(format("&7Bonus actuel : " + bonusLabel(stats.streakDays())));
    }

    private VoteSite resolveSite(String siteName) {
        for (VoteSite site : VoteListener.loadSites()) {
            if (siteName != null && siteName.equalsIgnoreCase(site.displayName())) return site;
        }
        return null;
    }

    private String bonusLabel(int streak) {
        if (streak >= 30) return "&5Épique min. garanti";
        if (streak >= 7) return "&9Rare min. garanti";
        int needed = streak >= 7 ? 30 - streak : 7 - streak;
        return "&7Aucun &8(prochain palier dans " + needed + "j)";
    }

    private void handleTop(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length >= 2) {
            try { limit = Math.max(1, Math.min(50, Integer.parseInt(args[1]))); } catch (NumberFormatException ignored) {}
        }
        List<VoteDAO.VoteStats> top = voteDAO.getTopVoters(limit);
        sender.sendMessage(format("&6=== Top " + limit + " votants ==="));
        if (top.isEmpty()) {
            sender.sendMessage(format("&7Aucun votant pour le moment."));
            return;
        }
        int rank = 1;
        for (VoteDAO.VoteStats s : top) {
            String medal = switch (rank) {
                case 1 -> "§6①";
                case 2 -> "§7②";
                case 3 -> "§c③";
                default -> "§8" + rank + ".";
            };
            sender.sendMessage(format(medal + " &e" + s.name() + " &8— &7" + s.totalVotes()
                    + " votes &8(streak " + s.streakDays() + "j)"));
            rank++;
        }
        int total = voteDAO.getTotalVotesCount();
        sender.sendMessage(format("&7Total tous votants : &e" + total + " votes"));
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage(format("&cVous n'avez pas la permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(format("&cUsage : /vote test <pseudo> <site>"));
            return;
        }
        voteListener.handleVote(args[1], args[2], "127.0.0.1");
        sender.sendMessage(format("&aVote simulé pour &e" + args[1] + " &avia &e" + args[2]));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(format("&6=== Aide /vote ==="));
        sender.sendMessage(format("&e/vote &7— Liste des sites de vote"));
        sender.sendMessage(format("&e/vote claim &7— Récupérer les récompenses en attente"));
        sender.sendMessage(format("&e/vote stats [pseudo] &7— Statistiques (votes, streak)"));
        sender.sendMessage(format("&e/vote top [n] &7— Leaderboard (top n, défaut 10)"));
        if (sender.hasPermission("cantale.admin")) {
            sender.sendMessage(format("&c[Admin] &e/vote test <pseudo> <site> &7— Simuler un vote"));
        }
    }

    private String format(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> base = new ArrayList<>(Arrays.asList("claim", "stats", "top", "help"));
            if (sender.hasPermission("cantale.admin")) base.add("test");
            return base.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("test"))) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
