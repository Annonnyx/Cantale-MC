package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.Warp;
import fr.cantale.plugin.teleport.WarpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager warpManager;

    public WarpCommand(CantalePlugin plugin) {
        this.warpManager = plugin.getWarpManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            warpManager.listWarps(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            warpManager.listWarps(player);
            return true;
        }

        // Sous-commandes admin
        if (args[0].equalsIgnoreCase("set") && player.hasPermission("cantale.admin")) {
            if (args.length < 2) {
                player.sendMessage("§8[§6Cantale§8] §cUsage : /warp set <nom> [event]");
                return true;
            }
            boolean isEvent = args.length >= 3 && args[2].equalsIgnoreCase("event");
            if (warpManager.createWarp(args[1], player.getLocation(), isEvent)) {
                player.sendMessage("§8[§6Cantale§8] §aWarp §e" + args[1] + " §acréé !");
            } else {
                player.sendMessage("§8[§6Cantale§8] §cCe warp existe déjà.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("delete") && player.hasPermission("cantale.admin")) {
            if (args.length < 2) {
                player.sendMessage("§8[§6Cantale§8] §cUsage : /warp delete <nom>");
                return true;
            }
            if (warpManager.deleteWarp(args[1])) {
                player.sendMessage("§8[§6Cantale§8] §aWarp §e" + args[1] + " §asupprimé !");
            } else {
                player.sendMessage("§8[§6Cantale§8] §cWarp inexistant.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle") && player.hasPermission("cantale.admin")) {
            if (args.length < 2) {
                player.sendMessage("§8[§6Cantale§8] §cUsage : /warp toggle <nom>");
                return true;
            }
            warpManager.setWarpActive(args[1], !warpManager.getWarps().stream()
                    .filter(w -> w.getName().equals(args[1]))
                    .findFirst()
                    .map(Warp::isActive)
                    .orElse(false));
            player.sendMessage("§8[§6Cantale§8] §aWarp §e" + args[1] + " §atogglé !");
            return true;
        }

        warpManager.teleportWarp(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = warpManager.getWarps().stream()
                    .map(w -> w.getName().toLowerCase())
                    .collect(Collectors.toList());
            if (sender.hasPermission("cantale.admin")) {
                options.addAll(Arrays.asList("set", "delete", "toggle", "list"));
            }
            return options;
        }
        return Collections.emptyList();
    }
}
