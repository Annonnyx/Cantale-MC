package fr.cantale.plugin.custom;

import org.bukkit.Material;

public enum CustomItemType {
    PICKANTAXE_IRON_1X1("Pickantaxe Fer 1x1", Material.IRON_PICKAXE, 1),
    PICKANTAXE_IRON_3X3("Pickantaxe Fer 3x3", Material.IRON_PICKAXE, 3),
    PICKANTAXE_IRON_5X5("Pickantaxe Fer 5x5", Material.IRON_PICKAXE, 5),
    PICKANTAXE_IRON_7X7("Pickantaxe Fer 7x7", Material.IRON_PICKAXE, 7),
    PICKANTAXE_DIAMOND_1X1("Pickantaxe Diamant 1x1", Material.DIAMOND_PICKAXE, 1),
    PICKANTAXE_DIAMOND_3X3("Pickantaxe Diamant 3x3", Material.DIAMOND_PICKAXE, 3),
    PICKANTAXE_DIAMOND_5X5("Pickantaxe Diamant 5x5", Material.DIAMOND_PICKAXE, 5),
    PICKANTAXE_DIAMOND_7X7("Pickantaxe Diamant 7x7", Material.DIAMOND_PICKAXE, 7),
    PICKANTAXE_NETHERITE_1X1("Pickantaxe Netherite 1x1", Material.NETHERITE_PICKAXE, 1),
    PICKANTAXE_NETHERITE_3X3("Pickantaxe Netherite 3x3", Material.NETHERITE_PICKAXE, 3),
    PICKANTAXE_NETHERITE_5X5("Pickantaxe Netherite 5x5", Material.NETHERITE_PICKAXE, 5),
    PICKANTAXE_NETHERITE_7X7("Pickantaxe Netherite 7x7", Material.NETHERITE_PICKAXE, 7),
    CANTAXE_IRON("Cantaxe Fer", Material.IRON_AXE, 0),
    CANTAXE_DIAMOND("Cantaxe Diamant", Material.DIAMOND_AXE, 0),
    CANTAXE_NETHERITE("Cantaxe Netherite", Material.NETHERITE_AXE, 0),
    MULTI_CANTOOL_IRON_1X1("Multi-Cantool Fer 1x1", Material.IRON_PICKAXE, 1),
    MULTI_CANTOOL_IRON("Multi-Cantool Fer", Material.IRON_PICKAXE, 0),
    MULTI_CANTOOL_DIAMOND_1X1("Multi-Cantool Diamant 1x1", Material.DIAMOND_PICKAXE, 1),
    MULTI_CANTOOL_DIAMOND("Multi-Cantool Diamant", Material.DIAMOND_PICKAXE, 0),
    MULTI_CANTOOL_NETHERITE_1X1("Multi-Cantool Netherite 1x1", Material.NETHERITE_PICKAXE, 1),
    MULTI_CANTOOL_NETHERITE("Multi-Cantool Netherite", Material.NETHERITE_PICKAXE, 0),
    CANTALAME("Cantalame", Material.NETHERITE_SWORD, 0),
    VIE("Vie", Material.TOTEM_OF_UNDYING, 0);

    private final String displayName;
    private final Material material;
    private final int miningRadius;

    CustomItemType(String displayName, Material material, int miningRadius) {
        this.displayName = displayName;
        this.material = material;
        this.miningRadius = miningRadius;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMiningRadius() {
        return miningRadius;
    }

    public boolean isPickantise() {
        return name().startsWith("PICKANTAXE");
    }

    public boolean isCantaxe() {
        return name().startsWith("CANTAXE");
    }

    public boolean isMultiCantool() {
        return name().startsWith("MULTI_CANTOOL");
    }

    public boolean isCantalame() {
        return name().equals("CANTALAME");
    }
}
