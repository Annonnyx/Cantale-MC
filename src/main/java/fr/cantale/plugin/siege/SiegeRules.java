package fr.cantale.plugin.siege;

/**
 * Un siège actif à la fois par attaquant et par défenseur.
 * La régénération est planifiée 30 minutes après la fin.
 */
public final class SiegeRules {

    public static final int DURATION_SECONDS = 20 * 60;
    public static final int REGEN_DELAY_SECONDS = 30 * 60;
    public static final int REGEN_BLOCKS_PER_BATCH = 30;

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";
    public static final String REGENERATING = "REGENERATING";
    public static final String DONE = "DONE";

    private SiegeRules() {
    }

    public static boolean canOpen(boolean attackerHasActive, boolean defenderBusy,
                                   boolean windowOpen, boolean enemyClaim, boolean pasdic) {
        return windowOpen && enemyClaim && !pasdic && !attackerHasActive && !defenderBusy;
    }

    public static long regenEpoch(long endedAtSeconds, int delaySeconds) {
        return endedAtSeconds + delaySeconds;
    }

    public static boolean regenDue(long nowSeconds, long regenAtSeconds, String status) {
        if (regenAtSeconds <= 0 || DONE.equals(status) || ACTIVE.equals(status)) {
            return false;
        }
        return nowSeconds >= regenAtSeconds
                && (ENDED.equals(status) || REGENERATING.equals(status));
    }
}
