package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionPowerShop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Historique paginé des mouvements de la banque de faction.
 * Ouvert depuis {@link FactionBankUI} (bouton Historique).
 */
public class FactionBankHistoryUI extends BaseUI {

    private static final int PAGE_SIZE = 28; // slots 9..36
    private static final int GRID_START = 9;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
                    .withZone(ZoneId.of("Europe/Paris"));

    private final Faction faction;
    private final BankManager bankManager;
    /** Mutable: pagination sans rouvrir d'inventaire (évite les clics morts). */
    private int page;

    public FactionBankHistoryUI(CantalePlugin plugin, Player player, Faction faction) {
        this(plugin, player, faction, 0);
    }

    public FactionBankHistoryUI(CantalePlugin plugin, Player player, Faction faction, int page) {
        super(plugin, player, "§8[§6Cantale§8] §7Historique — " + faction.getName(), 6);
        this.faction = faction;
        this.bankManager = new BankManager(plugin);
        this.page = Math.max(0, page);
    }

    private void setPage(int newPage) {
        int total = bankManager.countHistory(faction.getId());
        int maxPage = total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
        if (newPage < 0 || newPage > maxPage || newPage == page) return;
        this.page = newPage;
        init();
    }

    @Override
    protected void init() {
        inventory.clear();
        fillEmpty();

        int total = bankManager.countHistory(faction.getId());
        int maxPage = total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
        if (page > maxPage) page = maxPage;

        setItem(4, Material.BOOK, "§e§lHistorique banque",
                "§7Faction : §6" + faction.getName(),
                "§7Solde actuel : §6" + FactionPowerShop.formatMoney(faction.getBalance()) + " §7Cantox",
                "§7Entrées : §e" + total,
                total > 0 ? "§7Page §e" + (page + 1) + "§7/§e" + (maxPage + 1) : "§8Aucune transaction");

        List<BankManager.BankTransaction> entries =
                bankManager.getHistory(faction.getId(), PAGE_SIZE, page * PAGE_SIZE);

        if (entries.isEmpty()) {
            setItem(22, Material.BARRIER, "§cAucune transaction",
                    "§7Les dépôts, retraits et",
                    "§7achats de pouvoir apparaîtront ici.");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                setItem(GRID_START + i, buildEntryItem(entries.get(i)));
            }
        }

        setItem(size - 9, Material.ARROW, "§7§l← Retour", "§7Revenir à la banque");
        if (page > 0) {
            setItem(size - 6, Material.PAPER, "§e§l← Page précédente",
                    "§7Page " + page + " / " + (maxPage + 1));
        }
        setItem(size - 5, Material.BARRIER, "§c§l✕ Fermer", "§7Cliquez pour fermer");
        if (page < maxPage) {
            setItem(size - 4, Material.PAPER, "§e§lPage suivante →",
                    "§7Page " + (page + 2) + " / " + (maxPage + 1));
        }
        setItem(size - 1, Material.COMPASS, "§e§l↻ Actualiser", "§7Cliquez pour rafraîchir");
    }

    private ItemStack buildEntryItem(BankManager.BankTransaction tx) {
        ItemStack item = new ItemStack(materialFor(tx.action));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', titleFor(tx)));
            List<String> lore = new ArrayList<>();
            lore.add(c("§7Par : §e" + tx.displayName()));
            lore.add(c("§7Type : " + typeLabel(tx.action)));
            lore.add(c("§7Montant : " + amountLabel(tx)));
            if (!Double.isNaN(tx.balanceAfter)) {
                lore.add(c("§7Solde après : §6" + FactionPowerShop.formatMoney(tx.balanceAfter) + " §7Cantox"));
            }
            lore.add(c("§7Quand : §f" + formatWhen(tx.createdAt)));
            if (tx.reason != null && !tx.reason.isBlank()) {
                lore.add(c("§8" + tx.reason));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatWhen(long createdAt) {
        if (createdAt <= 0) return "date inconnue";
        return DATE_FMT.format(Instant.ofEpochSecond(createdAt));
    }

    private static String c(String line) {
        return ChatColor.translateAlternateColorCodes('&', line);
    }

    private static Material materialFor(String action) {
        if (action == null) return Material.PAPER;
        return switch (action) {
            case BankManager.ACTION_DEPOSIT -> Material.EMERALD;
            case BankManager.ACTION_WITHDRAW -> Material.GOLD_NUGGET;
            case BankManager.ACTION_POWER_BUY -> Material.NETHER_STAR;
            case BankManager.ACTION_ADMIN_ADD -> Material.EMERALD_BLOCK;
            case BankManager.ACTION_ADMIN_REMOVE -> Material.REDSTONE_BLOCK;
            default -> Material.PAPER;
        };
    }

    private static String titleFor(BankManager.BankTransaction tx) {
        return switch (tx.action != null ? tx.action : "") {
            case BankManager.ACTION_DEPOSIT -> "§a+ " + FactionPowerShop.formatMoney(tx.amount) + " Cantox";
            case BankManager.ACTION_WITHDRAW -> "§6− " + FactionPowerShop.formatMoney(tx.amount) + " Cantox";
            case BankManager.ACTION_POWER_BUY -> "§b⚡ Pouvoir − " + FactionPowerShop.formatMoney(tx.amount);
            case BankManager.ACTION_ADMIN_ADD -> "§aAdmin + " + FactionPowerShop.formatMoney(tx.amount);
            case BankManager.ACTION_ADMIN_REMOVE -> "§cAdmin − " + FactionPowerShop.formatMoney(tx.amount);
            default -> "§e" + (tx.action != null ? tx.action : "?") + " §7"
                    + FactionPowerShop.formatMoney(tx.amount);
        };
    }

    private static String typeLabel(String action) {
        if (action == null) return "§7Inconnu";
        return switch (action) {
            case BankManager.ACTION_DEPOSIT -> "§aDépôt";
            case BankManager.ACTION_WITHDRAW -> "§6Retrait";
            case BankManager.ACTION_POWER_BUY -> "§bAchat de pouvoir";
            case BankManager.ACTION_ADMIN_ADD -> "§aAjout admin";
            case BankManager.ACTION_ADMIN_REMOVE -> "§cRetrait admin";
            default -> "§7" + action;
        };
    }

    private static String amountLabel(BankManager.BankTransaction tx) {
        String signed = switch (tx.action != null ? tx.action : "") {
            case BankManager.ACTION_DEPOSIT, BankManager.ACTION_ADMIN_ADD -> "§a+";
            default -> "§c−";
        };
        return signed + FactionPowerShop.formatMoney(tx.amount) + " §7Cantox";
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == size - 6) {
            setPage(page - 1);
            return;
        }
        if (slot == size - 4) {
            setPage(page + 1);
        }
    }
}
