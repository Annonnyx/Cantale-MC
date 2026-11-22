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

class FactionPowerRepositoryTest {

    @Test
    void debitIsArithmeticAndRefusesToGoNegative() throws Exception {
        String url = memoryUrl("power-debit");
        try (Connection hold = DriverManager.getConnection(url);
             Connection conn = DriverManager.getConnection(url)) {
            create(hold);
            insert(conn, 1, 20);
            assertFalse(FactionPowerRepository.debit(conn, 1, 21));
            assertEquals(20, FactionPowerRepository.readPower(conn, 1));
            assertTrue(FactionPowerRepository.debit(conn, 1, 15));
            assertEquals(5, FactionPowerRepository.readPower(conn, 1));
            assertTrue(FactionPowerRepository.credit(conn, 1, 15));
            assertEquals(20, FactionPowerRepository.readPower(conn, 1));
        }
    }

    @Test
    void concurrentRevivesCannotOverdrawPower() throws Exception {
        String url = memoryUrl("power-race");
        try (Connection hold = DriverManager.getConnection(url)) {
            create(hold);
            insert(hold, 4, 15);

            int threads = 20;
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
                            if (FactionPowerRepository.debit(conn, 4, 15)) {
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
            assertEquals(1, wins.get());
            assertEquals(0, FactionPowerRepository.readPower(hold, 4));
        }
    }

    private static String memoryUrl(String name) {
        return "jdbc:sqlite:file:" + name + "-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static void create(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE factions (id INTEGER PRIMARY KEY, power INTEGER NOT NULL)");
        }
    }

    private static void insert(Connection conn, int id, int power) throws Exception {
        try (var ps = conn.prepareStatement("INSERT INTO factions (id, power) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setInt(2, power);
            ps.executeUpdate();
        }
    }
}
