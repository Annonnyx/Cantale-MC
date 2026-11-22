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
                plugin.messages().send(sender, "balance.console");
                return true;
            }
            double balance = economyManager.getBalance(player);
            plugin.messages().send(player, "balance.self", balance);
        } else {
            // Admin check
            if (!sender.hasPermission("cantale.admin.balance")) {
                plugin.messages().send(sender, "balance.denied");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "balance.unknown");
                return true;
            }
            double balance = economyManager.getBalance(target);
            plugin.messages().send(sender, "balance.other", target.getName(), balance);
        }

        return true;
    }
}
