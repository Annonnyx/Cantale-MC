package fr.cantale.plugin.lives;

import java.time.Duration;

/**
 * Deathban temporaire. Pas de ban IP, pas de ban permanent, pas de rachat contre de l'argent réel.
 * À l'échéance (ou après {@code /f revive}), le joueur revient avec {@link #LIVES_ON_RETURN} vie.
 */
public final class LifeRules {

    public static final Duration DEATHBAN = Duration.ofHours(2);
    public static final int LIVES_ON_RETURN = 1;
    public static final boolean BANS_IP = false;
    public static final boolean PERMANENT = false;

    private LifeRules() {
    }

    public static long deathbanSeconds() {
        return DEATHBAN.toSeconds();
    }

    /**
     * @param lastDeathEpochSeconds horodatage {@code players.last_death} (secondes). 0 = inconnu.
     */
    public static boolean deathbanActive(long lastDeathEpochSeconds, long nowEpochSeconds) {
        if (lastDeathEpochSeconds <= 0) {
            return false;
        }
        return nowEpochSeconds < lastDeathEpochSeconds + deathbanSeconds();
    }

    public static boolean shouldBlockLogin(int lives, long lastDeathEpochSeconds, long nowEpochSeconds) {
        return lives <= 0 && deathbanActive(lastDeathEpochSeconds, nowEpochSeconds);
    }

    /** Vie rendue quand la fenêtre de 2 h est passée et que le joueur est encore à 0. */
    public static int livesAfterExpiredBan(int lives, long lastDeathEpochSeconds, long nowEpochSeconds) {
        if (lives <= 0 && !deathbanActive(lastDeathEpochSeconds, nowEpochSeconds)) {
            return LIVES_ON_RETURN;
        }
        return lives;
    }
}
