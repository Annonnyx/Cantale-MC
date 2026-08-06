package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.events.FactionEventManager;
import fr.cantale.plugin.ui.inventory.HarvestDepositUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RecolteCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public RecolteCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        FactionEventManager manager = plugin.getFactionEventManager();
        if (manager == null || !manager.isHarvestActive()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Récolte&8] &cPas de récolte en cours."));
            return true;
        }

        new HarvestDepositUI(plugin, player, manager).open();
        return true;
    }
}
