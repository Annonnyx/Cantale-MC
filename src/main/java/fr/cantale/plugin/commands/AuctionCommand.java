package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.auction.AuctionListing;
import fr.cantale.plugin.auction.AuctionManager;
import fr.cantale.plugin.ui.chat.ChatMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final AuctionManager auctionManager;

    public AuctionCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            openAuctionMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell":
                handleSell(player, args);
                break;
            case "buy":
                handleBuy(player, args);
                break;
            case "cancel":
                handleCancel(player, args);
                break;
            case "list":
            case "browse":
                openAuctionMenu(player);
                break;
            case "my":
                openMyListings(player);
                break;
            default:
                player.sendMessage("§8[§6Cantale§8] §cUsage : /ah <sell|buy|cancel|list|my>");
        }

        return true;
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /ah sell [quantity|all] [price]");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cTenez un item dans votre main principale.");
            return;
        }

        int quantity;
        if (args[1].equalsIgnoreCase("all")) {
            quantity = hand.getAmount();
        } else {
            try {
                quantity = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
                return;
            }
        }

        if (quantity <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
            return;
        }

        double pricePerUnit;
        try {
            pricePerUnit = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cPrix invalide.");
            return;
        }

        auctionManager.sellItem(player, hand, quantity, pricePerUnit);
    }

    private void handleBuy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /ah buy <id> [quantity]");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cID invalide.");
            return;
        }

        int quantity = 1;
        if (args.length >= 3) {
            try {
                quantity = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
                return;
            }
        }

        auctionManager.buyItem(player, id, quantity);
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /ah cancel <id>");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cID invalide.");
            return;
        }

        auctionManager.cancelListing(player, id);
    }

    private void openAuctionMenu(Player player) {
        plugin.getUIManager().openAuctionMenu(player);
    }

    private void openMyListings(Player player) {
        plugin.getUIManager().getNavigationManager().openUI(player, new fr.cantale.plugin.ui.inventory.MyAuctionListingsUI(plugin, player));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("sell", "buy", "cancel", "list", "browse", "my");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Collections.singletonList("all");
        }
        return Collections.emptyList();
    }
}
