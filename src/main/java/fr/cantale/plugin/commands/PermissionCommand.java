package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.permissions.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PermissionCommand implements CommandExecutor {

    private final PermissionManager permissionManager;

    public PermissionCommand(CantalePlugin plugin) {
        this.permissionManager = plugin.getPermissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Seul l'owner peut utiliser cette commande
        if (sender instanceof Player player) {
            if (!permissionManager.hasPermission(player.getUniqueId(), PermissionManager.Role.OWNER)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul l'owner peut utiliser cette commande."));
                return true;
            }
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "set":
                handleSet(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "check":
                handleCheck(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUsage : /perm set <joueur> <role>"));
            return;
        }

        String playerName = args[1];
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        OfflinePlayer target;
        
        if (onlinePlayer != null) {
            target = onlinePlayer;
        } else {
            target = Bukkit.getOfflinePlayer(playerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
                return;
            }
        }

        PermissionManager.Role role = PermissionManager.Role.fromString(args[2]);
        if (role == PermissionManager.Role.PLAYER) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cRôle invalide. Rôles disponibles : owner, admin, moderator, vip"));
            return;
        }

        permissionManager.setPlayerRole(target.getUniqueId(), role);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRôle &e" + role.getName() + " &adonné à &e" + target.getName() + "&a."));

        if (target.isOnline()) {
            target.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&aVotre rôle est maintenant : &e" + role.getName()));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUsage : /perm remove <joueur>"));
            return;
        }

        String playerName = args[1];
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        OfflinePlayer target;
        
        if (onlinePlayer != null) {
            target = onlinePlayer;
        } else {
            target = Bukkit.getOfflinePlayer(playerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
                return;
            }
        }

        permissionManager.removePlayerRole(target.getUniqueId());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRôle retiré pour &e" + target.getName() + "&a."));

        if (target.isOnline()) {
            target.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVotre rôle a été retiré."));
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUsage : /perm check <joueur>"));
            return;
        }

        String playerName = args[1];
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        OfflinePlayer target;
        
        if (onlinePlayer != null) {
            target = onlinePlayer;
        } else {
            target = Bukkit.getOfflinePlayer(playerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
                return;
            }
        }

        PermissionManager.Role role = permissionManager.getPlayerRole(target.getUniqueId());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRôle de &e" + target.getName() + "&a : &e" + role.getName()));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== &lRôles disponibles &r&6 ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e&lowner &7- Niveau 4 - Accès total"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&ladmin &7- Niveau 3 - Accès admin"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&lmoderator &7- Niveau 2 - Accès modération"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&lvip &7- Niveau 1 - Accès VIP"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7&lplayer &7- Niveau 0 - Joueur normal"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== &lGestion des Permissions &r&6 ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm set <joueur> <role> &7- Définir le rôle d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm remove <joueur> &7- Retirer le rôle d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm check <joueur> &7- Vérifier le rôle d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm list &7- Lister les rôles disponibles"));
    }
}
