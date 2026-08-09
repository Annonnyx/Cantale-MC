package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Pc2Command implements CommandExecutor {

    private final CantalePlugin plugin;

    public Pc2Command(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        boolean isStaff = player.hasPermission("cantale.admin");
        PlayerRank rank = isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);
        int chestCount = plugin.getPrivateChestService().getPrivateChestCount(rank);

        ChestType target = ChestType.PC2;
        if (args.length > 0 && args[0].equals("3")) {
            target = ChestType.PC3;
        }

        if (target == ChestType.PC3 && chestCount < 3) {
            player.sendMessage("§8[§6Cantale§8] §cLe 3ème coffre privé (/pc3) est réservé au grade §eChèvre§c.");
            return true;
        }

        if (target == ChestType.PC2 && chestCount < 2) {
            player.sendMessage("§8[§6Cantale§8] §cLe 2ème coffre privé (/pc2) est réservé aux grades §eVIP §7et §eChèvre§c.");
            player.sendMessage("§8[§6Cantale§8] §7Achetez le grade sur la boutique : §ecantale.store");
            return true;
        }

        plugin.getPrivateChestService().openChest(player, target, isStaff);
        player.sendMessage("§8[§6Cantale§8] §7" + target.getTitle() + " §7ouvert.");
        return true;
    }
}
