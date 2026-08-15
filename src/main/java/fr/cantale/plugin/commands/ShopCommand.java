package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public ShopCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs. Depuis la console : /shop reload");
            return true;
        }

        if (args.length == 0) {
            plugin.getUIManager().getNavigationManager().openUI(player,
                    new fr.cantale.plugin.ui.inventory.ShopMainUI(plugin, player));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy" -> handleBuy(player, args);
            case "sell" -> handleSell(player, args);
            default -> player.sendMessage(formatMessage("&cUsage : /shop [buy|sell] <item> <quantité> | /shop reload"));
        }

        return true;
    }

    private void handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(formatMessage("&cUsage : /shop buy <item> <quantité>"));
            return;
        }
        Material material = parseMaterial(player, args[1]);
        if (material == null) return;

        int quantity = parseQuantity(player, args[2]);
        if (quantity <= 0) return;

        ShopManager.ShopResult result = plugin.getShopManager().buy(player, material, quantity);
        report(player, result, material, true);
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /shop sell <item|hand> <quantité|all>"));
            return;
        }

        Material material;
        int handHeldAmount = -1;
        if (args[1].equalsIgnoreCase("hand")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(formatMessage("&cTenez un item dans votre main."));
                return;
            }
            material = hand.getType();
            handHeldAmount = hand.getAmount();
        } else {
            material = parseMaterial(player, args[1]);
            if (material == null) return;
        }

        int quantity;
        if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
            quantity = Integer.MAX_VALUE;
        } else if (args.length >= 3) {
            quantity = parseQuantity(player, args[2]);
            if (quantity <= 0) return;
        } else if (handHeldAmount > 0) {
            quantity = handHeldAmount;
        } else {
            player.sendMessage(formatMessage("&cUsage : /shop sell <item|hand> <quantité|all>"));
            return;
        }

        ShopManager.ShopResult result = plugin.getShopManager().sell(player, material, quantity);
        report(player, result, material, false);
    }

    private void handleReload(CommandSender sender) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("cantale.admin")) {
            sender.sendMessage(plainOrPrefixed(sender, "&cPermission refusée."));
            return;
        }
        try {
            plugin.getShopManager().reload();
            sender.sendMessage(plainOrPrefixed(sender, "&aCatalogue du shop rechargé depuis shop.yml."));
        } catch (Exception e) {
            sender.sendMessage(plainOrPrefixed(sender, "&cÉchec du rechargement du shop : " + e.getMessage()));
            plugin.getLogger().warning("[Shop] Échec /shop reload : " + e.getMessage());
        }
    }

    /** Préfixe + couleurs en jeu ; texte français brut en console (pas de MiniMessage). */
    private String plainOrPrefixed(CommandSender sender, String msg) {
        if (sender instanceof ConsoleCommandSender) {
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', msg));
        }
        return formatMessage(msg);
    }

    private void report(Player player, ShopManager.ShopResult result, Material material, boolean buying) {
        if (!result.isSuccess()) {
            player.sendMessage(formatMessage("&c" + result.getMessage()));
            return;
        }
        String action = buying ? "Achat" : "Vente";
        String verb = buying ? "payé" : "reçu";
        player.sendMessage(formatMessage("&a" + action + " effectué(e) : &e" + material.name() + " §7x" + result.getQuantity()
                + " &a— " + verb + " &6" + formatAmount(result.getTotal()) + " Cantox&a."));
    }

    private Material parseMaterial(Player player, String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(formatMessage("&cItem inconnu : " + name));
            return null;
        }
    }

    private int parseQuantity(Player player, String value) {
        try {
            int qty = Integer.parseInt(value);
            if (qty <= 0) {
                player.sendMessage(formatMessage("&cQuantité invalide."));
                return 0;
            }
            return qty;
        } catch (NumberFormatException e) {
            player.sendMessage(formatMessage("&cQuantité invalide."));
            return 0;
        }
    }

    private String formatAmount(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("buy", "sell"));
            if (sender instanceof ConsoleCommandSender || sender.hasPermission("cantale.admin")) {
                options.add("reload");
            }
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Arrays.asList("hand");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            return Arrays.asList("all");
        }
        return List.of();
    }
}
