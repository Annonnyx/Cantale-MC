package fr.cantale.plugin.factions;

/**
 * Seuil de création d'une faction. Le créateur compte parmi les fondateurs présents.
 */
public final class FactionCreateRules {

    /** Rayon, en blocs, dans lequel les fondateurs sans faction doivent se tenir. */
    public static final double FOUNDING_RADIUS = 32.0;

    private FactionCreateRules() {
    }

    public static boolean canFound(int minimumMembers, int eligiblePlayersPresent) {
        int required = Math.max(1, minimumMembers);
        return eligiblePlayersPresent >= required;
    }

    public static boolean isWithinRadius(double dx, double dy, double dz, double radius) {
        if (radius < 0) {
            return false;
        }
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
