package fr.cantale.plugin.factions;

/**
 * {@code /f revive} : un officier (ou grade au moins équivalent) abrège le deathban
 * d'un allié de la même faction. Coût : {@link #POWER_COST} points de pouvoir,
 * débités par {@code UPDATE factions SET power = power - ? WHERE power >= ?}.
 * Le pouvoir est déjà la ressource perdue à la mort (−5) ; 15 correspond à trois morts,
 * assez pour empêcher le spam sans toucher au Cantox (pas de rachat de vie).
 * Une seconde exécution ne donne pas de vie supplémentaire : la cible a déjà au moins 1 vie.
 */
public final class FactionReviveRules {

    public static final int POWER_COST = 15;
    public static final int LIVES_GRANTED = 1;

    public enum Deny {
        NOT_OFFICER,
        NOT_SAME_FACTION,
        NOT_DEATHBANNED,
        ALREADY_HAS_LIVES,
        INSUFFICIENT_POWER
    }

    private FactionReviveRules() {
    }

    public static boolean canOrderRevive(FactionRank rank) {
        return rank != null && rank.isAtLeast(FactionRank.OFFICER);
    }

    /**
     * @return {@code null} si le débit peut être tenté
     */
    public static Deny evaluate(FactionRank actorRank, boolean targetInSameFaction, int targetLives,
                                boolean deathbanActive, int factionPower) {
        if (!canOrderRevive(actorRank)) {
            return Deny.NOT_OFFICER;
        }
        if (!targetInSameFaction) {
            return Deny.NOT_SAME_FACTION;
        }
        if (targetLives > 0) {
            return Deny.ALREADY_HAS_LIVES;
        }
        if (!deathbanActive) {
            return Deny.NOT_DEATHBANNED;
        }
        if (factionPower < POWER_COST) {
            return Deny.INSUFFICIENT_POWER;
        }
        return null;
    }

    /** Effet d'un revive accepté : une vie, jamais {@code previous + 1}. */
    public static int livesAfterRevive(int previousLives) {
        if (previousLives > 0) {
            return previousLives;
        }
        return LIVES_GRANTED;
    }
}
