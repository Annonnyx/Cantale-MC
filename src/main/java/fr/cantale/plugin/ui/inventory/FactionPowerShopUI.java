package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionPowerShop;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * GUI pouvoir de faction : quotas, paliers, et achat via banque (1 / 2 / 3 / 5 / max unités).
 * Chaque unité = +power-per-unit pouvoir (défaut ×10). Ouvert par /f power, /f bank power, menu /f et banque.
 */
public class FactionPowerShopUI extends BaseUI {

    private static final int[] BUY_AMOUNTS = {1, 2, 3, 5};
    private static final int[] BUY_SLOTS = {29, 30, 31, 32};
    private static final int SLOT_MAX = 33;
    private static final int SLOT_INFO = 4;

    private final Faction faction;
    private final FactionPowerShop powerShop;

    public FactionPowerShopUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Pouvoir — " + faction.getName(), 6);
        this.faction = faction;
        this.powerShop = new FactionPowerShop(plugin, new BankManager(plugin));
    }

    private boolean canBuy() {
        return faction.isLeader(player.getUniqueId())
                || faction.isOfficier(player.getUniqueId())
                || plugin.getFactionManager().hasPermission(player.getUniqueId(), FactionPermission.BANK_TAKE);
    }

    @Override
    protected void init() {
        fillEmpty();

        int power = faction.getPower();
        double balance = faction.getBalance();
        double next = powerShop.nextPointCost(faction);
        int maxAffordable = powerShop.maxAffordable(faction);
        int maxPer = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        int perUnit = powerShop.powerPerUnit();

        int hopperMax = plugin.getConfigManager().getMaxHoppersForPower(power);
        int hopperDivisor = plugin.getConfigManager().getHopperLimitDivisor();

        setItem(SLOT_INFO, Material.NETHER_STAR, "§b§lPouvoir de faction",
                "§7Pouvoir actuel : §b" + power,
                "§7Claims : §e" + faction.getClaimCount() + "§7/§e" + faction.getMaxClaims(),
                "§7F-homes max / joueur : §e" + faction.getMaxFhomes(),
                "§7Warps max : §e" + faction.getMaxWarps(),
                "§7Hoppers max (claims) : §e" + hopperMax,
                "§7Solde banque : §6" + FactionPowerShop.formatMoney(balance) + " §7Cantox",
                "§7Prochaine unité : §6" + FactionPowerShop.formatMoney(next) + " §7Cantox",
                "§7→ §b+" + perUnit + " §7pouvoir",
                "§8Unités déjà achetées : §7" + faction.getPowerPurchased());

        setItem(11, Material.GRASS_BLOCK, "§a§lClaims",
                "§7Formule : §emax(5, pouvoir / 10)",
                "§7Quota actuel : §e" + faction.getMaxClaims() + " §7chunks",
                "§7Utilisés : §e" + faction.getClaimCount(),
                "§7Prochain slot à §b" + faction.powerRequiredForNextClaimSlot() + " §7pouvoir",
                "§7→ encore §b" + faction.powerUntilNextClaimSlot() + " §7points");

        setItem(13, Material.RED_BED, "§e§lF-homes",
                "§7Formule : §emax(1, pouvoir / 20)",
                "§7Quota actuel : §e" + faction.getMaxFhomes() + " §7par joueur",
                "§7Prochain palier à §b" + faction.powerRequiredForNextFhome() + " §7pouvoir",
                "§7→ encore §b" + faction.powerUntilNextFhome() + " §7points");

        setItem(15, Material.ENDER_PEARL, "§d§lWarps de faction",
                "§7Formule : §e1 si pouvoir < 60",
                "§7sinon §e1 + (pouvoir − 60) / 30",
                "§7Quota actuel : §e" + faction.getMaxWarps(),
                "§7Prochain warp à §b" + faction.powerRequiredForNextWarp() + " §7pouvoir",
                "§7→ encore §b" + faction.powerUntilNextWarp() + " §7points");

        setItem(20, Material.HOPPER, "§6§lHoppers (anti-lag)",
                "§7Formule : §emax(0, pouvoir / " + hopperDivisor + ")",
                "§7Quota actuel : §e" + hopperMax + " §7dans les claims de la fac",
                "§7Craft vanilla interdit — achat §e/shop §7uniquement",
                "§7Pose hors claim de fac refusée",
                "§8Les hoppers déjà posés ne sont pas retirés");

        setItem(22, Material.PAPER, "§6§lÀ quoi sert le pouvoir ?",
                "§7Plus de pouvoir = plus de territoire,",
                "§7TP (homes / warps) et hoppers.",
                "§7Sources : membres, PvP, events,",
                "§7achat banque (/f bank power).",
                "§eAcheter 1 → +" + perUnit + " pouvoir");

        if (canBuy()) {
            setItem(28, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§l▼ ACHETER",
                    "§7Débite la banque de faction",
                    "§7Officiers et chef uniquement",
                    "§e1 unité = +" + perUnit + " pouvoir");
            for (int i = 0; i < BUY_AMOUNTS.length; i++) {
                placeBuyButton(BUY_SLOTS[i], BUY_AMOUNTS[i], balance, perUnit);
            }
            placeMaxButton(maxAffordable, maxPer, balance, perUnit);
        } else {
            setItem(31, Material.BARRIER, "§cAchat réservé",
                    "§7Consulte les quotas librement.",
                    "§7Seuls les officiers et le chef",
                    "§7peuvent acheter du pouvoir.",
                    "§eAcheter 1 → +" + perUnit + " pouvoir");
        }

        setItem(40, Material.BOOK, "§eFormule de prix d'achat",
                "§7cost = floor(base × growth^(unités × 10))",
                "§7Base : §6" + FactionPowerShop.formatMoney(plugin.getConfigManager().getPowerBuyBase()),
                "§7Croissance : §e" + plugin.getConfigManager().getPowerBuyGrowth(),
                "§7Max par achat : §e" + maxPer + " §7unités",
                "§a1 unité → +" + perUnit + " pouvoir",
                "§8Aussi : §7/f bank power <n>");

        addNavigationBar(true, true);
    }

    private void placeBuyButton(int slot, int units, double balance, int perUnit) {
        FactionPowerShop.Quote q = powerShop.quote(faction, units);
        boolean affordable = q.units() == units && balance >= q.totalCost();
        Material mat = affordable ? Material.LIGHT_BLUE_DYE : Material.GRAY_DYE;
        String name = (affordable ? "§b§l" : "§8§l") + "Acheter " + units
                + " → +" + q.powerGranted() + " pouvoir";
        if (!affordable) {
            setItem(slot, mat, name,
                    "§7Coût : §6" + FactionPowerShop.formatMoney(q.totalCost()) + " §7Cantox",
                    "§7Gain : §b+" + q.powerGranted() + " §7pouvoir (§e" + units
                            + " × " + perUnit + "§7)",
                    "§cBanque insuffisante",
                    "§7Solde : §6" + FactionPowerShop.formatMoney(balance));
            return;
        }
        setItem(slot, mat, name,
                "§7Coût total : §6" + FactionPowerShop.formatMoney(q.totalCost()) + " §7Cantox",
                "§7Gain : §b+" + q.powerGranted() + " §7pouvoir (§e" + units
                        + " × " + perUnit + "§7)",
                "§7Pouvoir après : §b" + q.powerAfter(),
                "§7Claims max après : §e" + q.claimsAfter(),
                "§aCliquez pour confirmer");
    }

    private void placeMaxButton(int maxAffordable, int maxPer, double balance, int perUnit) {
        if (maxAffordable < 1) {
            setItem(SLOT_MAX, Material.GRAY_DYE, "§8§lAcheter le maximum",
                    "§cImpossible : banque insuffisante",
                    "§7Prochaine unité : §6" + FactionPowerShop.formatMoney(powerShop.nextPointCost(faction)),
                    "§7→ §b+" + perUnit + " §7pouvoir",
                    "§7Solde : §6" + FactionPowerShop.formatMoney(balance),
                    "§8Plafond : §7" + maxPer + " unités / achat");
            return;
        }
        FactionPowerShop.Quote q = powerShop.quote(faction, maxAffordable);
        setItem(SLOT_MAX, Material.AMETHYST_SHARD,
                "§d§lAcheter le max §7(§d" + maxAffordable + " → +" + q.powerGranted() + "§7)",
                "§7Max abordable : §e" + maxAffordable + " §7/ §e" + maxPer + " §7unités",
                "§7Gain : §b+" + q.powerGranted() + " §7pouvoir",
                "§7Coût total : §6" + FactionPowerShop.formatMoney(q.totalCost()) + " §7Cantox",
                "§7Pouvoir après : §b" + q.powerAfter(),
                "§7Claims max après : §e" + q.claimsAfter(),
                "§aCliquez pour confirmer");
    }

    @Override
    protected void handleClick(int slot) {
        if (!canBuy()) return;

        int units = -1;
        for (int i = 0; i < BUY_SLOTS.length; i++) {
            if (slot == BUY_SLOTS[i]) {
                units = BUY_AMOUNTS[i];
                break;
            }
        }
        if (slot == SLOT_MAX) {
            units = powerShop.maxAffordable(faction);
            if (units < 1) {
                player.sendMessage("§8[§6Cantale§8] §cBanque insuffisante pour acheter du pouvoir.");
                return;
            }
        }
        if (units < 1) return;

        FactionPowerShop.Quote preview = powerShop.quote(faction, units);
        if (faction.getBalance() < preview.totalCost()) {
            player.sendMessage("§8[§6Cantale§8] §cBanque insuffisante. Coût : §6"
                    + FactionPowerShop.formatMoney(preview.totalCost()) + " §cCantox.");
            init();
            return;
        }

        int finalUnits = units;
        plugin.getUIManager().getNavigationManager().openUI(player,
                new ConfirmDialogUI(plugin, player,
                        "§7Confirmer l'achat",
                        "Acheter " + finalUnits + " → +" + preview.powerGranted()
                                + " pouvoir pour "
                                + FactionPowerShop.formatMoney(preview.totalCost()) + " Cantox ?",
                        confirmed -> {
                            if (!confirmed) {
                                plugin.getUIManager().goBack(player);
                                return;
                            }
                            executeBuy(finalUnits);
                            plugin.getUIManager().goBack(player);
                        }));
    }

    private void executeBuy(int units) {
        FactionPowerShop.PurchaseOutcome outcome = powerShop.buy(faction, player.getUniqueId(), units);
        FactionPowerShop.Quote q = outcome.quote();

        switch (outcome.result()) {
            case OK -> {
                player.sendMessage("§8[§6Cantale§8] §aAchat réussi : §b+" + q.powerGranted()
                        + " pouvoir §7(§e" + q.units() + " §7unité"
                        + (q.units() > 1 ? "s" : "") + "§7) §apour §6"
                        + FactionPowerShop.formatMoney(q.totalCost()) + " §aCantox.");
                player.sendMessage("§7Nouveau pouvoir : §b" + faction.getPower()
                        + " §7· claims max : §e" + faction.getMaxClaims()
                        + " §7· f-homes : §e" + faction.getMaxFhomes()
                        + " §7· warps : §e" + faction.getMaxWarps()
                        + " §7· hoppers : §e" + plugin.getConfigManager().getMaxHoppersForPower(faction.getPower())
                        + " §7· prochaine unité : §6"
                        + FactionPowerShop.formatMoney(powerShop.nextPointCost(faction)));
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage("§8[§6Cantale§8] §cBanque insuffisante. Coût : §6"
                    + FactionPowerShop.formatMoney(q.totalCost()) + " §cCantox (solde : §6"
                    + FactionPowerShop.formatMoney(faction.getBalance()) + "§c).");
            case INVALID_AMOUNT -> player.sendMessage("§8[§6Cantale§8] §cNombre invalide.");
            case NO_FACTION -> player.sendMessage("§8[§6Cantale§8] §cFaction introuvable.");
        }
    }
}
