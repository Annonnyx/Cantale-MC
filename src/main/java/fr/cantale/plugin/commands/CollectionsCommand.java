package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.ui.inventory.CollectionMainUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CollectionsCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public CollectionsCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs uniquement.");
            return true;
        }
        plugin.getCollectionManager().syncDerived(player.getUniqueId(), () ->
                plugin.getUIManager().getNavigationManager().openUI(player, new CollectionMainUI(plugin, player)));
        return true;
    }
}
