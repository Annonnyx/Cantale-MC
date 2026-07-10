package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DailyCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public DailyCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        PlayerRank rank = plugin.getRankManager().getRank(player);
        if (rank == PlayerRank.NONE) {
            player.sendMessage(formatMessage("&cVous n'avez aucun grade valide pour ce bonus quotidien."));
            return true;
        }

        int granted = plugin.getRankManager().grantDailyCantox(player);
        if (granted <= 0) {
            player.sendMessage(formatMessage("&cVous avez déjà récupéré votre récompense quotidienne aujourd'hui. Revenez demain !"));
        } else {
            player.sendMessage(formatMessage("&aRécompense quotidienne récupérée : &e" + granted + " Cantox&a."));
        }
        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
