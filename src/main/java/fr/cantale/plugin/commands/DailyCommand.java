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
    private static final int BASE_DAILY_CANTOX = 1000;

    public DailyCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        int granted = plugin.getRankManager().grantDailyCantox(player, BASE_DAILY_CANTOX);
        if (granted <= 0) {
            player.sendMessage(formatMessage("&cVous avez déjà récupéré votre récompense quotidienne aujourd'hui. Revenez demain !"));
        } else {
            PlayerRank rank = plugin.getRankManager().getRank(player);
            int rankBonus = rank.getDailyCantox();
            int base = granted - rankBonus;
            if (rankBonus > 0) {
                player.sendMessage(formatMessage("&aRécompense quotidienne : &e" + base + " Cantox &7(base) &a+ &e" + rankBonus + " Cantox &7(" + rank.getDisplayName() + ")&a."));
            } else {
                player.sendMessage(formatMessage("&aRécompense quotidienne récupérée : &e" + granted + " Cantox&a."));
            }
        }
        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
