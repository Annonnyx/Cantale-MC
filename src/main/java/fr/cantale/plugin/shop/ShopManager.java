package fr.cantale.plugin.shop;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.ShopDAO;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Logique métier du shop admin : achat/vente avec plafonds quotidiens.
 * Le shop n'est volontairement pas rentable pour le joueur (achat > vente,
 * voir shop.yml) — c'est un plancher de prix + un sink économique, pas un
 * marché équitable (celui-ci reste /ah entre joueurs).
 */
public class ShopManager {

    private final CantalePlugin plugin;
    private final ShopDAO shopDAO;
    private ShopConfig config;

    public ShopManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.shopDAO = new ShopDAO(plugin);
        reload();
    }

    public void reload() {
        ShopConfig newConfig = new ShopConfig(plugin);
        newConfig.load();
        this.config = newConfig;
    }

    public ShopConfig getConfig() {
        return config;
    }

    /** Quantité restante que ce joueur peut encore vendre aujourd'hui pour ce Material, ou -1 si illimité. */
    public int getRemainingSellToday(Player player, Material material) {
        ShopItem item = config.resolve(material);
        if (item == null || item.getDailySellCap() <= 0) return -1;
        int used = shopDAO.getSoldToday(player.getUniqueId(), material);
        return Math.max(0, item.getDailySellCap() - used);
    }

    /** Quantité restante que ce joueur peut encore acheter aujourd'hui pour ce Material, ou -1 si illimité. */
    public int getRemainingBuyToday(Player player, Material material) {
        ShopItem item = config.resolve(material);
        if (item == null || item.getDailyBuyCap() <= 0) return -1;
        int used = shopDAO.getBoughtToday(player.getUniqueId(), material);
        return Math.max(0, item.getDailyBuyCap() - used);
    }

    /**
     * Achète {@code quantity} exemplaires de {@code material} au shop pour {@code player}.
     */
    public ShopResult buy(Player player, Material material, int quantity) {
        if (quantity <= 0) return ShopResult.fail("Quantité invalide.");

        ShopItem item = config.resolve(material);
        if (item == null || !item.isBuyEnabled()) {
            return ShopResult.fail("Cet item n'est pas disponible à l'achat au shop.");
        }

        if (item.getDailyBuyCap() > 0) {
            int already = shopDAO.getBoughtToday(player.getUniqueId(), material);
            int remaining = item.getDailyBuyCap() - already;
            if (remaining <= 0) {
                return ShopResult.fail("Limite quotidienne d'achat atteinte pour cet item (reset à minuit).");
            }
            quantity = Math.min(quantity, remaining);
        }

        double totalPrice = item.getBuyPrice() * quantity;
        if (!plugin.getEconomyManager().hasEnough(player, totalPrice)) {
            return ShopResult.fail("Solde Cantox insuffisant (besoin : " + format(totalPrice) + " Cantox).");
        }

        // Débite d'abord, puis ajuste si l'inventaire ne peut pas tout accueillir.
        plugin.getEconomyManager().withdraw(player, totalPrice);

        ItemStack stack = new ItemStack(material, quantity);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        int actuallyBought = quantity - notAdded;

        if (actuallyBought <= 0) {
            plugin.getEconomyManager().deposit(player, totalPrice);
            return ShopResult.fail("Inventaire plein.");
        }

        if (notAdded > 0) {
            double refund = item.getBuyPrice() * notAdded;
            plugin.getEconomyManager().deposit(player, refund);
            totalPrice -= refund;
        }

        if (item.getDailyBuyCap() > 0) {
            shopDAO.addBought(player.getUniqueId(), material, actuallyBought);
        }

        plugin.getActionLogger().logAction("SHOP_BUY", player.getName(),
                material.name() + " x" + actuallyBought + " pour " + format(totalPrice) + " Cantox");

        return ShopResult.success(actuallyBought, totalPrice);
    }

    /**
     * Vend {@code quantity} exemplaires de {@code material} depuis l'inventaire de {@code player} au shop.
     */
    public ShopResult sell(Player player, Material material, int quantity) {
        if (quantity <= 0) return ShopResult.fail("Quantité invalide.");

        ShopItem item = config.resolve(material);
        if (item == null || !item.isSellEnabled()) {
            return ShopResult.fail("Cet item n'est pas rachetable par le shop.");
        }

        if (item.getDailySellCap() > 0) {
            int already = shopDAO.getSoldToday(player.getUniqueId(), material);
            int remaining = item.getDailySellCap() - already;
            if (remaining <= 0) {
                return ShopResult.fail("Limite quotidienne de vente atteinte pour cet item (reset à minuit).");
            }
            quantity = Math.min(quantity, remaining);
        }

        int available = countSellable(player, material);
        if (available <= 0) {
            return ShopResult.fail("Vous n'avez pas cet item (ou il est protégé/personnalisé).");
        }
        quantity = Math.min(quantity, available);

        removeSellable(player, material, quantity);

        double totalPrice = item.getSellPrice() * quantity;
        plugin.getEconomyManager().deposit(player, totalPrice);

        if (item.getDailySellCap() > 0) {
            shopDAO.addSold(player.getUniqueId(), material, quantity);
        }

        plugin.getActionLogger().logAction("SHOP_SELL", player.getName(),
                material.name() + " x" + quantity + " pour " + format(totalPrice) + " Cantox");

        return ShopResult.success(quantity, totalPrice);
    }

    /** Nombre d'exemplaires réellement vendables (exclut les items custom Cantale et les items renommés). */
    private int countSellable(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (!isSellableStack(stack, material)) continue;
            count += stack.getAmount();
        }
        return count;
    }

    private void removeSellable(Player player, Material material, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isSellableStack(stack, material)) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            contents[i] = stack.getAmount() <= 0 ? null : stack;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean isSellableStack(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) return false;
        if (plugin.getCustomItemManager().isCustomItem(stack)) return false;
        return !(stack.hasItemMeta() && stack.getItemMeta().hasDisplayName());
    }

    private static String format(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }

    /** Résultat d'une transaction d'achat/vente. */
    public static final class ShopResult {
        private final boolean success;
        private final String message;
        private final int quantity;
        private final double total;

        private ShopResult(boolean success, String message, int quantity, double total) {
            this.success = success;
            this.message = message;
            this.quantity = quantity;
            this.total = total;
        }

        static ShopResult fail(String message) {
            return new ShopResult(false, message, 0, 0);
        }

        static ShopResult success(int quantity, double total) {
            return new ShopResult(true, null, quantity, total);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getTotal() {
            return total;
        }
    }
}
