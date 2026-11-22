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
    /** Base versée à tout le monde. Les grades ajoutent {@link fr.cantale.plugin.rank.PlayerRank#getDailyCantox()}. */
    public static final int BASE_DAILY_CANTOX = 1000;

    public DailyCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }

        int granted = plugin.getRankManager().grantDailyCantox(player, BASE_DAILY_CANTOX);
        if (granted <= 0) {
            plugin.messages().send(player, "daily.already");
        } else {
            PlayerRank rank = plugin.getRankManager().getRank(player);
            int rankBonus = rank.getDailyCantox();
            int base = granted - rankBonus;
            if (rankBonus > 0) {
                plugin.messages().send(player, "daily.bonus", base, rankBonus, rank.getDisplayName());
            } else {
                plugin.messages().send(player, "daily.base", granted);
            }
            if (plugin.getSeasonPass() != null) {
                plugin.getSeasonPass().grantPlayXp(player.getUniqueId(),
                        fr.cantale.plugin.cosmetics.SeasonPassRules.XP_DAILY);
            }
        }
        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
