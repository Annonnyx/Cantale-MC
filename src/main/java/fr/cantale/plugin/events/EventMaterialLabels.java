package fr.cantale.plugin.events;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

/**
 * Libellés français pour les cibles d'événements faction (récolte / minage).
 */
public final class EventMaterialLabels {

    private static final Map<Material, String> LABELS = Map.ofEntries(
            Map.entry(Material.WHEAT, "blé"),
            Map.entry(Material.CARROT, "carotte"),
            Map.entry(Material.POTATO, "pomme de terre"),
            Map.entry(Material.BEETROOT, "betterave"),
            Map.entry(Material.NETHER_WART, "verrues du Nether"),
            Map.entry(Material.SUGAR_CANE, "canne à sucre"),
            Map.entry(Material.MELON_SLICE, "tranche de pastèque"),
            Map.entry(Material.PUMPKIN, "citrouille"),
            Map.entry(Material.SWEET_BERRIES, "baies sucrées"),
            Map.entry(Material.COCOA_BEANS, "fèves de cacao"),
            Map.entry(Material.COAL_ORE, "charbon"),
            Map.entry(Material.IRON_ORE, "fer"),
            Map.entry(Material.COPPER_ORE, "cuivre"),
            Map.entry(Material.GOLD_ORE, "or"),
            Map.entry(Material.REDSTONE_ORE, "redstone"),
            Map.entry(Material.LAPIS_ORE, "lapis-lazuli"),
            Map.entry(Material.DIAMOND_ORE, "diamant"),
            Map.entry(Material.EMERALD_ORE, "émeraude"),
            Map.entry(Material.NETHER_GOLD_ORE, "or du Nether"),
            Map.entry(Material.NETHER_QUARTZ_ORE, "quartz"),
            Map.entry(Material.ANCIENT_DEBRIS, "débris antiques")
    );

    private EventMaterialLabels() {
    }

    public static String french(Material material) {
        if (material == null) return "?";
        Material key = canonicalOre(material);
        String label = LABELS.get(key);
        if (label != null) return label;
        label = LABELS.get(material);
        if (label != null) return label;
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Regroupe minerai / deepslate du même type (ex. DIAMOND_ORE ↔ DEEPSLATE_DIAMOND_ORE).
     */
    public static Material canonicalOre(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.startsWith("DEEPSLATE_")) {
            Material base = Material.matchMaterial(name.substring("DEEPSLATE_".length()));
            if (base != null) return base;
        }
        return material;
    }

    public static boolean sameOreType(Material a, Material b) {
        if (a == null || b == null) return false;
        return canonicalOre(a) == canonicalOre(b);
    }
}
