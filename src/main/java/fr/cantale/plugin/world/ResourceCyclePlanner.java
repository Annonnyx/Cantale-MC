package fr.cantale.plugin.world;

/**
 * Reset des zones de récolte. Période de 6 h (terres sauvages) et 12 h (gisements de warzone).
 * Le planner ne touche ni l'inventaire ni le dossier du monde : il dit seulement quels chunks sont dus.
 */
public final class ResourceCyclePlanner {

    /** Terres sauvages : les filons de la zone de récolte reviennent toutes les 6 heures. */
    public static final long RESOURCE_PERIOD_SECONDS = 6L * 3600L;

    /** Warzone : gisements rares, deux fois moins souvent. */
    public static final long WARZONE_DEPOSIT_PERIOD_SECONDS = 12L * 3600L;

    /** Carré de chunks autour du spawn, bords inclus. 16 → 33×33 chunks. */
    public static final int HARVEST_RADIUS_CHUNKS = 16;

    /** Place du camp, autour du spawn, jamais régénérée. */
    public static final int SAFE_CAMP_RADIUS_CHUNKS = 2;

    private ResourceCyclePlanner() {
    }

    public static boolean isDue(long nowSeconds, long lastResetSeconds, long periodSeconds) {
        if (periodSeconds <= 0) {
            return false;
        }
        if (lastResetSeconds <= 0) {
            return true;
        }
        return nowSeconds - lastResetSeconds >= periodSeconds;
    }

    public static long nextResetAt(long lastResetSeconds, long periodSeconds) {
        if (lastResetSeconds <= 0) {
            return 0L;
        }
        return lastResetSeconds + periodSeconds;
    }

    public static boolean isHarvestChunk(int chunkX, int chunkZ) {
        if (Math.abs(chunkX) <= SAFE_CAMP_RADIUS_CHUNKS && Math.abs(chunkZ) <= SAFE_CAMP_RADIUS_CHUNKS) {
            return false;
        }
        return Math.abs(chunkX) <= HARVEST_RADIUS_CHUNKS && Math.abs(chunkZ) <= HARVEST_RADIUS_CHUNKS;
    }
}
