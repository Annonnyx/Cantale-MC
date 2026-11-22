package fr.cantale.plugin.listeners;

import java.util.UUID;

/**
 * Protection du butin de mort, étendue au ramassage.
 * Même clé PDC {@code death_loot_until} que l'anti-feu : pas de second système.
 * Le propriétaire peut ramasser tout de suite ; les autres attendent {@link #DURATION_MS}.
 */
public final class DeathLootProtection {

    public static final long DURATION_MS = 5L * 60L * 1000L;
    public static final String UNTIL_KEY = "death_loot_until";
    public static final String OWNER_KEY = "death_loot_owner";

    private DeathLootProtection() {
    }

    public static long until(long nowMs) {
        return nowMs + DURATION_MS;
    }

    public static boolean expired(long untilMs, long nowMs) {
        return nowMs > untilMs;
    }

    /**
     * @return true si ce joueur ne doit pas ramasser l'item pour l'instant
     */
    public static boolean blocksPickup(UUID owner, UUID picker, long untilMs, long nowMs) {
        if (owner == null || picker == null) {
            return false;
        }
        if (expired(untilMs, nowMs)) {
            return false;
        }
        return !owner.equals(picker);
    }
}
