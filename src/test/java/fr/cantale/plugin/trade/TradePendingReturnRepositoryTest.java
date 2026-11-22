package fr.cantale.plugin.trade;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradePendingReturnRepositoryTest {

    @Test
    void pendingItemsSurviveANewConnectionUntilDeleted() throws Exception {
        String url = "jdbc:sqlite:file:trade-" + UUID.randomUUID() + "?mode=memory&cache=shared";
        UUID player = UUID.randomUUID();
        try (Connection hold = DriverManager.getConnection(url)) {
            try (Statement st = hold.createStatement()) {
                st.execute(TradePendingReturnRepository.createTableSql(true));
                st.execute(TradePendingReturnRepository.CREATE_INDEX_SQL);
                st.execute(TradePendingReturnRepository.createTableSql(true));
            }
            long id;
            try (Connection writer = DriverManager.getConnection(url)) {
                id = TradePendingReturnRepository.insert(writer, player, "encoded-item", 1_700_000_000L);
            }
            assertTrue(id > 0);

            try (Connection reader = DriverManager.getConnection(url)) {
                List<TradePendingReturnRepository.Row> rows = TradePendingReturnRepository.loadByPlayer(reader, player);
                assertEquals(1, rows.size());
                assertEquals("encoded-item", rows.get(0).itemData());
                assertEquals(player, rows.get(0).playerId());
                TradePendingReturnRepository.delete(reader, id);
                assertTrue(TradePendingReturnRepository.loadAll(reader).isEmpty());
            }
        }
    }

    @Test
    void crashAfterTheGiveDoesNotDeliverTheSameReturnTwice() throws Exception {
        String url = memoryUrl("trade-ack");
        UUID player = UUID.randomUUID();
        try (Connection hold = DriverManager.getConnection(url)) {
            prepare(hold);
            long id = TradePendingReturnRepository.insert(hold, player, "encoded-item", 1L);

            assertEquals(TradePendingReturnRepository.Delivery.GIVE,
                    TradePendingReturnRepository.resolve(hold, id, "owner", false));
            assertEquals(TradePendingReturnRepository.Delivery.SKIP,
                    TradePendingReturnRepository.resolve(hold, id, "rival", false));

            assertTrue(TradePendingReturnRepository.acknowledge(hold, id));
            assertEquals(TradePendingReturnRepository.Delivery.SKIP,
                    TradePendingReturnRepository.resolve(hold, id, "restart", false));
            assertTrue(TradePendingReturnRepository.loadAll(hold).isEmpty());
        }
    }

    @Test
    void witnessedGiveIsNotRepeatedAndAFailedGiveStaysRetryable() throws Exception {
        String url = memoryUrl("trade-witness");
        UUID player = UUID.randomUUID();
        try (Connection hold = DriverManager.getConnection(url)) {
            prepare(hold);
            long given = TradePendingReturnRepository.insert(hold, player, "already-in-inventory", 1L);
            assertEquals(TradePendingReturnRepository.Delivery.GIVE,
                    TradePendingReturnRepository.resolve(hold, given, "owner", false));
            assertEquals(TradePendingReturnRepository.Delivery.SKIP,
                    TradePendingReturnRepository.resolve(hold, given, "restart", true));
            assertTrue(TradePendingReturnRepository.loadByPlayer(hold, player).isEmpty());

            long missed = TradePendingReturnRepository.insert(hold, player, "not-given", 2L);
            assertEquals(TradePendingReturnRepository.Delivery.GIVE,
                    TradePendingReturnRepository.resolve(hold, missed, "owner", false));
            assertEquals(1, TradePendingReturnRepository.abandonOpenClaims(hold));
            assertEquals(TradePendingReturnRepository.Delivery.GIVE,
                    TradePendingReturnRepository.resolve(hold, missed, "retry", false));
            assertTrue(TradePendingReturnRepository.release(hold, missed, "retry"));
            List<TradePendingReturnRepository.Row> kept = TradePendingReturnRepository.loadByPlayer(hold, player);
            assertEquals(1, kept.size());
            assertEquals(TradePendingReturnRepository.STATUS_PENDING, kept.get(0).status());
            assertFalse(kept.get(0).handedOff());
        }
    }

    private static String memoryUrl(String name) {
        return "jdbc:sqlite:file:" + name + "-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static void prepare(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(TradePendingReturnRepository.createTableSql(true));
            st.execute(TradePendingReturnRepository.CREATE_INDEX_SQL);
        }
    }
}
