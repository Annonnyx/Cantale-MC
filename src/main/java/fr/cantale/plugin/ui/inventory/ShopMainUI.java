package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.shop.ShopCategory;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Menu principal de la Boutique Admin : sélection d'une catégorie.
 */
public class ShopMainUI extends BaseUI {

    private final CantalePlugin plugin;

    public ShopMainUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Boutique Admin", 4);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        fillEmpty();

        double balance = plugin.getEconomyManager().getBalance(player);
        setItem(4, org.bukkit.Material.SUNFLOWER, "§6§lBoutique Admin",
                "§7Votre solde : §e" + (long) balance + " Cantox",
                "§7Plancher de prix garanti — pas le marché réel",
                "§7Le vrai marché entre joueurs, c'est §e/ah");

        List<ShopCategory> categories = plugin.getShopManager().getConfig().getCategories();
        for (int i = 0; i < categories.size(); i++) {
            ShopCategory category = categories.get(i);
            int row = i / 7;
            int col = i % 7;
            int slot = 9 * (row + 1) + 1 + col;
            setItem(slot, category.getIcon(), category.getDisplayName(), "§7Cliquez pour voir les items");
        }

        addNavigationBar(false, true);
    }

    @Override
    protected void handleClick(int slot) {
        List<ShopCategory> categories = plugin.getShopManager().getConfig().getCategories();
        for (int i = 0; i < categories.size(); i++) {
            int row = i / 7;
            int col = i % 7;
            int expectedSlot = 9 * (row + 1) + 1 + col;
            if (expectedSlot == slot) {
                ShopCategory category = categories.get(i);
                plugin.getUIManager().getNavigationManager().openUI(player,
                        new ShopCategoryUI(plugin, player, category.getId(), 0));
                return;
            }
        }
    }
}
