package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionBalanceRepository;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.skills.Skill;
import fr.cantale.plugin.skills.SkillFormulas;

import java.sql.Connection;
import java.sql.SQLException;

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
        return costForIndex(faction.getPowerPurchased()) * negociantMultiplier(faction);
    }

    private double negociantMultiplier(Faction faction) {
        if (faction == null || plugin.getSkillService() == null) {
            return 1.0;
        }
        return SkillFormulas.negociantCostMultiplier(
                plugin.getSkillService().count(faction, Skill.NEGOCIANT));
    }

    public Quote quote(Faction faction, int units) {
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        int n = Math.max(0, Math.min(units, max));
        double total = 0;
        int start = faction.getPowerPurchased();
        double multiplier = negociantMultiplier(faction);
        for (int i = 0; i < n; i++) {
            total += costForIndex(start + i) * multiplier;
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
            double c = costForIndex(start + i) * negociantMultiplier(faction);
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

        int powerBefore = faction.getPower();
        int purchasedBefore = faction.getPowerPurchased();
        double balanceBefore = faction.getBalance();
        int powerAfter = powerBefore + q.powerGranted();
        int purchasedAfter = purchasedBefore + q.units();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
            }
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (!FactionBalanceRepository.applyDelta(conn, faction.getId(), -q.totalCost())) {
                    conn.rollback();
                    return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
                }
                factionDAO.updatePowerPurchase(conn, faction.getId(), powerAfter, purchasedAfter);
                double updated = FactionBalanceRepository.readBalance(conn, faction.getId());
                faction.setPower(powerAfter);
                faction.setPowerPurchased(purchasedAfter);
                faction.setBalance(updated);
                conn.commit();
                committed = true;
            } catch (SQLException e) {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // la connexion sera rendue au pool
                    }
                    faction.setPower(powerBefore);
                    faction.setPowerPurchased(purchasedBefore);
                    faction.setBalance(balanceBefore);
                }
                plugin.getLogger().severe("Achat de pouvoir refusé : " + e.getMessage());
                return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Achat de pouvoir refusé : " + e.getMessage());
            return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
        }
        // Pas de saveFaction : il réécrirait balance avec la valeur absolue lue ici
        // et écraserait un dépôt concurrent plus récent.

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
