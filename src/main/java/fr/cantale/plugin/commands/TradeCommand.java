package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.trade.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final TradeManager tradeManager;

    public TradeCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.tradeManager = plugin.getTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            new fr.cantale.plugin.ui.inventory.PlayerSelectorUI(plugin, player, "§8[§6Cantale§8] §7Sélectionner un joueur", target -> {
                player.performCommand("trade " + target.getName());
            }, "Trade").open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept":
                handleAccept(player);
                break;
            case "decline":
            case "cancel":
                handleDecline(player);
                break;
            case "money":
                handleMoney(player, args);
                break;
            default:
                handleRequest(player, args[0]);
                break;
        }

        return true;
    }

    private void handleRequest(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
            return;
        }

        tradeManager.sendTradeRequest(player, target);
    }

    private void handleAccept(Player player) {
        if (!tradeManager.hasActiveTrade(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas d'échange en cours.");
            return;
        }
        tradeManager.acceptTrade(player);
    }

    private void handleDecline(Player player) {
        if (!tradeManager.hasActiveTrade(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas d'échange en cours.");
            return;
        }
        tradeManager.declineTrade(player);
    }

    private void handleMoney(Player player, String[] args) {
        if (!tradeManager.hasActiveTrade(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas d'échange en cours.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /trade money <montant>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cMontant invalide.");
            return;
        }

        tradeManager.setMoney(player, amount);
        player.sendMessage("§8[§6Cantale§8] §aMontant d'échange défini à §6" + amount + " §aCantox");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("accept", "decline", "money");
        }
        return Collections.emptyList();
    }
}
