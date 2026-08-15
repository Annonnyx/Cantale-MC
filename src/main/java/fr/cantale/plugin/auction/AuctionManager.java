package fr.cantale.plugin.auction;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {

    private final CantalePlugin plugin;
    private final AuctionDAO auctionDAO;
    private final Map<Integer, AuctionListing> activeListings;
    private final Map<UUID, Set<Material>> playerItemTypes;
    private static final long DEFAULT_DURATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 jours

    public AuctionManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.auctionDAO = new AuctionDAO(plugin);
        this.activeListings = new ConcurrentHashMap<>();
        this.playerItemTypes = new ConcurrentHashMap<>();
    }

    public boolean sellItem(Player player, ItemStack handItem, int quantity, double pricePerUnit) {
        UUID uuid = player.getUniqueId();

        if (handItem == null || handItem.getType().isAir() || handItem.getAmount() <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cTenez un item dans votre main.");
            return false;
        }

        // Snapshot AVANT toute mutation : setAmount(0) transforme le stack Bukkit en AIR.
        Material soldType = handItem.getType();
        ItemStack listingStack = handItem.clone();
        listingStack.setAmount(quantity);

        // Vérifier max types d'items (dépend du grade)
        PlayerRank rank = plugin.getRankManager().getRank(player);
        int maxListings = rank.getMaxAhListings();
        Set<Material> playerTypes = playerItemTypes.computeIfAbsent(uuid, k -> new HashSet<>());
        if (!playerTypes.contains(soldType) && playerTypes.size() >= maxListings) {
            player.sendMessage("§8[§6Cantale§8] §cVous avez déjà " + maxListings + " types d'items en vente. Annulez-en un avant.");
            return false;
        }

        int available = handItem.getAmount();
        if (quantity <= 0 || quantity > available) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez que " + available + " items en main.");
            return false;
        }

        if (pricePerUnit <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cLe prix doit être positif.");
            return false;
        }

        AuctionListing listing = new AuctionListing(uuid, player.getName(), listingStack, quantity, pricePerUnit, DEFAULT_DURATION_MS);
        int id = auctionDAO.createListing(listing);
        if (id > 0) {
            listing.setId(id);
            activeListings.put(id, listing);
            playerTypes.add(soldType);

            consumeFromMainHand(player, quantity);

            player.sendMessage("§8[§6Cantale§8] §aItem mis en vente : §e" + soldType.name()
                    + " §7x" + quantity + " §7pour §6" + formatPrice(pricePerUnit) + " §7Cantox/u");
            return true;
        }

        player.sendMessage("§8[§6Cantale§8] §cErreur lors de la mise en vente.");
        return false;
    }

    /** Retire {@code quantity} de la main principale sans laisser un stack AIR fantôme. */
    private static void consumeFromMainHand(Player player, int quantity) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;

        int left = hand.getAmount() - quantity;
        if (left <= 0) {
            inv.setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            inv.setItemInMainHand(hand);
        }
    }

    public static String formatPrice(double price) {
        if (Math.floor(price) == price) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    public boolean buyItem(Player player, int listingId, int quantity) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null || listing.isSold() || listing.isExpired()) {
            player.sendMessage("§8[§6Cantale§8] §cCette vente n'existe plus.");
            return false;
        }

        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous ne pouvez pas acheter votre propre item.");
            return false;
        }

        int available = listing.getQuantity();
        if (quantity > available) {
            quantity = available;
        }
        if (quantity <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
            return false;
        }

        double totalPrice = listing.getPricePerUnit() * quantity;

        if (!plugin.getEconomyManager().hasEnough(player, totalPrice)) {
            player.sendMessage("§8[§6Cantale§8] §cSolde insuffisant (besoin : " + formatPrice(totalPrice) + " Cantox)");
            return false;
        }

        plugin.getEconomyManager().withdraw(player, totalPrice);
        plugin.getEconomyManager().addBalance(listing.getSellerUuid(), totalPrice);

        ItemStack item = listing.getItem().clone();
        item.setAmount(quantity);
        player.getInventory().addItem(item);

        if (quantity == available) {
            listing.setBuyerUuid(player.getUniqueId());
            auctionDAO.updateListing(listing);
            activeListings.remove(listingId);
            removePlayerItemType(listing.getSellerUuid(), listing.getItemType());
        } else {
            listing.setQuantity(available - quantity);
            auctionDAO.updateListing(listing);
        }

        player.sendMessage("§8[§6Cantale§8] §aAchat effectué : §e" + item.getType().name()
                + " §7x" + quantity + " §7pour §6" + formatPrice(totalPrice) + " §7Cantox");
        return true;
    }

    public AuctionListing getListing(int listingId) {
        return activeListings.get(listingId);
    }

    public boolean cancelListing(Player player, int listingId) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            player.sendMessage("§8[§6Cantale§8] §cCette vente n'existe plus.");
            return false;
        }

        if (!listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cCe n'est pas votre vente.");
            return false;
        }

        if (listing.isSold()) {
            player.sendMessage("§8[§6Cantale§8] §cCette vente a déjà été achetée.");
            return false;
        }

        ItemStack item = listing.getItem().clone();
        item.setAmount(listing.getQuantity());
        player.getInventory().addItem(item);

        auctionDAO.deleteListing(listingId);
        activeListings.remove(listingId);
        removePlayerItemType(player.getUniqueId(), listing.getItemType());

        player.sendMessage("§8[§6Cantale§8] §aVente annulée, item rendu.");
        return true;
    }

    public List<AuctionListing> getActiveListings() {
        return new ArrayList<>(activeListings.values());
    }

    public List<AuctionListing> getPlayerListings(UUID uuid) {
        List<AuctionListing> listings = new ArrayList<>();
        for (AuctionListing listing : activeListings.values()) {
            if (listing.getSellerUuid().equals(uuid) && !listing.isSold()) {
                listings.add(listing);
            }
        }
        return listings;
    }

    public int getPlayerListingCount(UUID uuid) {
        Set<Material> types = playerItemTypes.get(uuid);
        return types != null ? types.size() : 0;
    }

    private void removePlayerItemType(UUID uuid, Material type) {
        Set<Material> types = playerItemTypes.get(uuid);
        if (types != null) {
            boolean stillHasType = false;
            for (AuctionListing listing : activeListings.values()) {
                if (listing.getSellerUuid().equals(uuid) && listing.getItemType() == type && !listing.isSold()) {
                    stillHasType = true;
                    break;
                }
            }
            if (!stillHasType) {
                types.remove(type);
            }
        }
    }

    public void loadListings() {
        List<AuctionListing> listings = auctionDAO.getActiveListings();
        for (AuctionListing listing : listings) {
            if (!listing.isSold() && !listing.isExpired()) {
                activeListings.put(listing.getId(), listing);
                playerItemTypes.computeIfAbsent(listing.getSellerUuid(), k -> new HashSet<>())
                        .add(listing.getItemType());
            }
        }
    }

    public void cleanupExpired() {
        Iterator<AuctionListing> it = activeListings.values().iterator();
        while (it.hasNext()) {
            AuctionListing listing = it.next();
            if (listing.isExpired() && !listing.isSold()) {
                if (plugin.getServer().getPlayer(listing.getSellerUuid()) != null) {
                    ItemStack item = listing.getItem().clone();
                    item.setAmount(listing.getQuantity());
                    plugin.getServer().getPlayer(listing.getSellerUuid()).getInventory().addItem(item);
                }
                it.remove();
                removePlayerItemType(listing.getSellerUuid(), listing.getItemType());
                auctionDAO.deleteListing(listing.getId());
            }
        }
    }
}
