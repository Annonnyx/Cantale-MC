package fr.cantale.plugin.listeners;

/**
 * Règle de coup sur un défenseur dans son claim.
 * Hors raid le coup est annulé. Pendant un raid actif de l'attaquant, il passe.
 */
public final class ClaimPvpRules {

    private ClaimPvpRules() {
    }

    public static boolean shouldCancelDefenderHit(boolean victimBelongsToClaim, boolean attackerIsRaidingClaim) {
        return victimBelongsToClaim && !attackerIsRaidingClaim;
    }

    /**
     * Fenêtre qui autorise le coup sur un défenseur dans son claim.
     * Un raid actif suffit. Une guerre déclarée hors de cette fenêtre ne le fait pas.
     */
    public static boolean raidWindow(boolean activeRaid, boolean atWar) {
        return activeRaid || (atWar && activeRaid);
    }

    /**
     * Coup sur un défenseur dans son claim : raid actif ou siège actif, et seulement
     * dans le créneau de vulnérabilité. Une guerre seule, dans ou hors créneau, ne suffit pas.
     */
    public static boolean assaultWindow(boolean activeRaid, boolean activeSiege,
                                         boolean vulnerabilityOpen, boolean atWar) {
        if (atWar && !activeRaid && !activeSiege) {
            return false;
        }
        return vulnerabilityOpen && (activeRaid || activeSiege);
    }
}
