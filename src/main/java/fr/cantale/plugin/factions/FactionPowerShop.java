package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.economy.BankManager;

import java.util.UUID;

/**
 * Achat de pouvoir de faction via la banque, prix exponentiel.
 * cost(i) = floor(base * growth^i) où i = power_purchased.
 */
public final class FactionPowerShop {

    public enum BuyResult {
        OK,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        NO_FACTION
    }

    public record Quote(int points, double totalCost, double nextPointCost, int powerAfter, int claimsAfter) {}

    public record PurchaseOutcome(BuyResult result, Quote quote) {}

    private final CantalePlugin plugin;
    private final FactionDAO factionDAO;
    private final BankManager bankManager;

    public FactionPowerShop(CantalePlugin plugin, BankManager bankManager) {
        this.plugin = plugin;
        this.factionDAO = new FactionDAO(plugin);
        this.bankManager = bankManager;
    }

    public double costForIndex(int purchasedIndex) {
        double base = plugin.getConfigManager().getPowerBuyBase();
        double growth = plugin.getConfigManager().getPowerBuyGrowth();
        if (purchasedIndex < 0) purchasedIndex = 0;
        return Math.floor(base * Math.pow(growth, purchasedIndex));
    }

    public double nextPointCost(Faction faction) {
        return costForIndex(faction.getPowerPurchased());
    }

    public Quote quote(Faction faction, int points) {
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        int n = Math.max(0, Math.min(points, max));
        double total = 0;
        int start = faction.getPowerPurchased();
        for (int i = 0; i < n; i++) {
            total += costForIndex(start + i);
        }
        int powerAfter = faction.getPower() + n;
        int claimsAfter = Math.max(5, powerAfter / 10);
        return new Quote(n, total, costForIndex(start), powerAfter, claimsAfter);
    }

    public PurchaseOutcome buy(Faction faction, UUID actorUuid, int points) {
        if (faction == null) {
            return new PurchaseOutcome(BuyResult.NO_FACTION, null);
        }
        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        if (points < 1 || points > max) {
            return new PurchaseOutcome(BuyResult.INVALID_AMOUNT, quote(faction, Math.max(1, Math.min(points, max))));
        }

        Quote q = quote(faction, points);
        if (faction.getBalance() < q.totalCost()) {
            return new PurchaseOutcome(BuyResult.INSUFFICIENT_FUNDS, q);
        }

        faction.setBalance(faction.getBalance() - q.totalCost());
        faction.addPower(q.points());
        faction.addPowerPurchased(q.points());
        factionDAO.updatePowerPurchase(faction.getId(), faction.getPower(), faction.getPowerPurchased());
        plugin.getFactionManager().saveFaction(faction);

        bankManager.logTransaction(
                faction.getId(),
                actorUuid,
                "POWER_BUY",
                q.totalCost(),
                "Achat de " + q.points() + " pouvoir"
        );

        return new PurchaseOutcome(BuyResult.OK, q);
    }
}
