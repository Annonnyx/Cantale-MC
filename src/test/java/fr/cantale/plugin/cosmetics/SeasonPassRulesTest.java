package fr.cantale.plugin.cosmetics;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonPassRulesTest {

    @Test
    void tierFormulasArePureAndGrantNoGameplay() {
        assertEquals(0, SeasonPassRules.balanceReward());
        assertEquals(0, SeasonPassRules.livesReward());
        assertEquals(0, SeasonPassRules.powerReward());
        assertFalse(SeasonPassRules.grantsFlight());
        assertFalse(SeasonPassRules.grantsClaim());
        assertEquals(SeasonPassRules.Equip.LOCKED, SeasonPassRules.equip(true, false));
        assertEquals(SeasonPassRules.Equip.UNKNOWN, SeasonPassRules.equip(false, true));
        assertEquals(SeasonPassRules.Equip.OK, SeasonPassRules.equip(true, true));

        int previous = 0;
        for (int tier = 1; tier <= SeasonPassRules.MAX_TIER; tier++) {
            int required = SeasonPassRules.xpRequired(tier);
            assertTrue(required > previous);
            assertEquals(tier, SeasonPassRules.tierReached(required));
            assertEquals(tier - 1, SeasonPassRules.tierReached(required - 1));
            previous = required;
        }
        assertEquals(SeasonPassRules.MAX_TIER, SeasonPassRules.tierReached(Integer.MAX_VALUE));
        assertEquals(0, SeasonPassRules.tierReached(0));
    }

    @Test
    void lockedCosmeticCannotBeEquippedAndXpDoesNotTouchBalanceOrLives() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:file:pass-" + UUID.randomUUID() + "?mode=memory&cache=shared")) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE players (
                            uuid VARCHAR(36) PRIMARY KEY,
                            balance DOUBLE DEFAULT 0,
                            lives INTEGER DEFAULT 3
                        )
                        """);
                for (String sql : SeasonPassRepository.schema()) {
                    st.execute(sql);
                }
            }
            UUID uuid = UUID.randomUUID();
            try (var ps = conn.prepareStatement("INSERT INTO players (uuid, balance, lives) VALUES (?, 80, 2)")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            SeasonPassRepository.ensureSeason(conn, 1_700_000_000L);
            int xp = SeasonPassRepository.addXp(conn, true, uuid, SeasonPassRules.xpRequired(1));
            assertEquals(SeasonPassRules.xpRequired(1), xp);
            SeasonPassRepository.unlockThrough(conn, uuid, SeasonPassRules.tierReached(xp), 1_700_000_000L);

            CosmeticDefinition locked = CosmeticDefinition.byId("aura_ember");
            assertFalse(SeasonPassRepository.owns(conn, uuid, locked.id()));
            assertFalse(SeasonPassRepository.equip(conn, uuid, locked));

            CosmeticDefinition owned = CosmeticDefinition.byId("title_novice");
            assertTrue(SeasonPassRepository.owns(conn, uuid, owned.id()));
            assertTrue(SeasonPassRepository.equip(conn, uuid, owned));

            try (var ps = conn.prepareStatement("SELECT balance, lives FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(80, rs.getDouble("balance"), 0.001);
                    assertEquals(2, rs.getInt("lives"));
                }
            }
        }
    }
}
