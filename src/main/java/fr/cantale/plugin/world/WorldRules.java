package fr.cantale.plugin.world;

/**
 * Où un claim peut exister, et où les protections de claim s'appliquent.
 * Warzone, terres sauvages et sanctuaire refusent {@code /claim} et tout autre chemin.
 */
public final class WorldRules {

    private WorldRules() {
    }

    public static boolean claimsAllowed(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return WorldTopology.FACTIONS.equals(worldName) || WorldTopology.LEGACY.equals(worldName);
    }

    /** Protections de claim (grief, PvP de base). Les autres mondes sont traités comme wilderness. */
    public static boolean claimRulesApply(String worldName) {
        return claimsAllowed(worldName);
    }

    public static boolean isHub(String worldName) {
        return WorldTopology.HUB.equals(worldName);
    }

    public static boolean isWarzone(String worldName) {
        return WorldTopology.WARZONE.equals(worldName);
    }

    public static boolean isResources(String worldName) {
        return WorldTopology.RESOURCES.equals(worldName);
    }
}
