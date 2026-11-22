package fr.cantale.plugin.world;

import fr.cantale.plugin.listeners.ClaimPvpRules;
import fr.cantale.plugin.siege.SiegeBreakPace;
import fr.cantale.plugin.siege.SiegeRules;
import fr.cantale.plugin.siege.VulnerabilityWindow;
import fr.cantale.plugin.skills.SkillChangeRules;
import fr.cantale.plugin.skills.SkillFormulas;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseThreeRulesTest {

    @Test
    void claimsAreForbiddenInWarzoneResourcesAndHub() {
        assertFalse(WorldRules.claimsAllowed(WorldTopology.WARZONE));
        assertFalse(WorldRules.claimsAllowed(WorldTopology.RESOURCES));
        assertFalse(WorldRules.claimsAllowed(WorldTopology.HUB));
        assertFalse(WorldRules.claimsAllowed("world_nether"));
        assertFalse(WorldRules.claimsAllowed(null));
        assertTrue(WorldRules.claimsAllowed(WorldTopology.FACTIONS));
        assertTrue(WorldRules.claimsAllowed(WorldTopology.LEGACY));
        assertFalse(WorldRules.claimRulesApply(WorldTopology.WARZONE));
        assertTrue(WorldRules.claimRulesApply(WorldTopology.FACTIONS));
    }

    @Test
    void spawnAndRtpDoNotDropAPlayerIntoTheWarzone() {
        assertEquals(WorldTopology.HUB, WorldTransit.spawnWorld(true, true));
        assertEquals(WorldTopology.LEGACY, WorldTransit.spawnWorld(false, true));
        assertFalse(WorldTopology.WARZONE.equals(WorldTransit.spawnWorld(false, false)));
        assertEquals(WorldTopology.RESOURCES, WorldTransit.rtpWorld(WorldTopology.HUB));
        assertEquals(WorldTopology.FACTIONS, WorldTransit.rtpWorld(WorldTopology.FACTIONS));
        assertEquals(WorldTopology.WARZONE, WorldTransit.rtpWorld(WorldTopology.WARZONE));
        assertFalse(WorldTransit.allows(WorldTopology.FACTIONS, WorldTopology.WARZONE, false));
        assertFalse(WorldTransit.allows(WorldTopology.HUB, WorldTopology.WARZONE, false));
        assertTrue(WorldTransit.allows(WorldTopology.FACTIONS, WorldTopology.WARZONE, true));
        assertTrue(WorldTransit.allows(WorldTopology.WARZONE, WorldTopology.WARZONE, false));
        assertTrue(WorldTransit.allows(WorldTopology.HUB, WorldTopology.RESOURCES, false));
    }

    @Test
    void harvestResetIsDueOnARealPeriod() {
        long period = ResourceCyclePlanner.RESOURCE_PERIOD_SECONDS;
        assertEquals(6L * 3600L, period);
        assertEquals(12L * 3600L, ResourceCyclePlanner.WARZONE_DEPOSIT_PERIOD_SECONDS);
        assertTrue(ResourceCyclePlanner.isDue(10_000L, 0L, period));
        assertFalse(ResourceCyclePlanner.isDue(period - 1, 100L, period));
        assertTrue(ResourceCyclePlanner.isDue(100L + period, 100L, period));
        assertEquals(100L + period, ResourceCyclePlanner.nextResetAt(100L, period));
        assertFalse(ResourceCyclePlanner.isHarvestChunk(0, 0));
        assertTrue(ResourceCyclePlanner.isHarvestChunk(ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS, 0));
        assertFalse(ResourceCyclePlanner.isHarvestChunk(ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS + 1, 0));
        assertEquals(HarvestLayout.nodeId(4, 20, 8, 3L, false), HarvestLayout.nodeId(4, 20, 8, 3L, false));
        assertNull(HarvestLayout.nodeId(4, 1, 8, 3L, false));
    }

    @Test
    void vulnerabilityWindowDefaultsToEveningAndCanWrapMidnight() {
        assertTrue(VulnerabilityWindow.isOpen(18 * 60, 22 * 60, 18 * 60));
        assertTrue(VulnerabilityWindow.isOpen(18 * 60, 22 * 60, 21 * 60 + 59));
        assertFalse(VulnerabilityWindow.isOpen(18 * 60, 22 * 60, 22 * 60));
        assertFalse(VulnerabilityWindow.isOpen(18 * 60, 22 * 60, 12 * 60));
        assertFalse(VulnerabilityWindow.isOpen(18 * 60, 18 * 60, 18 * 60));
        assertTrue(VulnerabilityWindow.isOpen(22 * 60, 2 * 60, 23 * 60));
        assertTrue(VulnerabilityWindow.isOpen(22 * 60, 2 * 60, 1 * 60));
        assertFalse(VulnerabilityWindow.isOpen(22 * 60, 2 * 60, 12 * 60));
        assertEquals(18 * 60, VulnerabilityWindow.parseMinute("18:00"));
        assertEquals(22 * 60, VulnerabilityWindow.parseMinute("22:00"));
        assertEquals(VulnerabilityWindow.DEFAULT_START_MINUTE, 18 * 60);
        assertEquals(VulnerabilityWindow.DEFAULT_END_MINUTE, 22 * 60);
    }

    @Test
    void onlyOneSiegeCanOpenAndRegenIsScheduledThirtyMinutesLater() {
        assertTrue(SiegeRules.canOpen(false, false, true, true, false));
        assertFalse(SiegeRules.canOpen(false, true, true, true, false));
        assertFalse(SiegeRules.canOpen(true, false, true, true, false));
        assertFalse(SiegeRules.canOpen(false, false, false, true, false));
        assertFalse(SiegeRules.canOpen(false, false, true, false, false));
        assertFalse(SiegeRules.canOpen(false, false, true, true, true));
        long ended = 1_700_000_000L;
        assertEquals(ended + 30L * 60L, SiegeRules.regenEpoch(ended, SiegeRules.REGEN_DELAY_SECONDS));
        assertEquals(30 * 60, SiegeRules.REGEN_DELAY_SECONDS);
        assertFalse(SiegeRules.regenDue(ended + 30L * 60L - 1, ended + 30L * 60L, SiegeRules.ENDED));
        assertTrue(SiegeRules.regenDue(ended + 30L * 60L, ended + 30L * 60L, SiegeRules.ENDED));
        assertTrue(SiegeRules.regenDue(ended + 40L * 60L, ended + 30L * 60L, SiegeRules.REGENERATING));
        assertFalse(SiegeRules.regenDue(ended + 40L * 60L, ended + 30L * 60L, SiegeRules.ACTIVE));
        assertFalse(SiegeRules.regenDue(ended + 40L * 60L, ended + 30L * 60L, SiegeRules.DONE));
    }

    @Test
    void warAloneNeverOpensClaimPvpEvenInsideTheVulnerabilityWindow() {
        assertFalse(ClaimPvpRules.assaultWindow(false, false, true, true));
        assertFalse(ClaimPvpRules.assaultWindow(true, false, false, false));
        assertFalse(ClaimPvpRules.assaultWindow(false, true, false, false));
        assertTrue(ClaimPvpRules.assaultWindow(true, false, true, false));
        assertTrue(ClaimPvpRules.assaultWindow(false, true, true, true));
        assertTrue(ClaimPvpRules.shouldCancelDefenderHit(true, ClaimPvpRules.assaultWindow(false, false, true, true)));
        assertFalse(ClaimPvpRules.shouldCancelDefenderHit(true, ClaimPvpRules.assaultWindow(true, false, true, false)));
    }

    @Test
    void fourProfessionsStackUntilTheirCaps() {
        assertEquals(1, SkillFormulas.architectBreaksRequired(0));
        assertEquals(2, SkillFormulas.architectBreaksRequired(1));
        assertEquals(4, SkillFormulas.architectBreaksRequired(3));
        assertEquals(4, SkillFormulas.architectBreaksRequired(5));
        assertEquals(4, SkillFormulas.architectBreaksRequired(40));

        assertEquals(1.0, SkillFormulas.negociantCostMultiplier(0));
        assertEquals(0.80, SkillFormulas.negociantCostMultiplier(5), 1e-9);
        assertEquals(0.80, SkillFormulas.negociantCostMultiplier(12), 1e-9);
        assertEquals(80.0, SkillFormulas.discountedAmount(100.0, 9), 1e-9);
        assertTrue(SkillFormulas.discountedAmount(100.0, 9) <= 100.0);
        assertEquals(0.0, SkillFormulas.discountedAmount(-5, 5));

        assertEquals(10, SkillFormulas.artisanRepairCost(10, 0));
        assertEquals(6, SkillFormulas.artisanRepairCost(10, 5));
        assertEquals(1, SkillFormulas.artisanRepairCost(2, 5));
        assertEquals(0, SkillFormulas.artisanDurationBonusTicks(0));
        assertEquals(60 * 20, SkillFormulas.artisanDurationBonusTicks(5));
        assertEquals(60 * 20, SkillFormulas.artisanDurationBonusTicks(9));

        assertEquals(1.0, SkillFormulas.knightOutgoingMultiplier(0));
        assertEquals(1.15, SkillFormulas.knightOutgoingMultiplier(5), 1e-9);
        assertEquals(1.15, SkillFormulas.knightOutgoingMultiplier(8), 1e-9);
        assertTrue(SkillFormulas.knightOutgoingMultiplier(100) <= 1.0 + SkillFormulas.KNIGHT_MAX_DAMAGE_BONUS);
        assertEquals(0.90, SkillFormulas.knightIncomingMultiplier(5), 1e-9);
        assertTrue(SkillFormulas.knightIncomingMultiplier(100) >= 1.0 - SkillFormulas.KNIGHT_MAX_RESIST);

        SiegeBreakPace pace = new SiegeBreakPace();
        assertFalse(pace.ready("obsidian", 3));
        assertFalse(pace.ready("obsidian", 3));
        assertFalse(pace.ready("obsidian", 3));
        assertTrue(pace.ready("obsidian", 3));
        assertTrue(pace.ready("dirt", 0));
    }

    @Test
    void skillChangeCostsACooldownAndRefusesCombat() {
        long cooldown = SkillFormulas.CHANGE_COOLDOWN_SECONDS;
        assertEquals(SkillChangeRules.Deny.NONE,
                SkillChangeRules.evaluate(false, false, false, 1_000L, 0L, 0));
        assertEquals(SkillChangeRules.Deny.COMBAT,
                SkillChangeRules.evaluate(true, false, false, 1_000L, 0L, 10_000));
        assertEquals(SkillChangeRules.Deny.SAME,
                SkillChangeRules.evaluate(false, true, true, 1_000L, 0L, 10_000));
        assertEquals(SkillChangeRules.Deny.COOLDOWN,
                SkillChangeRules.evaluate(false, true, false, 1_000L, 1_000L, SkillFormulas.CHANGE_COST));
        assertEquals(SkillChangeRules.Deny.NONE,
                SkillChangeRules.evaluate(false, true, false, 1_000L + cooldown, 1_000L, SkillFormulas.CHANGE_COST));
        assertEquals(SkillChangeRules.Deny.FUNDS,
                SkillChangeRules.evaluate(false, true, false, 1_000L + cooldown, 1_000L, SkillFormulas.CHANGE_COST - 1));
    }

    @Test
    void kothRewardIsCappedAndNotAOneShotCapture() {
        assertTrue(KothRules.CAPTURE_SECONDS >= 60);
        assertTrue(KothRules.REWARD_CANTOX <= 1_000);
        assertFalse(KothRules.rewardAllowed(KothRules.MAX_CAPTURES_PER_FACTION_PER_DAY, 10_000L, 0L));
        assertFalse(KothRules.rewardAllowed(0, 100L, 100L));
        assertTrue(KothRules.rewardAllowed(0, 100L + KothRules.REWARD_COOLDOWN_SECONDS, 100L));
        assertEquals(3, KothRules.leading(Map.of(3, 2, 4, 1)));
        assertNull(KothRules.leading(Map.of(3, 2, 4, 2)));
        assertNull(KothRules.leading(Map.of()));
        assertTrue(KothRules.inside(0, 0, 0));
        assertFalse(KothRules.inside(KothRules.ZONE_RADIUS + 1, 0, 0));
    }
}
