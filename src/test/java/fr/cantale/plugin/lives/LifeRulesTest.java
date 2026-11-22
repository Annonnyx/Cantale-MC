package fr.cantale.plugin.lives;

import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.FactionReviveRules;
import fr.cantale.plugin.listeners.DeathLootProtection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeRulesTest {

    @Test
    void zeroLivesIsATwoHourBanNotAPermanentIpBan() {
        assertEquals(Duration.ofHours(2), LifeRules.DEATHBAN);
        assertEquals(7_200L, LifeRules.deathbanSeconds());
        assertFalse(LifeRules.PERMANENT);
        assertFalse(LifeRules.BANS_IP);
        assertEquals(1, LifeRules.LIVES_ON_RETURN);

        long death = 1_700_000_000L;
        assertTrue(LifeRules.deathbanActive(death, death + 3_600));
        assertFalse(LifeRules.deathbanActive(death, death + LifeRules.deathbanSeconds()));
        assertFalse(LifeRules.shouldBlockLogin(1, death, death + 10));
        assertTrue(LifeRules.shouldBlockLogin(0, death, death + 10));
        assertFalse(LifeRules.shouldBlockLogin(0, death, death + 7_200));
        assertEquals(1, LifeRules.livesAfterExpiredBan(0, death, death + 7_200));
        assertEquals(0, LifeRules.livesAfterExpiredBan(0, death, death + 10));
    }

    @Test
    void reviveCostsFactionPowerOnceAndDoesNotDuplicateLives() {
        assertEquals(15, FactionReviveRules.POWER_COST);
        assertTrue(FactionReviveRules.canOrderRevive(FactionRank.OFFICER));
        assertTrue(FactionReviveRules.canOrderRevive(FactionRank.LEADER));
        assertFalse(FactionReviveRules.canOrderRevive(FactionRank.VETERAN));
        assertFalse(FactionReviveRules.canOrderRevive(FactionRank.MEMBER));

        assertNull(FactionReviveRules.evaluate(FactionRank.OFFICER, true, 0, true, 15));
        assertEquals(FactionReviveRules.Deny.INSUFFICIENT_POWER,
                FactionReviveRules.evaluate(FactionRank.LEADER, true, 0, true, 14));
        assertEquals(FactionReviveRules.Deny.NOT_OFFICER,
                FactionReviveRules.evaluate(FactionRank.VETERAN, true, 0, true, 100));
        assertEquals(FactionReviveRules.Deny.NOT_SAME_FACTION,
                FactionReviveRules.evaluate(FactionRank.OFFICER, false, 0, true, 100));
        assertEquals(FactionReviveRules.Deny.ALREADY_HAS_LIVES,
                FactionReviveRules.evaluate(FactionRank.OFFICER, true, 1, true, 100));
        assertEquals(FactionReviveRules.Deny.NOT_DEATHBANNED,
                FactionReviveRules.evaluate(FactionRank.OFFICER, true, 0, false, 100));

        assertEquals(1, FactionReviveRules.livesAfterRevive(0));
        assertEquals(1, FactionReviveRules.livesAfterRevive(1));
        assertEquals(3, FactionReviveRules.livesAfterRevive(3));
    }

    @Test
    void deathLootIsReservedForFiveMinutes() {
        assertEquals(300_000L, DeathLootProtection.DURATION_MS);
        long now = 10_000L;
        long until = DeathLootProtection.until(now);
        assertEquals(now + 300_000L, until);
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertFalse(DeathLootProtection.blocksPickup(owner, owner, until, now + 1_000));
        assertTrue(DeathLootProtection.blocksPickup(owner, other, until, now + 1_000));
        assertFalse(DeathLootProtection.blocksPickup(owner, other, until, now + 300_001));
        assertFalse(DeathLootProtection.blocksPickup(null, other, until, now));
    }
}
