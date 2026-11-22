package fr.cantale.plugin.network;

import fr.cantale.plugin.cache.RedisChannels;
import fr.cantale.plugin.database.DailyClaimRepository;
import fr.cantale.plugin.web.AdminActionClaim;
import fr.cantale.plugin.web.RedisBridgeMessages;
import fr.cantale.plugin.web.RedisRelayPolicy;
import fr.cantale.plugin.world.KothRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPhaseFourTest {

    @Test
    void velocityStaysOffWithoutAUsableSecretAndRejectsAnUnforwardedProxy() {
        assertFalse(NetworkIdentity.velocityEnabled(null));
        assertFalse(NetworkIdentity.velocityEnabled(""));
        assertFalse(NetworkIdentity.velocityEnabled("${CANTALE_VELOCITY_SECRET}"));
        assertTrue(NetworkIdentity.velocityEnabled("local-forwarding-token"));
        assertEquals("paper-1", NetworkIdentity.serverId(null, null));
        assertEquals("paper-1", NetworkIdentity.serverId("${CANTALE_SERVER_ID}", "paper-1"));
        assertEquals("node2", NetworkIdentity.serverId("node 2!", null));
        assertEquals(VelocityForwarding.Decision.ALLOW, VelocityForwarding.decide(false, false));
        assertEquals(VelocityForwarding.Decision.REJECT, VelocityForwarding.decide(true, false));
        assertEquals(VelocityForwarding.Decision.ALLOW, VelocityForwarding.decide(true, true));
    }

    @Test
    void serverIdFiltersOwnEchoAndKeepsWebsiteChat() {
        String json = RedisBridgeMessages.outboundChat("mc", UUID.randomUUID().toString(), "Noa", "salut", null, "paper-2");
        assertEquals("paper-2", RedisBridgeMessages.parseChat(json).serverId());
        assertEquals(RedisRelayPolicy.Action.RELAY_CROSS_CHAT,
                RedisRelayPolicy.accept(RedisChannels.CHAT_MC_TO_WEB, "paper-2", "paper-1"));
        assertEquals(RedisRelayPolicy.Action.PUBLISH_ONLY,
                RedisRelayPolicy.accept(RedisChannels.CHAT_MC_TO_WEB, "paper-1", "paper-1"));
        assertEquals(RedisRelayPolicy.Action.PUBLISH_ONLY,
                RedisRelayPolicy.accept(RedisChannels.CHAT_MC_TO_WEB, null, "paper-1"));
        assertEquals(RedisRelayPolicy.Action.RELAY_CHAT,
                RedisRelayPolicy.accept(RedisChannels.CHAT_WEB_TO_MC, null, "paper-1"));
        assertEquals(RedisRelayPolicy.Action.IGNORE,
                RedisRelayPolicy.accept(RedisChannels.ADMIN_ACTIONS, "paper-1", "paper-1"));
        assertEquals(RedisRelayPolicy.Action.ADMIN,
                RedisRelayPolicy.accept(RedisChannels.ADMIN_ACTIONS, null, "paper-1"));
        assertFalse(RedisRelayPolicy.sqlPollingEnabled(true));
    }

    @Test
    void oneDailyClaimAndOneAdminActionWin() throws Exception {
        String url = "jdbc:sqlite:file:net-" + UUID.randomUUID() + "?mode=memory&cache=shared";
        try (Connection hold = DriverManager.getConnection(url)) {
            try (Statement st = hold.createStatement()) {
                st.execute("""
                        CREATE TABLE player_permissions (
                            uuid VARCHAR(36) PRIMARY KEY,
                            role VARCHAR(32) NOT NULL,
                            updated_at INTEGER,
                            last_cantox_claim VARCHAR(16)
                        )
                        """);
                st.execute("""
                        CREATE TABLE web_admin_actions (
                            id INTEGER PRIMARY KEY,
                            status VARCHAR(16) NOT NULL
                        )
                        """);
                st.execute("INSERT INTO web_admin_actions (id, status) VALUES (7, 'pending')");
                st.execute(KothRepository.createSql());
            }
            String uuid = UUID.randomUUID().toString();
            assertEquals(1, winners(url, 8, conn -> DailyClaimRepository.tryClaim(conn, true, uuid, "NONE", "2026-11-07", 1)));
            assertEquals(1, winners(url, 8, conn -> AdminActionClaim.claim(conn, 7)));
            try (Connection conn = DriverManager.getConnection(url)) {
                assertTrue(KothRepository.claimSlot(conn, 3, 100, 4, 1_000L, 2_700L));
                assertFalse(KothRepository.claimSlot(conn, 3, 100, 4, 1_100L, 2_700L));
            }
        }
    }

    private static int winners(String url, int threads, SqlCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger wins = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    try (Connection conn = DriverManager.getConnection(url)) {
                        try (Statement pragma = conn.createStatement()) {
                            pragma.execute("PRAGMA busy_timeout = 5000");
                        }
                        if (call.run(conn)) {
                            wins.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        return wins.get();
    }

    @FunctionalInterface
    private interface SqlCall {
        boolean run(Connection conn) throws Exception;
    }
}
