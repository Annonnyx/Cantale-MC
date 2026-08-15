package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.auction.AuctionListing;
import fr.cantale.plugin.auction.AuctionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionUI extends BaseUI {

    /** Slots d'annonces — évite 30/32 (boutons Vendre / Mes ventes). */
    private static final int[] LISTING_SLOTS = {
            10, 11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24,
            28, 29, 31, 33
    };

    private final CantalePlugin plugin;
    private final Map<Integer, Integer> slotToListingId = new HashMap<>();

    public AuctionUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Hôtel des Ventes", 4);
        this.plugin = plugin;
    }

    @Override
    protected void init() {
        inventory.clear();
        fillEmpty();
        slotToListingId.clear();

        int playerListingCount = plugin.getAuctionManager().getPlayerListingCount(player.getUniqueId());
        setItem(4, Material.CHEST, "§6§lHôtel des Ventes",
                "§7Vos en vente: §e" + playerListingCount + " §7types",
                "§7Cliquez un item → confirmation d'achat");

        List<AuctionListing> listings = plugin.getAuctionManager().getActiveListings();
        if (listings.isEmpty()) {
            setItem(22, Material.BARRIER, "§cAucune vente", "§7Aucune vente en cours");
        } else {
            int n = Math.min(LISTING_SLOTS.length, listings.size());
            for (int i = 0; i < n; i++) {
                AuctionListing listing = listings.get(i);
                int slot = LISTING_SLOTS[i];
                slotToListingId.put(slot, listing.getId());
                setItem(slot, buildListingIcon(listing));
            }
        }

        setItem(30, Material.HOPPER, "§eVendre un item",
                "§7Vendre l'item en main principale",
                "§7Commande : §e/ah sell <qté|all> <prix>");
        setItem(32, Material.BOOK, "§eMes ventes", "§7Voir et annuler vos ventes");

        addNavigationBar(true, true);
    }

    static ItemStack buildListingIcon(AuctionListing listing) {
        ItemStack item = listing.getItem().clone();
        item.setAmount(Math.min(Math.max(1, item.getMaxStackSize()), Math.max(1, listing.getQuantity())));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String baseName = meta.hasDisplayName()
                    ? meta.getDisplayName()
                    : "§f" + listing.getItemType().name();
            String unit = AuctionManager.formatPrice(listing.getPricePerUnit());
            double total = listing.getPricePerUnit() * listing.getQuantity();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    baseName + " §8· §6" + unit + "§7/u"));

            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add("");
            lore.add(c("§7Vendeur : §e" + listing.getSellerName()));
            lore.add(c("§7Quantité : §e" + listing.getQuantity()));
            lore.add(c("§6Prix / unité : §e" + unit + " §6Cantox"));
            lore.add(c("§6Total : §e" + AuctionManager.formatPrice(total) + " §6Cantox"));
            lore.add(c("§8#" + listing.getId()));
            lore.add("");
            lore.add(c("§aCliquez pour acheter (confirmation)"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String c(String line) {
        return ChatColor.translateAlternateColorCodes('&', line);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();

        if (slot == 30) {
            player.closeInventory();
            player.sendMessage("§8[§6Cantale§8] §7Tapez: §e/ah sell [quantité|all] [prix]");
            return;
        }
        if (slot == 32) {
            nav.openUI(player, new MyAuctionListingsUI(plugin, player));
            return;
        }

        Integer listingId = slotToListingId.get(slot);
        if (listingId == null) return;

        AuctionListing listing = plugin.getAuctionManager().getListing(listingId);
        if (listing == null || listing.isSold() || listing.isExpired()) {
            player.sendMessage("§8[§6Cantale§8] §cCette vente n'existe plus.");
            init();
            return;
        }

        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous ne pouvez pas acheter votre propre item.");
            return;
        }

        int qty = listing.getQuantity();
        double total = listing.getPricePerUnit() * qty;
        String unit = AuctionManager.formatPrice(listing.getPricePerUnit());
        String totalFmt = AuctionManager.formatPrice(total);
        String itemLabel = listing.getItemType().name();

        int finalListingId = listingId;
        int finalQty = qty;
        nav.openUI(player, new ConfirmDialogUI(
                plugin,
                player,
                "§7Confirmer l'achat AH",
                "Acheter §e" + itemLabel + " §7x" + finalQty
                        + " §7pour §6" + totalFmt + " §7Cantox"
                        + " §8(" + unit + "/u) §7?",
                confirmed -> {
                    if (!confirmed) {
                        plugin.getUIManager().goBack(player);
                        return;
                    }
                    plugin.getAuctionManager().buyItem(player, finalListingId, finalQty);
                    plugin.getUIManager().goBack(player);
                }
        ));
    }
}
