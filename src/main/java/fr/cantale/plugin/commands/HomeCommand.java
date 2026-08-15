package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;

    public HomeCommand(CantalePlugin plugin) {
        this.homeManager = plugin.getHomeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            homeManager.listHomes(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 2) {
                    player.sendMessage("§8[§6Cantale§8] §cUsage : /home set <nom>");
                    return true;
                }
                homeManager.setHome(player, args[1]);
                break;
            case "delete":
            case "del":
                if (args.length < 2) {
                    player.sendMessage("§8[§6Cantale§8] §cUsage : /home delete <nom>");
                    return true;
                }
                homeManager.deleteHome(player, args[1]);
                break;
            default:
                homeManager.teleportHome(player, args[0]);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "delete", "del");
        }
        return Collections.emptyList();
    }
}
