package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.shop.ShopCategory;
import fr.cantale.plugin.shop.ShopItem;
import fr.cantale.plugin.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste paginée des items d'une catégorie du shop.
 * Clic gauche = acheter 1, Shift+clic gauche = acheter 64.
 * Clic droit = vendre 1, Shift+clic droit = vendre tout (jusqu'au plafond/disponible).
 */
public class ShopCategoryUI extends BaseUI {

    private static final int PAGE_SIZE = 36; // slots 9..44
    private static final int GRID_START = 9;

    private final CantalePlugin plugin;
    private final String categoryId;
    /** Mutable: pagination must stay on this same inventory/listener (see {@link #setPage}). */
    private int page;
    private List<ShopItem> items;

    public ShopCategoryUI(CantalePlugin plugin, Player player, String categoryId, int page) {
        super(plugin, player, buildTitle(plugin, categoryId), 6);
        this.plugin = plugin;
        this.categoryId = categoryId;
        this.page = Math.max(0, page);
    }

    private static String buildTitle(CantalePlugin plugin, String categoryId) {
        for (ShopCategory category : plugin.getShopManager().getConfig().getCategories()) {
            if (category.getId().equals(categoryId)) {
                return "§8[§6Cantale§8] §7Shop — " + ChatColor.translateAlternateColorCodes('&', category.getDisplayName());
            }
        }
        return "§8[§6Cantale§8] §7Shop";
    }

    /**
     * Change de page sans rouvrir d'inventaire. Ouvrir un nouveau {@code ShopCategoryUI}
     * par page enregistrait un listener puis {@code openInventory} fermait l'ancien —
     * le close sync pouvait désenregistrer la page nouvellement ouverte (clics morts
     * dès la page 1). Une seule entrée dans l'historique → Retour = ShopMainUI.
     */
    private void setPage(int newPage) {
        if (items == null) {
            items = plugin.getShopManager().getConfig().getItemsInCategory(categoryId);
        }
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / PAGE_SIZE;
        if (newPage < 0 || newPage > maxPage || newPage == page) return;
        this.page = newPage;
        init();
    }

    @Override
    protected void init() {
        // clear: sinon les slots de la page précédente restent (dernière page incomplète,
        // disparition du bouton « suivante », etc.)
        inventory.clear();
        fillEmpty();
        this.items = plugin.getShopManager().getConfig().getItemsInCategory(categoryId);

        double balance = plugin.getEconomyManager().getBalance(player);
        setItem(4, Material.GOLD_INGOT, "§6§lVotre solde", "§e" + (long) balance + " Cantox");

        int from = page * PAGE_SIZE;
        int to = Math.min(items.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ShopItem shopItem = items.get(i);
            setItem(GRID_START + (i - from), shopItem.getMaterial(), buildDisplayName(shopItem), buildLore(shopItem));
        }

        setItem(size - 9, Material.ARROW, "§7§l← Retour", "§7Revenir aux catégories");
        if (page > 0) {
            setItem(size - 6, Material.PAPER, "§e§l← Page précédente");
        }
        setItem(size - 5, Material.BARRIER, "§c§l✕ Fermer");
        if (to < items.size()) {
            setItem(size - 4, Material.PAPER, "§e§lPage suivante →");
        }
        setItem(size - 1, Material.COMPASS, "§e§l↻ Actualiser");
    }

    private String buildDisplayName(ShopItem item) {
        // Nom Material (ex. HOPPER) — reconnaissable à la recherche visuelle / /shop buy
        String name = item.getMaterial().name();
        if (item.getMaterial() == Material.HOPPER) {
            return "§eHopper §8(HOPPER)";
        }
        return "§e" + name;
    }

    private String[] buildLore(ShopItem item) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (item.isBuyEnabled()) {
            lore.add("§aAchat : §6" + format(item.getBuyPrice()) + " Cantox/u");
            int remainingBuy = plugin.getShopManager().getRemainingBuyToday(player, item.getMaterial());
            if (remainingBuy >= 0) lore.add("§7Achat restant aujourd'hui : §e" + remainingBuy);
        } else {
            lore.add("§8Non achetable au shop");
        }
        if (item.isSellEnabled()) {
            lore.add("§cVente : §6" + format(item.getSellPrice()) + " Cantox/u");
            int remainingSell = plugin.getShopManager().getRemainingSellToday(player, item.getMaterial());
            if (remainingSell >= 0) lore.add("§7Vente restante aujourd'hui : §e" + remainingSell);
        } else {
            lore.add("§8Non rachetable par le shop");
        }
        lore.add("");
        if (item.isBuyEnabled()) lore.add("§7Clic gauche : acheter 1 §8| §7Shift+gauche : acheter 64");
        if (item.isSellEnabled()) lore.add("§7Clic droit : vendre 1 §8| §7Shift+droit : vendre tout");
        return lore.toArray(new String[0]);
    }

    private String format(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }

    @Override
    protected void handleClick(int slot) {
        // Non utilisé : onClick est entièrement redéfini ci-dessous pour distinguer achat/vente.
    }

    @Override
    protected void handleClientClick(int slot, boolean right, boolean shift) {
        if (items == null) return;
        if (slot == size - 6 && page > 0) {
            setPage(page - 1);
            return;
        }
        if (slot == size - 4 && (page + 1) * PAGE_SIZE < items.size()) {
            setPage(page + 1);
            return;
        }
        int index = page * PAGE_SIZE + (slot - GRID_START);
        if (slot < GRID_START || index < 0 || index >= items.size()) return;
        ShopItem shopItem = items.get(index);
        if (right) {
            int quantity = shift ? Integer.MAX_VALUE : 1;
            ShopManager.ShopResult result = plugin.getShopManager().sell(player, shopItem.getMaterial(), quantity);
            handleResult(result, shopItem.getMaterial(), false);
        } else {
            int quantity = shift ? 64 : 1;
            ShopManager.ShopResult result = plugin.getShopManager().buy(player, shopItem.getMaterial(), quantity);
            handleResult(result, shopItem.getMaterial(), true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Identity check: all pages of a category share the same title.
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == top || event.isShiftClick()) {
            event.setCancelled(true);
        }
        if (clicked != top) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= size) return;

        if (slot == size - 9) {
            // Une seule ShopCategoryUI dans la pile → revient à ShopMainUI depuis n'importe quelle page.
            plugin.getUIManager().getNavigationManager().goBack(player);
            return;
        }
        if (slot == size - 6 && page > 0) {
            setPage(page - 1);
            return;
        }
        if (slot == size - 5) {
            close();
            return;
        }
        if (slot == size - 4 && (page + 1) * PAGE_SIZE < items.size()) {
            setPage(page + 1);
            return;
        }
        if (slot == size - 1) {
            init();
            return;
        }

        int index = page * PAGE_SIZE + (slot - GRID_START);
        if (slot < GRID_START || index < 0 || index >= items.size()) return;
        ShopItem shopItem = items.get(index);

        if (event.isRightClick()) {
            int quantity = event.isShiftClick() ? Integer.MAX_VALUE : 1;
            ShopManager.ShopResult result = plugin.getShopManager().sell(player, shopItem.getMaterial(), quantity);
            handleResult(result, shopItem.getMaterial(), false);
        } else {
            int quantity = event.isShiftClick() ? 64 : 1;
            ShopManager.ShopResult result = plugin.getShopManager().buy(player, shopItem.getMaterial(), quantity);
            handleResult(result, shopItem.getMaterial(), true);
        }
    }

    private void handleResult(ShopManager.ShopResult result, Material material, boolean buying) {
        if (!result.isSuccess()) {
            player.sendMessage("§8[§6Cantale§8] §c" + result.getMessage());
            return;
        }
        String action = buying ? "Achat" : "Vente";
        String verb = buying ? "payé" : "reçu";
        player.sendMessage("§8[§6Cantale§8] §a" + action + " : §e" + material.name() + " §7x" + result.getQuantity()
                + " §a— " + verb + " §6" + format(result.getTotal()) + " Cantox");
        init();
    }
}
