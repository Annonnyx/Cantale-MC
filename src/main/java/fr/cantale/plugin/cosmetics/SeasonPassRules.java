package fr.cantale.plugin.cosmetics;

/**
 * Formules pures du pass. Aucun palier ne donne de solde, de vie, de pouvoir, de vol ou de claim.
 */
public final class SeasonPassRules {

    public static final int SEASON_ID = 1;
    public static final int MAX_TIER = 6;
    public static final int SEASON_DAYS = 90;
    public static final int XP_QUEST = 40;
    public static final int XP_DAILY = 25;

    private SeasonPassRules() {
    }

    public static int xpRequired(int tier) {
        if (tier < 1) {
            return Integer.MAX_VALUE;
        }
        return 50 * tier * (tier + 1) / 2;
    }

    public static int tierReached(int xp) {
        int reached = 0;
        int score = Math.max(0, xp);
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            if (score >= xpRequired(tier)) {
                reached = tier;
            } else {
                break;
            }
        }
        return reached;
    }

    public static int balanceReward() {
        return 0;
    }

    public static int livesReward() {
        return 0;
    }

    public static int powerReward() {
        return 0;
    }

    public static boolean grantsFlight() {
        return false;
    }

    public static boolean grantsClaim() {
        return false;
    }

    public enum Equip {
        OK,
        UNKNOWN,
        LOCKED
    }

    public static Equip equip(boolean knownCosmetic, boolean unlocked) {
        if (!knownCosmetic) {
            return Equip.UNKNOWN;
        }
        if (!unlocked) {
            return Equip.LOCKED;
        }
        return Equip.OK;
    }
}
