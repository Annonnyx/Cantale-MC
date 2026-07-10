package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class LastDeathCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;

    public LastDeathCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cCette commande nécessite un joueur cible."));
                return true;
            }
            showLastDeath(sender, ((Player) sender).getUniqueId());
        } else {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                showLastDeath(sender, target.getUniqueId());
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable : &e" + args[0]));
            }
        }
        return true;
    }

    private void showLastDeath(CommandSender sender, UUID uuid) {
        long lastDeath = playerDAO.getLastDeath(uuid);
        int deaths = playerDAO.getDeaths(uuid);
        
        if (lastDeath == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Ce joueur n'est jamais mort."));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String date = sdf.format(new Date(lastDeath * 1000));
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Dernière Mort ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Date : &e" + date));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Nombre total de morts : &c" + deaths));
        
        // TODO: Ajouter la localisation de la mort si stockée
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Localisation : &eNon disponible"));
    }
}
