package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.auction.AuctionListing;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyAuctionListingsUI extends BaseUI {

    private static final int[] LISTING_SLOTS = {
            10, 11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24,
            28, 29, 30, 31, 32, 33
    };

    private final CantalePlugin plugin;
    private final Map<Integer, Integer> slotToListingId = new HashMap<>();

    public MyAuctionListingsUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Mes Ventes", 4);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        inventory.clear();
        fillEmpty();
        slotToListingId.clear();

        setItem(4, Material.ENDER_CHEST, "§6§lMes Ventes",
                "§7Gérez vos annonces",
                "§7Cliquez une annonce pour l'annuler");

        List<AuctionListing> listings = plugin.getAuctionManager().getPlayerListings(player.getUniqueId());
        if (listings.isEmpty()) {
            setItem(22, Material.BARRIER, "§cAucune vente", "§7Vous n'avez aucune vente en cours");
        } else {
            int n = Math.min(LISTING_SLOTS.length, listings.size());
            for (int i = 0; i < n; i++) {
                AuctionListing listing = listings.get(i);
                int slot = LISTING_SLOTS[i];
                slotToListingId.put(slot, listing.getId());
                setItem(slot, AuctionUI.buildListingIcon(listing));
            }
        }

        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        Integer listingId = slotToListingId.get(slot);
        if (listingId == null) return;

        AuctionListing listing = plugin.getAuctionManager().getListing(listingId);
        if (listing == null) {
            init();
            return;
        }

        plugin.getUIManager().getNavigationManager().openUI(player,
                new ConfirmDialogUI(plugin, player,
                        "§7Annuler la vente",
                        "Annuler §e" + listing.getItemType().name()
                                + " §7x" + listing.getQuantity() + " §7et récupérer l'item ?",
                        confirmed -> {
                            if (!confirmed) {
                                plugin.getUIManager().goBack(player);
                                return;
                            }
                            plugin.getAuctionManager().cancelListing(player, listingId);
                            plugin.getUIManager().goBack(player);
                        }));
    }
}
