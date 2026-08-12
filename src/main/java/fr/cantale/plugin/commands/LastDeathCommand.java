package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class LastDeathCommand implements CommandExecutor {

    private final PlayerDAO playerDAO;

    public LastDeathCommand(CantalePlugin plugin) {
        this.playerDAO = new PlayerDAO(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cCette commande nécessite un joueur cible."));
                return true;
            }
            showLastDeath(sender, ((Player) sender).getUniqueId());
            return true;
        }

        UUID uuid = resolveTargetUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cJoueur introuvable : &e" + args[0]));
            return true;
        }
        showLastDeath(sender, uuid);
        return true;
    }

    /**
     * Online exact, puis BDD players.username, puis OfflinePlayer connu.
     */
    private UUID resolveTargetUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }

        UUID fromDb = playerDAO.getUuidByUsername(name);
        if (fromDb != null) {
            return fromDb;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()
                || playerDAO.playerExists(offline.getUniqueId())) {
            return offline.getUniqueId();
        }
        return null;
    }

    private void showLastDeath(CommandSender sender, UUID uuid) {
        if (!playerDAO.playerExists(uuid)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cJoueur introuvable en base."));
            return;
        }

        long lastDeath = playerDAO.getLastDeath(uuid);
        int deaths = playerDAO.getDeaths(uuid);

        if (lastDeath == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Ce joueur n'est jamais mort."));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String date = sdf.format(new Date(lastDeath * 1000));

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Dernière Mort ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Date : &e" + date));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Nombre total de morts : &c" + deaths));

        PlayerDAO.LastDeathLocation loc = playerDAO.getLastDeathLocation(uuid);
        if (loc != null) {
            String coords = String.format("%s &7X:&e%d &7Y:&e%d &7Z:&e%d",
                    loc.getWorld(),
                    (int) Math.floor(loc.getX()),
                    (int) Math.floor(loc.getY()),
                    (int) Math.floor(loc.getZ()));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Localisation : &e" + coords));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Localisation : &eNon disponible &7(mort avant enregistrement des coords)"));
        }
    }
}
