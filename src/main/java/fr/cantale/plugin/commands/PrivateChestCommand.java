package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PrivateChestCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PrivateChestService chestService;

    public PrivateChestCommand(CantalePlugin plugin) {
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
        chestService.openChest(player, ChestType.PC1, isStaff);
        player.sendMessage("§8[§6Cantale§8] §7Coffre privé ouvert.");
        return true;
    }

    public PrivateChestService getChestService() {
        return chestService;
    }
}
