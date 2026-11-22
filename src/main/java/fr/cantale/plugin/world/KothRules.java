package fr.cantale.plugin.world;

import java.util.Map;

/**
 * KOTH des terres de guerre. Récompense plafonnée, sans objet de puissance.
 * 400 Cantox, au plus 4 prises par faction et par jour, 45 min entre deux prises.
 */
public final class KothRules {

    public static final int ZONE_RADIUS = 12;
    public static final int ZONE_VERTICAL = 8;
    public static final int CAPTURE_SECONDS = 180;
    public static final double REWARD_CANTOX = 400.0;
    public static final long REWARD_COOLDOWN_SECONDS = 45L * 60L;
    public static final int MAX_CAPTURES_PER_FACTION_PER_DAY = 4;

    private KothRules() {
    }

    public static boolean inside(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= (double) ZONE_RADIUS * ZONE_RADIUS
                && Math.abs(dy) <= ZONE_VERTICAL;
    }

    /** Faction strictement majoritaire. {@code null} si vide ou égalité. */
    public static Integer leading(Map<Integer, Integer> playersByFaction) {
        if (playersByFaction == null || playersByFaction.isEmpty()) {
            return null;
        }
        int bestId = 0;
        int best = 0;
        boolean tie = false;
        for (Map.Entry<Integer, Integer> entry : playersByFaction.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0) {
                continue;
            }
            if (count > best) {
                best = count;
                bestId = entry.getKey();
                tie = false;
            } else if (count == best) {
                tie = true;
            }
        }
        if (best <= 0 || tie) {
            return null;
        }
        return bestId;
    }

    public static int nextProgress(int current, boolean leading, boolean contested, boolean empty) {
        int value = Math.max(0, current);
        if (empty) {
            return Math.max(0, value - 1);
        }
        if (contested) {
            return value;
        }
        if (leading) {
            return value + 1;
        }
        return Math.max(0, value - 1);
    }

    public static boolean captureComplete(int progressSeconds) {
        return progressSeconds >= CAPTURE_SECONDS;
    }

    public static boolean rewardAllowed(int capturesToday, long nowSeconds, long lastRewardSeconds) {
        if (capturesToday >= MAX_CAPTURES_PER_FACTION_PER_DAY) {
            return false;
        }
        if (lastRewardSeconds <= 0) {
            return true;
        }
        return nowSeconds >= lastRewardSeconds + REWARD_COOLDOWN_SECONDS;
    }
}
