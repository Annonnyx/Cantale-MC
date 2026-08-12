package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Accès outils staff : permission Bukkit OU grade PlayerRank staff (Modo/Admin/Owner).
 */
final class StaffAccess {

    private StaffAccess() {}

    static boolean canUseStaffTools(CantalePlugin plugin, CommandSender sender) {
        if (sender.hasPermission("cantale.admin") || sender.hasPermission("cantale.moderator")) {
            return true;
        }
        if (sender instanceof Player player) {
            PlayerRank rank = plugin.getRankManager().getRank(player);
            return rank != null && rank.isStaff();
        }
        return false;
    }
}
