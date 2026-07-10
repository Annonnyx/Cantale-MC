package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.auction.AuctionListing;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AuctionUI extends BaseUI {

    private final CantalePlugin plugin;

    public AuctionUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Hôtel des Ventes", 4);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        fillEmpty();
        
        int playerListingCount = plugin.getAuctionManager().getPlayerListingCount(player.getUniqueId());
        setItem(4, Material.CHEST, "§6§lHôtel des Ventes",
            "§7Vos en vente: §e" + playerListingCount + "/3 types",
            "§7Cliquez sur un item pour acheter");

        List<AuctionListing> listings = plugin.getAuctionManager().getActiveListings();
        if (listings.isEmpty()) {
            setItem(22, Material.BARRIER, "§cAucune vente", "§7Aucune vente en cours");
        } else {
            int slot = 10;
            for (int i = 0; i < Math.min(21, listings.size()); i++) {
                AuctionListing listing = listings.get(i);
                if (slot >= 16) slot = 19;
                if (slot >= 25) slot = 28;
                if (slot >= 34) break;
                
                ItemStack item = listing.getItem().clone();
                item.setAmount(Math.min(item.getMaxStackSize(), listing.getQuantity()));
                setItem(slot, item);
                slot++;
            }
        }

        setItem(30, Material.HOPPER, "§eVendre un item", "§7Vendre l'item en main");
        setItem(32, Material.BOOK, "§eMes ventes", "§7Voir et annuler vos ventes");

        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        
        if (slot == 30) {
            player.closeInventory();
            player.sendMessage("§8[§6Cantale§8] §7Tapez: §e/ah sell [quantité|all] [prix]");
        } else if (slot == 32) {
            nav.openUI(player, new MyAuctionListingsUI(plugin, player));
        } else if (slot >= 10 && slot <= 34) {
            List<AuctionListing> listings = plugin.getAuctionManager().getActiveListings();
            int index = slot - 10;
            if (index >= 0 && index < listings.size()) {
                AuctionListing listing = listings.get(index);
                player.closeInventory();
                player.sendMessage("§8[§6Cantale§8] §7Tapez: §e/ah buy " + listing.getId() + " [quantité]");
            }
        }
    }
}
