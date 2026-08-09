package fr.cantale.plugin.shop;

import org.bukkit.Material;

/**
 * Prix et règles d'un Material négociable au shop (résolu depuis une entrée
 * exacte de shop.yml ou depuis une règle de pattern).
 */
public class ShopItem {

    private final Material material;
    private final String categoryId;
    private final double buyPrice;
    private final double sellPrice;
    private final boolean buyEnabled;
    private final boolean sellEnabled;
    private final int dailySellCap;
    private final int dailyBuyCap;

    public ShopItem(Material material, String categoryId, double buyPrice, double sellPrice,
                     boolean buyEnabled, boolean sellEnabled, int dailySellCap, int dailyBuyCap) {
        this.material = material;
        this.categoryId = categoryId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.buyEnabled = buyEnabled;
        this.sellEnabled = sellEnabled;
        this.dailySellCap = dailySellCap;
        this.dailyBuyCap = dailyBuyCap;
    }

    public Material getMaterial() {
        return material;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public boolean isBuyEnabled() {
        return buyEnabled;
    }

    public boolean isSellEnabled() {
        return sellEnabled;
    }

    public int getDailySellCap() {
        return dailySellCap;
    }

    public int getDailyBuyCap() {
        return dailyBuyCap;
    }
}
