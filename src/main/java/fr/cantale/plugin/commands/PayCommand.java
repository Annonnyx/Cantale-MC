package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final EconomyManager economyManager;

    public PayCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = new EconomyManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }

        if (args.length < 2) {
            plugin.messages().send(player, "pay.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "pay.unknown");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "pay.self");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            plugin.messages().send(player, "pay.amount");
            return true;
        }

        if (amount <= 0) {
            plugin.messages().send(player, "pay.positive");
            return true;
        }

        if (!economyManager.hasEnough(player, amount)) {
            plugin.messages().send(player, "pay.funds", economyManager.getBalance(player));
            return true;
        }

        if (economyManager.transfer(player, target, amount)) {
            plugin.messages().send(player, "pay.sent", target.getName(), amount);
            plugin.messages().send(target, "pay.received", player.getName(), amount);
        } else {
            plugin.messages().send(player, "pay.failed");
        }

        return true;
    }
}
