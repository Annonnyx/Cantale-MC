package fr.cantale.plugin.anticheat.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Détecte les blocs qui cassent instantanément (ou quasi) en maintenant le clic gauche.
 * Ces blocs produisent un spam légitime de {@code LEFT_CLICK_BLOCK} / {@code BlockBreakEvent}
 * et ne doivent pas être traités comme du CPS PvP ni comme un nuker.
 */
public final class InstantBreakUtil {

    private InstantBreakUtil() {
    }

    /**
     * true si le bloc est « soft » / instantané pour ce joueur (herbe, fleurs, neige, torches,
     * ou dureté ≤ 0, ou vitesse de casse ≥ 1 tick avec l'outil actuel).
     */
    public static boolean isSoftOrInstant(Player player, Block block) {
        if (block == null || player == null) {
            return false;
        }

        Material type = block.getType();
        if (type.isAir()) {
            return true;
        }

        float hardness = type.getHardness();
        // Indestructible
        if (hardness < 0f) {
            return false;
        }
        // Instant sans outil (herbe, fleurs, feu, etc.)
        if (hardness <= 0f) {
            return true;
        }
        // Couches de neige, lianes, lichen… : casse spam en maintenant le clic
        if (hardness <= 0.2f) {
            return true;
        }

        if (isKnownSoftPlant(type)) {
            return true;
        }

        // Instant avec l'outil / effets actuels (Efficiency + Haste, etc.)
        try {
            if (block.getBreakSpeed(player) >= 1.0f) {
                return true;
            }
        } catch (Exception ignored) {
            // API / version : ignorer
        }

        return false;
    }

    /**
     * Matériaux végétaux / décoratifs souvent spam-minés même si la dureté API varie selon la version.
     */
    private static boolean isKnownSoftPlant(Material type) {
        String name = type.name();
        return name.contains("GRASS")
                || name.contains("FERN")
                || name.contains("FLOWER")
                || name.contains("TULIP")
                || name.contains("ORCHID")
                || name.contains("ALLIUM")
                || name.contains("BLUET")
                || name.contains("POPPY")
                || name.contains("DANDELION")
                || name.contains("LILAC")
                || name.contains("ROSE_BUSH")
                || name.contains("PEONY")
                || name.contains("SUNFLOWER")
                || name.contains("CORNFLOWER")
                || name.contains("LILY")
                || name.contains("TORCHFLOWER")
                || name.contains("PINK_PETALS")
                || name.contains("PITCHER")
                || name.contains("SEAGRASS")
                || name.contains("KELP")
                || name.contains("VINE")
                || name.contains("MOSS_CARPET")
                || name.equals("SNOW")
                || name.equals("POWDER_SNOW")
                || name.contains("TORCH")
                || name.contains("SAPLING")
                || name.equals("DEAD_BUSH")
                || name.equals("SHORT_GRASS")
                || name.equals("TALL_GRASS")
                || name.equals("FIRE")
                || name.equals("SOUL_FIRE")
                || name.equals("COBWEB");
    }
}
