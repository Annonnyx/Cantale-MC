package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.auction.AuctionListing;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MyAuctionListingsUI extends BaseUI {

    private final CantalePlugin plugin;

    public MyAuctionListingsUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Mes Ventes", 4);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        fillEmpty();
        
        setItem(4, Material.ENDER_CHEST, "§6§lMes Ventes", "§7Gérez vos annonces");

        List<AuctionListing> listings = plugin.getAuctionManager().getPlayerListings(player.getUniqueId());
        if (listings.isEmpty()) {
            setItem(22, Material.BARRIER, "§cAucune vente", "§7Vous n'avez aucune vente en cours");
        } else {
            int slot = 10;
            for (AuctionListing listing : listings) {
                if (slot >= 16) slot = 19;
                if (slot >= 25) slot = 28;
                if (slot >= 34) break;
                
                ItemStack item = listing.getItem().clone();
                item.setAmount(Math.min(item.getMaxStackSize(), listing.getQuantity()));
                setItem(slot, item);
                slot++;
            }
        }

        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        
        if (slot >= 10 && slot <= 34) {
            List<AuctionListing> listings = plugin.getAuctionManager().getPlayerListings(player.getUniqueId());
            int index = slot - 10;
            if (index >= 0 && index < listings.size()) {
                AuctionListing listing = listings.get(index);
                plugin.getAuctionManager().cancelListing(player, listing.getId());
                init();
            }
        }
    }
}
