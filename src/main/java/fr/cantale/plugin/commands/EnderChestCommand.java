package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderChestCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public EnderChestCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        // /ec opens the player's actual ender chest (27 slots, everyone has access)
        player.openInventory(player.getEnderChest());
        player.sendMessage("§8[§6Cantale§8] §7Ender Chest ouvert §7(§e27 slots§7)");
        return true;
    }
}
