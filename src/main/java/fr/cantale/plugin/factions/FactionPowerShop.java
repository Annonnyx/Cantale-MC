package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.economy.BankManager;

import java.util.UUID;

/**
 * Achat de pouvoir de faction via la banque, prix exponentiel.
 * cost(i) = floor(base * growth^(i * {@link #PURCHASED_INDEX_SCALE})) où i = power_purchased
 * (chaque unité achetée compte ×10 dans l'exposant).
 * Chaque unité achetée octroie {@link #DEFAULT_POWER_PER_UNIT} pouvoir (config {@code power-per-unit}),
 * tandis que {@code power_purchased} n'incrémente que du nombre d'unités (échelle de prix).
 */
public final class FactionPowerShop {

    /** Facteur d'escalade : 1 unité achetée ≡ 10 dans l'indice de prix. */
    public static final int PURCHASED_INDEX_SCALE = 10;

    /** Défaut : 1 unité achetée → +10 pouvoir. */
    public static final int DEFAULT_POWER_PER_UNIT = 10;

    public enum BuyResult {
        OK,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        NO_FACTION
    }

    /**
     * @param units         unités achetées (échelle de prix / power_purchased)
     * @param powerGranted  pouvoir réellement ajouté (units × power-per-unit)
     */
    public record Quote(int units, int powerGranted, double totalCost, double nextPointCost,
                        int powerAfter, int claimsAfter) {
        /** Alias historique : nombre d'unités achetées. */
        public int points() {
            return units;
        }
    }

    public record PurchaseOutcome(BuyResult result, Quote quote) {}

    private final CantalePlugin plugin;
    private final FactionDAO factionDAO;
    private final BankManager bankManager;

    public FactionPowerShop(CantalePlugin plugin, BankManager bankManager) {
        this.plugin = plugin;
        this.factionDAO = new FactionDAO(plugin);
        this.bankManager = bankManager;
    }

    public int powerPerUnit() {
        return plugin.getConfigManager().getPowerBuyPowerPerUnit();
    }

    public int powerGrantedFor(int units) {
        return Math.max(0, units) * powerPerUnit();
    }

    public double costForIndex(int purchasedIndex) {
        double base = plugin.getConfigManager().getPowerBuyBase();
        double growth = plugin.getConfigManager().getPowerBuyGrowth();
        if (purchasedIndex < 0) purchasedIndex = 0;
        return Math.floor(base * Math.pow(growth, (double) purchasedIndex * PURCHASED_INDEX_SCALE));
    }

    public double nextPointCost(Faction faction) {
        return costForIndex(faction.getPowerPurchased());
    }

    public Quote quote(Faction faction, int units) {
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        int n = Math.max(0, Math.min(units, max));
        double total = 0;
        int start = faction.getPowerPurchased();
        for (int i = 0; i < n; i++) {
            total += costForIndex(start + i);
        }
        int granted = powerGrantedFor(n);
        int powerAfter = faction.getPower() + granted;
        int claimsAfter = Math.max(5, powerAfter / 10);
        return new Quote(n, granted, total, costForIndex(start), powerAfter, claimsAfter);
    }

    /**
     * Nombre max d'unités achetable d'un coup : min(max-per-purchase, ce que la banque peut payer).
     */
    public int maxAffordable(Faction faction) {
        if (faction == null) return 0;
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        double balance = faction.getBalance();
        int start = faction.getPowerPurchased();
        double spent = 0;
        int n = 0;
        for (int i = 0; i < max; i++) {
            double c = costForIndex(start + i);
            if (spent + c > balance) break;
            spent += c;
            n++;
        }
        return n;
    }

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 10_000) {
            return String.format("%.0fk", amount / 1000.0);
        }
        if (Math.floor(amount) == amount) {
            return String.format("%.0f", amount);
        }
        return String.format("%.2f", amount);
    }

    public PurchaseOutcome buy(Faction faction, UUID actorUuid, int units) {
        if (faction == null) {
            return new PurchaseOutcome(BuyResult.NO_FACTION, null);
        }
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        if (units < 1 || units > max) {
            return new PurchaseOutcome(BuyResult.INVALID_AMOUNT, quote(faction, Math.max(1, Math.min(units, max))));
        }

        Quote q = quote(faction, units);
        if (faction.getBalance() < q.totalCost()) {
            return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
        }

        faction.setBalance(faction.getBalance() - q.totalCost());
        faction.addPower(q.powerGranted());
        faction.addPowerPurchased(q.units());
        factionDAO.updatePowerPurchase(faction.getId(), faction.getPower(), faction.getPowerPurchased());
        plugin.getFactionManager().saveFaction(faction);

        bankManager.logTransaction(
                faction.getId(),
                actorUuid,
                BankManager.ACTION_POWER_BUY,
                q.totalCost(),
                faction.getBalance(),
                "Achat de " + q.units() + " unité(s) (+" + q.powerGranted() + " pouvoir)"
        );

        return new PurchaseOutcome(BuyResult.OK, q);
    }
}
