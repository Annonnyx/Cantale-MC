package fr.cantale.plugin.database;

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

class FactionBalanceRepositoryTest {

    @Test
    void rejectsNegativeBalanceAndCountsTheRow() throws Exception {
        String url = memoryUrl();
        try (Connection hold = DriverManager.getConnection(url);
             Connection conn = DriverManager.getConnection(url)) {
            create(hold);
            insert(conn, 1, 100);
            assertFalse(FactionBalanceRepository.applyDelta(conn, 1, -150));
            assertEquals(100, FactionBalanceRepository.readBalance(conn, 1), 0.001);
            assertTrue(FactionBalanceRepository.applyDelta(conn, 1, -40));
            assertEquals(60, FactionBalanceRepository.readBalance(conn, 1), 0.001);
            assertTrue(FactionBalanceRepository.applyDelta(conn, 1, 15));
            assertEquals(75, FactionBalanceRepository.readBalance(conn, 1), 0.001);
        }
    }

    @Test
    void concurrentDebitsCannotOverdraw() throws Exception {
        String url = memoryUrl();
        try (Connection hold = DriverManager.getConnection(url)) {
            create(hold);
            insert(hold, 7, 10);

            int threads = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger wins = new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        try (Connection conn = DriverManager.getConnection(url)) {
                            try (Statement pragma = conn.createStatement()) {
                                pragma.execute("PRAGMA busy_timeout = 5000");
                            }
                            if (FactionBalanceRepository.applyDelta(conn, 7, -1)) {
                                wins.incrementAndGet();
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
            pool.shutdown();
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
            assertEquals(10, wins.get());
            assertEquals(0, FactionBalanceRepository.readBalance(hold, 7), 0.001);
        }
    }

    private static String memoryUrl() {
        return "jdbc:sqlite:file:faction-balance-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static void create(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE factions (id INTEGER PRIMARY KEY, balance REAL NOT NULL)");
        }
    }

    private static void insert(Connection conn, int id, double balance) throws Exception {
        try (var ps = conn.prepareStatement("INSERT INTO factions (id, balance) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setDouble(2, balance);
            ps.executeUpdate();
        }
    }
}
