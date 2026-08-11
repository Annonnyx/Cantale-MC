package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.permissions.PermissionManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class UnbanIpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PermissionManager permissionManager;

    public UnbanIpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.permissionManager = plugin.getPermissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (!permissionManager.hasPermission(player.getUniqueId(), PlayerRank.ADMIN)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cSeul un admin peut utiliser cette commande."));
                return true;
            }
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage : /unbanip <ip|joueur>"));
            return true;
        }

        String arg = args[0].trim();

        // Si ça ressemble à une IP → pardon direct
        if (looksLikeIp(arg)) {
            try {
                Bukkit.getBanList(BanList.Type.IP).pardon(arg);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aL'IP &e" + arg + " &aa été débannie."));
            } catch (Exception e) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cErreur : " + e.getMessage()));
            }
            return true;
        }

        // Sinon : pseudo → pardon vies complet (NAME + PROFILE + IP connue)
        OfflinePlayer offline = Bukkit.getOfflinePlayer(arg);
        UUID uuid = offline.getUniqueId();
        String name = offline.getName() != null ? offline.getName() : arg;
        plugin.getLifeManager().pardonLifeBan(uuid, name);
        if (plugin.getLifeManager().getLives(uuid) <= 0) {
            plugin.getLifeManager().setLives(uuid, 1);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&eVies à 0 → remises à &a1&e."));
        }
        String ip = new fr.cantale.plugin.database.PlayerDAO(plugin).getLastIp(uuid);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aBan vies levé pour &e" + name
                        + (ip != null ? " &7(IP &e" + ip + "&7)" : " &7(IP inconnue — check &ebanned-ips.json&7)")));
        return true;
    }

    private static boolean looksLikeIp(String value) {
        // IPv4 simple ou IPv6 contenant ':'
        if (value.contains(":")) return true;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
