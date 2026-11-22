package fr.cantale.plugin.database;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerBalanceRepositoryTest {

    @Test
    void poolSettingsStayInRange() {
        HikariConfig mysql = new HikariConfig();
        DatabasePoolSettings.apply(mysql, 10, false);
        assertEquals(10, mysql.getMaximumPoolSize());
        assertEquals(10, mysql.getMinimumIdle());
        assertEquals(DatabasePoolSettings.LEAK_DETECTION_MS, mysql.getLeakDetectionThreshold());
        assertEquals(DatabasePoolSettings.MAX_LIFETIME_MS, mysql.getMaxLifetime());
        assertEquals(DatabasePoolSettings.CONNECTION_TIMEOUT_MS, mysql.getConnectionTimeout());

        HikariConfig clamped = new HikariConfig();
        DatabasePoolSettings.apply(clamped, 100, false);
        assertEquals(20, clamped.getMaximumPoolSize());

        HikariConfig sqlite = new HikariConfig();
        DatabasePoolSettings.apply(sqlite, 10, true);
        assertEquals(1, sqlite.getMaximumPoolSize());
    }

    @Test
    void rejectsNegativeBalanceAndKeepsTheRow() throws Exception {
        try (Connection hold = open();
             Connection conn = open()) {
            createPlayers(conn);
            UUID uuid = UUID.randomUUID();
            insert(conn, uuid, 5);
            assertFalse(PlayerBalanceRepository.applyDelta(conn, uuid, -10));
            assertEquals(5, balance(conn, uuid), 0.001);
            assertTrue(PlayerBalanceRepository.applyDelta(conn, uuid, 2.5));
            assertEquals(7.5, balance(conn, uuid), 0.001);
        }
    }

    @Test
    void fiftyConcurrentDebitsCannotOverdraw() throws Exception {
        String url = "jdbc:sqlite:file:balance-" + UUID.randomUUID() + "?mode=memory&cache=shared";
        try (Connection hold = DriverManager.getConnection(url)) {
            createPlayers(hold);
            UUID uuid = UUID.randomUUID();
            insert(hold, uuid, 10);

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
                            if (PlayerBalanceRepository.applyDelta(conn, uuid, -1)) {
                                wins.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failure.compareAndSet(null, e);
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
            assertEquals(0, balance(hold, uuid), 0.001);
        }
    }

    private static Connection open() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:file:balance-single-" + UUID.randomUUID() + "?mode=memory&cache=shared");
        try (Statement pragma = conn.createStatement()) {
            pragma.execute("PRAGMA busy_timeout = 5000");
        }
        return conn;
    }

    private static void createPlayers(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        balance REAL NOT NULL,
                        balance_peak REAL
                    )
                    """);
        }
    }

    private static void insert(Connection conn, UUID uuid, double balance) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid, balance, balance_peak) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, balance);
            ps.setDouble(3, balance);
            ps.executeUpdate();
        }
    }

    private static double balance(Connection conn, UUID uuid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getDouble(1);
            }
        }
    }
}
