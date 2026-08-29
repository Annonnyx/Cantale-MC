package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.trade.TradeManager;
import fr.cantale.plugin.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /trade — échanges joueur ↔ joueur.
 * Sans argument : rouvre l'échange en cours, sinon sélecteur de joueur.
 * /trade <joueur> : envoie une demande (60 s) — le receveur accepte via le
 * bouton cliquable du chat ou /trade accept. Le reste se fait dans le GUI
 * (dépôt d'items, argent, confirmation, compte à rebours de 5 s).
 */
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
            TradeSession session = tradeManager.getSession(player.getUniqueId());
            if (session != null) {
                tradeManager.openSessionGUI(player, session);
                return true;
            }
            new fr.cantale.plugin.ui.inventory.PlayerSelectorUI(plugin, player, "§8[§6Cantale§8] §7Sélectionner un joueur", target -> {
                player.performCommand("trade " + target.getName());
            }, "Trade").open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> tradeManager.accept(player);
            case "decline", "deny", "cancel" -> tradeManager.decline(player);
            case "money" -> handleMoney(player, args);
            default -> handleRequest(player, args[0]);
        }

        return true;
    }

    private void handleRequest(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable (ou hors ligne).");
            return;
        }
        tradeManager.sendTradeRequest(player, target);
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
            amount = Double.parseDouble(args[1].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cMontant invalide.");
            return;
        }
        tradeManager.setMoney(player, amount);
        player.sendMessage("§8[§6Cantale§8] §aMontant d'échange défini à §6"
            + (amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount))
            + " §aCantox");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of("accept", "decline", "money"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(sender)) suggestions.add(online.getName());
            }
            String prefix = args[0].toLowerCase();
            suggestions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return suggestions;
        }
        return Collections.emptyList();
    }
}
