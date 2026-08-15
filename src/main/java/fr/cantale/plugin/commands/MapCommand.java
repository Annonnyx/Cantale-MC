package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.ui.inventory.WorldMapUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /carte — ouvre la carte du monde interactive (terrain, claims, warps, homes, TP).
 * /carte mini — donne la minimap live à tenir en main.
 */
public class MapCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public MapCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }
        // Défaut false : la carte in-game est dormante, le mod client fournit la carte.
        if (!plugin.getConfig().getBoolean("map.enabled", false)) {
            player.sendMessage("§8[§6Carte§8] §cLa carte est désactivée.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("mini")) {
            plugin.getMapUI().giveMinimap(player);
            return true;
        }
        plugin.getUIManager().getNavigationManager().openUI(player, new WorldMapUI(plugin, player));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "mini".startsWith(args[0].toLowerCase())) {
            return List.of("mini");
        }
        return List.of();
    }
}
