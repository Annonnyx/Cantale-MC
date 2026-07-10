package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class EconomyUI extends BaseUI {

    public EconomyUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Économie", 4);
    }

    @Override
    protected void init() {
        fillEmpty();
        double balance = plugin.getEconomyManager().getBalance(player);

        setItem(4, Material.GOLD_BLOCK, "§6§l" + player.getName(),
            "§7Solde: §6" + (int) balance + " §7Cantox");

        setItem(11, Material.GOLD_NUGGET, "§ePayer un joueur", "§7Transférer de l'argent");
        setItem(13, Material.EMERALD, "§eAuction House", "§7Acheter / Vendre");
        setItem(15, Material.BOOK, "§eTransactions", "§7Historique");

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 11 -> nav.openUI(player, new PlayerSelectorUI(plugin, player, "§7Payer un joueur", target -> {
                player.closeInventory();
                player.sendMessage("§8[§6Cantale§8] §7Tapez le montant à envoyer à §e" + target.getName());
                // Ici on pourrait utiliser un système de chat input
                player.performCommand("pay " + target.getName());
            }, title));
            case 13 -> nav.openUI(player, new AuctionUI(plugin, player));
            case 15 -> player.sendMessage("§eHistorique des transactions — à implémenter.");
        }
    }
}
