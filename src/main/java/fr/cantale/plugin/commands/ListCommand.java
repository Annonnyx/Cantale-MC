package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ListCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;
    private final SimpleDateFormat dateFormat;

    public ListCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("listemorts")) {
            return false;
        }

        List<PlayerDAO.DeadPlayer> deadPlayers = playerDAO.getPermanentlyDeadPlayers();

        if (deadPlayers.isEmpty()) {
            sender.sendMessage(formatMessage("&aPersonne n'a encore été banni pour mort définitive."));
            return true;
        }

        sender.sendMessage(formatMessage("&c&l=== JOUEURS MORTS DÉFINITIVEMENT ==="));
        sender.sendMessage(formatMessage("&7Total : &c" + deadPlayers.size() + " &7joueurs"));
        sender.sendMessage("");

        int rank = 1;
        for (PlayerDAO.DeadPlayer player : deadPlayers) {
            String date = dateFormat.format(new Date(player.getLastDeath() * 1000));
            String line = String.format(
                "&7#%d &c%s &7- Mort le &c%s &7(&c%d&7 morts)",
                rank++,
                player.getUsername(),
                date,
                player.getDeaths()
            );
            sender.sendMessage(formatMessage(line));
        }

        sender.sendMessage("");
        sender.sendMessage(formatMessage("&7Liste complète sur : &chttps://cantale.world/liste.html"));

        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
