package fr.cantale.plugin.database;

import fr.cantale.plugin.lives.LifeRules;
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

class PlayerLifeRepositoryTest {

    @Test
    void spamGrantLeavesASingleLife() throws Exception {
        String url = "jdbc:sqlite:file:lives-" + UUID.randomUUID() + "?mode=memory&cache=shared";
        UUID player = UUID.randomUUID();
        try (Connection hold = DriverManager.getConnection(url)) {
            try (Statement st = hold.createStatement()) {
                st.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, lives INTEGER NOT NULL)");
            }
            try (var ps = hold.prepareStatement("INSERT INTO players (uuid, lives) VALUES (?, 0)")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }

            int threads = 25;
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
                            if (PlayerLifeRepository.grantIfDepleted(conn, player, LifeRules.LIVES_ON_RETURN)) {
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
            assertEquals(1, PlayerLifeRepository.readLives(hold, player));
        }
    }
}
