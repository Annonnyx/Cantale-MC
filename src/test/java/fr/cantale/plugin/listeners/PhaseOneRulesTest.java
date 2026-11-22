package fr.cantale.plugin.listeners;

import fr.cantale.plugin.commands.DailyCommand;
import fr.cantale.plugin.factions.FactionCreateRules;
import fr.cantale.plugin.rank.PlayerRank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseOneRulesTest {

    @Test
    void defenderIsHitOnlyWhileTheAttackerIsRaiding() {
        assertTrue(ClaimPvpRules.shouldCancelDefenderHit(true, false));
        assertFalse(ClaimPvpRules.shouldCancelDefenderHit(true, true));
        assertFalse(ClaimPvpRules.shouldCancelDefenderHit(false, false));
        assertFalse(ClaimPvpRules.shouldCancelDefenderHit(false, true));
    }

    @Test
    void declaredWarWithoutAnActiveRaidDoesNotLiftClaimProtection() {
        assertFalse(ClaimPvpRules.raidWindow(false, true));
        assertFalse(ClaimPvpRules.raidWindow(false, false));
        assertTrue(ClaimPvpRules.raidWindow(true, false));
        assertTrue(ClaimPvpRules.raidWindow(true, true));
        assertTrue(ClaimPvpRules.shouldCancelDefenderHit(true, ClaimPvpRules.raidWindow(false, true)));
        assertFalse(ClaimPvpRules.shouldCancelDefenderHit(true, ClaimPvpRules.raidWindow(true, false)));
    }

    @Test
    void factionNeedsTheConfiguredFoundersInsideTheRadius() {
        assertTrue(FactionCreateRules.canFound(2, 2));
        assertFalse(FactionCreateRules.canFound(2, 1));
        assertTrue(FactionCreateRules.isWithinRadius(0, 0, 0, FactionCreateRules.FOUNDING_RADIUS));
        assertFalse(FactionCreateRules.isWithinRadius(40, 0, 0, FactionCreateRules.FOUNDING_RADIUS));
    }

    @Test
    void paidRanksDoNotGrantStealFlightOrAHundredThousandDaily() {
        for (PlayerRank rank : PlayerRank.values()) {
            assertFalse(rank.canStealInClaims());
            assertFalse(rank.hasUnlimitedFlight());
            assertTrue(rank.getDailyCantox() + DailyCommand.BASE_DAILY_CANTOX < 10_000);
        }
        assertTrue(PlayerRank.CHEVRE.getDailyCantox() > PlayerRank.VIP.getDailyCantox());
        assertTrue(PlayerRank.VIP.getDailyCantox() > PlayerRank.AVENTURIER.getDailyCantox());
        assertTrue(PlayerRank.AVENTURIER.getDailyCantox() > PlayerRank.NONE.getDailyCantox());
        assertTrue(PlayerRank.CHEVRE.getDailyCantox() < 100_000);
    }
}
