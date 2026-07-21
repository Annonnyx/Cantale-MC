package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.permissions.PermissionManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnbanIpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PermissionManager permissionManager;

    public UnbanIpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.permissionManager = plugin.getPermissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Console can use this command without permission check
        if (sender instanceof Player player) {
            if (!permissionManager.hasPermission(player.getUniqueId(), PlayerRank.ADMIN)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul un admin peut utiliser cette commande."));
                return true;
            }
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUsage : /unbanip <ip>"));
            return true;
        }

        String ip = args[0];
        
        try {
            // Remove IP ban
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(ip);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aL'IP &e" + ip + " &aa été débannie."));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cErreur : " + e.getMessage()));
        }

        return true;
    }
}
