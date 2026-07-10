package fr.cantale.plugin.auction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {
    private int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final Material itemType;
    private int quantity;
    private final double pricePerUnit;
    private final long createdAt;
    private long expiresAt;
    private UUID buyerUuid;

    public AuctionListing(int id, UUID sellerUuid, String sellerName, ItemStack item, Material itemType, int quantity, double pricePerUnit, long createdAt, long expiresAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.itemType = itemType;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.buyerUuid = null;
    }

    public AuctionListing(UUID sellerUuid, String sellerName, ItemStack item, int quantity, double pricePerUnit, long durationMs) {
        this(0, sellerUuid, sellerName, item, item.getType(), quantity, pricePerUnit, System.currentTimeMillis() / 1000, (System.currentTimeMillis() + durationMs) / 1000);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public Material getItemType() {
        return itemType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public double getTotalPrice() {
        return pricePerUnit * quantity;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getBuyerUuid() {
        return buyerUuid;
    }

    public void setBuyerUuid(UUID buyerUuid) {
        this.buyerUuid = buyerUuid;
    }

    public boolean isSold() {
        return buyerUuid != null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 > expiresAt;
    }

    public boolean isShulker() {
        return itemType == Material.SHULKER_BOX || itemType == Material.WHITE_SHULKER_BOX
                || itemType == Material.ORANGE_SHULKER_BOX || itemType == Material.MAGENTA_SHULKER_BOX
                || itemType == Material.LIGHT_BLUE_SHULKER_BOX || itemType == Material.YELLOW_SHULKER_BOX
                || itemType == Material.LIME_SHULKER_BOX || itemType == Material.PINK_SHULKER_BOX
                || itemType == Material.GRAY_SHULKER_BOX || itemType == Material.LIGHT_GRAY_SHULKER_BOX
                || itemType == Material.CYAN_SHULKER_BOX || itemType == Material.PURPLE_SHULKER_BOX
                || itemType == Material.BLUE_SHULKER_BOX || itemType == Material.BROWN_SHULKER_BOX
                || itemType == Material.GREEN_SHULKER_BOX || itemType == Material.RED_SHULKER_BOX
                || itemType == Material.BLACK_SHULKER_BOX;
    }
}
