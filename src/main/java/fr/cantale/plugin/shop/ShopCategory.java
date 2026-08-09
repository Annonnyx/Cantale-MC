package fr.cantale.plugin.shop;

/**
 * Une catégorie d'affichage du shop (regroupement visuel dans l'UI).
 */
public class ShopCategory {

    private final String id;
    private final String displayName;
    private final org.bukkit.Material icon;

    public ShopCategory(String id, String displayName, org.bukkit.Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public org.bukkit.Material getIcon() {
        return icon;
    }
}
