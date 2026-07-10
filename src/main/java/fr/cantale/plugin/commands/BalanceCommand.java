package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final EconomyManager economyManager;

    public BalanceCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = new EconomyManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage console : /balance <joueur>");
                return true;
            }
            double balance = economyManager.getBalance(player);
            player.sendMessage("§8[§6Cantale§8] §7Votre solde : §6" + balance + " §7Cantox");
        } else {
            // Admin check
            if (!sender.hasPermission("cantale.admin.balance")) {
                sender.sendMessage("§cPermission refusée.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
                return true;
            }
            double balance = economyManager.getBalance(target);
            sender.sendMessage("§8[§6Cantale§8] §7Solde de §e" + target.getName() + "§7 : §6" + balance + " §7Cantox");
        }

        return true;
    }
}
