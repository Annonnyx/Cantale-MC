package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateManager;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TYPES = List.of("vote", "rare", "epic", "mythic", "legendary");

    private final CantalePlugin plugin;
    private final CrateManager crateManager;

    public CrateCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cTu n'as pas la permission.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            crateManager.reloadConfig();
            sender.sendMessage("§8[§6Cantale§8] §aConfiguration des caisses (§ecrate.yml§a) rechargée.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sendUsage(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
            return true;
        }

        CrateType type;
        try {
            type = CrateType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§8[§6Cantale§8] §cType invalide. Utilise: vote, rare, epic, mythic, legendary.");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
                return true;
            }
        }

        var crateItem = crateManager.createCrateItem(type);
        crateItem.setAmount(amount);
        var leftover = target.getInventory().addItem(crateItem);
        if (!leftover.isEmpty()) {
            for (var drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
        }

        sender.sendMessage("§8[§6Cantale§8] §a" + amount + "x " + type.getDisplayName() + " donné(s) à §e" + target.getName() + "§a.");
        target.sendMessage("§8[§6Cantale§8] §aTu as reçu §e" + amount + "x " + type.getDisplayName() + "§a.");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8[§6Cantale§8] §cUsage:");
        sender.sendMessage("§7  /crate give <joueur> <vote|rare|epic|mythic|legendary> [quantité]");
        sender.sendMessage("§7  /crate reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("give", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(TYPES, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("1", "5", "10", "64"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(opt);
            }
        }
        return out;
    }
}
