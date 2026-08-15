package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.quests.QuestPeriod;
import fr.cantale.plugin.quests.QuestScope;
import fr.cantale.plugin.ui.inventory.QuestFactionUI;
import fr.cantale.plugin.ui.inventory.QuestMainUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class QuetesCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public QuetesCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("quete") || (args.length > 0 && isAdminSub(args[0]))) {
            return admin(sender, args, label.equalsIgnoreCase("quete") ? args : shift(args));
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs uniquement.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("fac")) {
            plugin.getUIManager().getNavigationManager().openUI(player, new QuestFactionUI(plugin, player));
            return true;
        }
        plugin.getUIManager().getNavigationManager().openUI(player, new QuestMainUI(plugin, player));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args, String[] adminArgs) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cPermission refusée.");
            return true;
        }
        if (adminArgs.length == 0) {
            sender.sendMessage("§e/quete reroll <solo|fac> <daily|weekly|seasonal>");
            sender.sendMessage("§e/quete give <joueur> <daily|weekly|seasonal|rare|epic|mythic|legendary>");
            return true;
        }
        if (adminArgs[0].equalsIgnoreCase("reroll") && adminArgs.length >= 3) {
            QuestScope scope = adminArgs[1].equalsIgnoreCase("fac") ? QuestScope.FACTION : QuestScope.SOLO;
            QuestPeriod period = switch (adminArgs[2].toLowerCase(Locale.ROOT)) {
                case "weekly", "hebdo" -> QuestPeriod.WEEKLY;
                case "seasonal", "saison" -> QuestPeriod.SEASONAL;
                default -> QuestPeriod.DAILY;
            };
            plugin.getQuestEngine().adminReroll(scope, period);
            sender.sendMessage("§aPool " + scope + " " + period + " régénéré.");
            return true;
        }
        if (adminArgs[0].equalsIgnoreCase("give") && adminArgs.length >= 3) {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(adminArgs[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage("§cJoueur introuvable.");
                return true;
            }
            String kind = adminArgs[2].toLowerCase(Locale.ROOT);
            QuestPeriod period = switch (kind) {
                case "daily", "jour", "journaliere" -> QuestPeriod.DAILY;
                case "weekly", "hebdo" -> QuestPeriod.WEEKLY;
                case "seasonal", "saison" -> QuestPeriod.SEASONAL;
                default -> null;
            };
            if (period != null) {
                fr.cantale.plugin.quests.QuestRewards.givePeriodKey(plugin, target, period);
            } else {
                try {
                    fr.cantale.plugin.crate.CrateType type =
                            fr.cantale.plugin.crate.CrateType.valueOf(kind.toUpperCase(Locale.ROOT));
                    fr.cantale.plugin.quests.QuestRewards.giveCrate(plugin, target, type);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cType invalide. daily/weekly/seasonal ou rare/epic/mythic/legendary.");
                    return true;
                }
            }
            sender.sendMessage("§aRécompense donnée à " + target.getName() + ".");
            return true;
        }
        sender.sendMessage("§e/quete reroll <solo|fac> <daily|weekly|seasonal>");
        sender.sendMessage("§e/quete give <joueur> <daily|weekly|seasonal|rare|epic|mythic|legendary>");
        return true;
    }

    private static boolean isAdminSub(String a) {
        return a.equalsIgnoreCase("reroll") || a.equalsIgnoreCase("give");
    }

    private static String[] shift(String[] args) {
        if (args.length == 0) return args;
        String[] n = new String[args.length];
        System.arraycopy(args, 0, n, 0, args.length);
        return n;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            if (args.length == 1) return List.of("fac");
            return List.of();
        }
        if (args.length == 1) return Stream.of("fac", "reroll", "give").filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("reroll")) return List.of("solo", "fac");
        if (args.length == 3 && args[0].equalsIgnoreCase("reroll")) return List.of("daily", "weekly", "seasonal");
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("daily", "weekly", "seasonal", "rare", "epic", "mythic", "legendary");
        }
        return List.of();
    }
}
