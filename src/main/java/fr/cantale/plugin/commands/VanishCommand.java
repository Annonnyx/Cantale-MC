package fr.cantale.plugin.commands;

import fr.cantale.plugin.moderation.VanishManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final VanishManager vanishManager;

    public VanishCommand(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin") && !sender.hasPermission("cantale.moderator")) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        vanishManager.toggleVanish(player);
        if (vanishManager.isVanished(player.getUniqueId())) {
            player.sendMessage("§aVanish activé.");
        } else {
            player.sendMessage("§cVanish désactivé.");
        }

        return true;
    }
}
