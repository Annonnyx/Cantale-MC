package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ModHelpCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public ModHelpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin") && !sender.hasPermission("cantale.moderator")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission."));
            return true;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6Cantale&8] &7=== Commandes de Modération ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation &8- &7Activer/Désactiver le mode trace"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation kick <joueur> [raison] &8- &7Expulser un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation mute <joueur> [durée] [raison] &8- &7Muter un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation unmute <joueur> &8- &7Unmuter un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation tp <joueur> &8- &7Se téléporter à un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation tphere <joueur> &8- &7Téléporter un joueur à soi"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/moderation vanish &8- &7Activer/Désactiver le vanish"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e&lInspection inventaires / homes :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/adminhome check <joueur> &8- &7Lister les homes"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/adminhome <joueur> <nom> &8- &7TP vers ce home"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/adminec <joueur> &8- &7Voir/éditer l'ender chest"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7/adminpc <joueur> [1|2|3] &8- &7Voir/éditer les coffres privés"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNote: Vous devez activer le mode trace avec /moderation avant d'utiliser les commandes de modération."));

        return true;
    }
}
