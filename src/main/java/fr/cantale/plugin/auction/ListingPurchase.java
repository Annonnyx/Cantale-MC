package fr.cantale.plugin.auction;

import java.util.UUID;

/**
 * Achat d'un listing sous verrou. Un seul appelant à la fois peut débiter, créditer et réserver le stock.
 */
public final class ListingPurchase {

    private ListingPurchase() {
    }

    public enum Status {
        PURCHASED,
        UNAVAILABLE,
        OWN_LISTING,
        INVALID_QUANTITY,
        INSUFFICIENT_FUNDS
    }

    public record Outcome(Status status, int quantity, double totalPrice) {
        public static Outcome of(Status status) {
            return new Outcome(status, 0, 0);
        }

        public boolean success() {
            return status == Status.PURCHASED;
        }
    }

    public interface Stock {
        UUID sellerId();

        boolean isUnavailable();

        int quantity();

        double pricePerUnit();

        void applySale(UUID buyerId, int quantitySold);
    }

    public interface Ledger {
        /** Retire {@code amount} (> 0). {@code false} si le solde deviendrait négatif. */
        boolean tryDebit(UUID account, double amount);

        /** Ajoute {@code amount} (> 0). */
        boolean tryCredit(UUID account, double amount);
    }

    public static Outcome tryPurchase(Object lock, Stock stock, UUID buyerId, int requested, Ledger ledger) {
        if (lock == null || stock == null || buyerId == null || ledger == null) {
            return Outcome.of(Status.UNAVAILABLE);
        }
        synchronized (lock) {
            if (stock.isUnavailable()) {
                return Outcome.of(Status.UNAVAILABLE);
            }
            if (buyerId.equals(stock.sellerId())) {
                return Outcome.of(Status.OWN_LISTING);
            }
            int available = stock.quantity();
            int qty = Math.min(requested, available);
            if (qty <= 0) {
                return Outcome.of(Status.INVALID_QUANTITY);
            }
            double total = stock.pricePerUnit() * qty;
            if (!(total > 0) || Double.isNaN(total) || Double.isInfinite(total)) {
                return Outcome.of(Status.INVALID_QUANTITY);
            }
            if (!ledger.tryDebit(buyerId, total)) {
                return Outcome.of(Status.INSUFFICIENT_FUNDS);
            }
            if (!ledger.tryCredit(stock.sellerId(), total)) {
                ledger.tryCredit(buyerId, total);
                return Outcome.of(Status.INSUFFICIENT_FUNDS);
            }
            try {
                stock.applySale(buyerId, qty);
            } catch (RuntimeException failure) {
                if (ledger.tryDebit(stock.sellerId(), total)) {
                    ledger.tryCredit(buyerId, total);
                }
                throw failure;
            }
            return new Outcome(Status.PURCHASED, qty, total);
        }
    }
}
