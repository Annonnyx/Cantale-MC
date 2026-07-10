package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Banque de faction : dépôt / retrait via boutons avec montants prédéfinis.
 */
public class FactionBankUI extends BaseUI {

    private final Faction faction;

    public FactionBankUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Banque — " + faction.getName(), 5);
        this.faction = faction;
    }

    @Override
    protected void init() {
        fillEmpty();

        double playerBalance = plugin.getEconomyManager().getBalance(player);
        boolean canView = faction.isLeader(player.getUniqueId())
            || plugin.getFactionManager().hasPermission(player.getUniqueId(), FactionPermission.INFO_BANK);
        boolean canDeposit = faction.isLeader(player.getUniqueId())
            || plugin.getFactionManager().hasPermission(player.getUniqueId(), FactionPermission.BANK_ADD);
        boolean canWithdraw = faction.isLeader(player.getUniqueId())
            || plugin.getFactionManager().hasPermission(player.getUniqueId(), FactionPermission.BANK_TAKE);

        setItem(4, Material.GOLD_BLOCK, "§6§lBanque de Faction",
            canView ? "§7Solde banque : §6" + (int) faction.getBalance() + " §7Cantox" : "§7Solde banque : §8***",
            "§7Votre solde : §6" + (int) playerBalance + " §7Cantox");

        if (canDeposit) {
            setItem(10, Material.LIME_STAINED_GLASS_PANE, "§a§l▼ DÉPOSER", "§7Transférer de votre solde", "§7vers la banque de faction");
            setItem(11, Material.GREEN_DYE, "§a+100 Cantox", "§7Cliquez pour déposer 100");
            setItem(12, Material.GREEN_DYE, "§a+1 000 Cantox", "§7Cliquez pour déposer 1000");
            setItem(13, Material.LIME_DYE, "§a+10 000 Cantox", "§7Cliquez pour déposer 10 000");
            setItem(14, Material.EMERALD, "§a+100 000 Cantox", "§7Cliquez pour déposer 100 000");
            setItem(15, Material.EMERALD_BLOCK, "§a§lTout déposer", "§7Tout votre solde");
        }

        if (canWithdraw) {
            setItem(19, Material.ORANGE_STAINED_GLASS_PANE, "§6§l▲ RETIRER", "§7Transférer de la banque", "§7vers votre solde personnel");
            setItem(20, Material.ORANGE_DYE, "§6-100 Cantox", "§7Cliquez pour retirer 100");
            setItem(21, Material.ORANGE_DYE, "§6-1 000 Cantox", "§7Cliquez pour retirer 1000");
            setItem(22, Material.YELLOW_DYE, "§6-10 000 Cantox", "§7Cliquez pour retirer 10 000");
            setItem(23, Material.GOLD_INGOT, "§6-100 000 Cantox", "§7Cliquez pour retirer 100 000");
            setItem(24, Material.GOLD_BLOCK, "§6§lTout retirer", "§7Tout le solde de la banque");
        }

        if (!canDeposit && !canWithdraw) {
            setItem(22, Material.BARRIER, "§cAucune action disponible", "§7Votre grade ne permet pas", "§7d'accéder à la banque.");
        }

        setItem(31, Material.BOOK, "§eHistorique", "§7Voir les transactions", "§7(à venir)");

        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        switch (slot) {
            case 11 -> { close(); player.performCommand("f bank deposit 100"); }
            case 12 -> { close(); player.performCommand("f bank deposit 1000"); }
            case 13 -> { close(); player.performCommand("f bank deposit 10000"); }
            case 14 -> { close(); player.performCommand("f bank deposit 100000"); }
            case 15 -> { close(); player.performCommand("f bank deposit " + (int) plugin.getEconomyManager().getBalance(player)); }
            case 20 -> { close(); player.performCommand("f bank withdraw 100"); }
            case 21 -> { close(); player.performCommand("f bank withdraw 1000"); }
            case 22 -> { close(); player.performCommand("f bank withdraw 10000"); }
            case 23 -> { close(); player.performCommand("f bank withdraw 100000"); }
            case 24 -> { close(); player.performCommand("f bank withdraw " + (int) faction.getBalance()); }
        }
    }
}
