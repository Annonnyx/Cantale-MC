package fr.cantale.plugin.auction;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingPurchaseTest {

    @Test
    void fiftyBuyersOfOneListingProduceASingleWinnerAndAConsistentBalance() throws Exception {
        UUID seller = UUID.randomUUID();
        MemoryStock stock = new MemoryStock(seller, 1, 10);
        MemoryLedger ledger = new MemoryLedger();
        ledger.set(seller, 0);

        int buyers = 50;
        UUID[] ids = new UUID[buyers];
        for (int i = 0; i < buyers; i++) {
            ids[i] = UUID.randomUUID();
            ledger.set(ids[i], 100);
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(buyers);
        AtomicInteger wins = new AtomicInteger();
        for (UUID buyer : ids) {
            pool.submit(() -> {
                try {
                    start.await();
                    ListingPurchase.Outcome outcome = ListingPurchase.tryPurchase(stock, stock, buyer, 1, ledger);
                    if (outcome.success()) {
                        wins.incrementAndGet();
                        assertEquals(1, outcome.quantity());
                        assertEquals(10, outcome.totalPrice(), 0.001);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, wins.get());
        assertEquals(1, stock.sales);
        assertEquals(0, stock.quantity);
        assertEquals(10, ledger.get(seller), 0.001);

        int broke = 0;
        double sum = ledger.get(seller);
        for (UUID buyer : ids) {
            double balance = ledger.get(buyer);
            sum += balance;
            if (balance == 90) {
                broke++;
            } else {
                assertEquals(100, balance, 0.001);
            }
        }
        assertEquals(1, broke);
        assertEquals(5000, sum, 0.001);
    }

    @Test
    void failedPersistenceRefundsBothSides() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        MemoryStock stock = new MemoryStock(seller, 1, 10);
        stock.failApply = true;
        MemoryLedger ledger = new MemoryLedger();
        ledger.set(seller, 0);
        ledger.set(buyer, 100);

        assertThrows(IllegalStateException.class,
                () -> ListingPurchase.tryPurchase(stock, stock, buyer, 1, ledger));

        assertEquals(1, stock.quantity);
        assertEquals(0, stock.sales);
        assertEquals(0, ledger.get(seller), 0.001);
        assertEquals(100, ledger.get(buyer), 0.001);
    }

    private static final class MemoryStock implements ListingPurchase.Stock {
        private final UUID seller;
        private int quantity;
        private final double price;
        private int sales;
        private boolean failApply;

        private MemoryStock(UUID seller, int quantity, double price) {
            this.seller = seller;
            this.quantity = quantity;
            this.price = price;
        }

        @Override
        public UUID sellerId() {
            return seller;
        }

        @Override
        public boolean isUnavailable() {
            return quantity <= 0;
        }

        @Override
        public int quantity() {
            return quantity;
        }

        @Override
        public double pricePerUnit() {
            return price;
        }

        @Override
        public void applySale(UUID buyerId, int quantitySold) {
            if (failApply) {
                throw new IllegalStateException("persistance refusée");
            }
            quantity -= quantitySold;
            sales++;
        }
    }

    private static final class MemoryLedger implements ListingPurchase.Ledger {
        private final Map<UUID, Double> balances = new HashMap<>();

        void set(UUID id, double amount) {
            balances.put(id, amount);
        }

        double get(UUID id) {
            return balances.getOrDefault(id, 0.0);
        }

        @Override
        public synchronized boolean tryDebit(UUID account, double amount) {
            double current = get(account);
            if (amount <= 0 || current < amount) {
                return false;
            }
            balances.put(account, current - amount);
            return true;
        }

        @Override
        public synchronized boolean tryCredit(UUID account, double amount) {
            if (amount <= 0) {
                return false;
            }
            balances.put(account, get(account) + amount);
            return true;
        }
    }
}
