package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderChestCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PrivateChestService chestService;

    public EnderChestCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.chestService = new PrivateChestService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        boolean isStaff = player.hasPermission("cantale.admin");
        chestService.openChest(player, ChestType.EC, isStaff);
        player.sendMessage("§8[§6Cantale§8] §7Ender Chest ouvert.");
        return true;
    }
}
