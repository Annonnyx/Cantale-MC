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

        // Toujours l'ender chest vanilla (persistance Minecraft) + migration éventuelle
        // depuis l'ancienne sauvegarde virtuelle private_chests.ec
        plugin.getPrivateChestService().openVanillaEnderChest(player);
        return true;
    }
}
