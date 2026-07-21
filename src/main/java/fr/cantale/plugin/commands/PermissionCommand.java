package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.permissions.PermissionManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PermissionCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PermissionManager permissionManager;

    public PermissionCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.permissionManager = plugin.getPermissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Seul l'owner peut utiliser cette commande
        if (sender instanceof Player player) {
            if (!permissionManager.hasPermission(player.getUniqueId(), PlayerRank.ADMIN)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul un admin peut utiliser cette commande."));
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

        PlayerRank rank = PlayerRank.fromString(args[2]);
        if (rank == PlayerRank.NONE) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cRôle invalide. Rôles disponibles : owner, admin, moderator, vip, player"));
            return;
        }

        permissionManager.setPlayerRole(target.getUniqueId(), rank);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aGrade &e" + rank.getDisplayName() + " &adonné à &e" + target.getName() + "&a."));

        if (target.isOnline()) {
            target.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&aVotre grade est maintenant : &e" + rank.getDisplayName()));
            plugin.getTagManager().updatePlayerNametag(target.getPlayer());
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

        PlayerRank rank = permissionManager.getPlayerRole(target.getUniqueId());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aGrade de &e" + target.getName() + "&a : &e" + rank.getDisplayName()));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== &lGrades disponibles &r&6 ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4&lowner &7- Accès total"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&ladmin &7- Accès admin (équivalent Owner)"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&lmoderator &7- Accès modération"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&lvip &7- Accès VIP"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7&lplayer &7- Joueur normal"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== &lGestion des Permissions &r&6 ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm set <joueur> <role> &7- Définir le grade d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm remove <joueur> &7- Retirer le grade d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm check <joueur> &7- Vérifier le grade d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a/perm list &7- Lister les grades disponibles"));
    }
}
