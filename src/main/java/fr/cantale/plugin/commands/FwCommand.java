package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionWarp;
import fr.cantale.plugin.factions.FactionWarpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Commande top-level {@code /fw} — warps de faction publics (table {@code faction_warps}).
 */
public class FwCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public FwCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    private FactionWarpManager manager() {
        return plugin.getFactionWarpManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            manager().listAll(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "set" -> {
                if (args.length < 2) {
                    player.sendMessage(FactionWarpManager.PREFIX + "§cUsage : /fw create <nom>");
                    return true;
                }
                manager().create(player, args[1]);
            }
            case "delete", "del", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(FactionWarpManager.PREFIX + "§cUsage : /fw delete <nom>");
                    return true;
                }
                manager().delete(player, args[1]);
            }
            case "list", "ls" -> manager().listAll(player);
            case "help" -> sendUsage(player);
            case "tp" -> {
                if (args.length < 2) {
                    player.sendMessage(FactionWarpManager.PREFIX + "§cUsage : /fw tp <nom>");
                    return true;
                }
                manager().teleport(player, args[1]);
            }
            default -> manager().teleport(player, args[0]);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(FactionWarpManager.PREFIX + "§6=== Warp de faction public ===");
        player.sendMessage("§e/fw §7— liste tous les FW du serveur");
        player.sendMessage("§e/fw <nom> §7— s'y téléporter (tout le monde, warmup comme /warp)");
        player.sendMessage("§e/fw create <nom> §7— créer (officiers+, dans un claim)");
        player.sendMessage("§e/fw delete <nom> §7— supprimer (officiers+)");
        player.sendMessage("§7Quota : 1 warp jusqu'à 60 de pouvoir, puis +1 tous les 30.");
        player.sendMessage("§7Ce n'est pas le spawn de fac (§e/f setspawn§7 / §e/f spawn§7).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.addAll(Arrays.asList("create", "delete", "list", "help"));
            for (FactionWarp warp : manager().getAll()) {
                options.add(warp.getName());
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && sender instanceof Player player) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
                Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
                if (faction == null) return Collections.emptyList();
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return manager().getByFaction(faction.getId()).stream()
                        .map(FactionWarp::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
            if (sub.equals("tp")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return manager().getAll().stream()
                        .map(FactionWarp::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
