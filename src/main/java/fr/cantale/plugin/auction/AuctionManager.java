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
            plugin.messages().send(player, "auction.hand");
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
            plugin.messages().send(player, "auction.limit", maxListings);
            return false;
        }

        int available = handItem.getAmount();
        if (quantity <= 0 || quantity > available) {
            plugin.messages().send(player, "auction.short", available);
            return false;
        }

        if (pricePerUnit <= 0) {
            plugin.messages().send(player, "auction.price");
            return false;
        }

        AuctionListing listing = new AuctionListing(uuid, player.getName(), listingStack, quantity, pricePerUnit, DEFAULT_DURATION_MS);
        int id = auctionDAO.createListing(listing);
        if (id > 0) {
            listing.setId(id);
            activeListings.put(id, listing);
            playerTypes.add(soldType);

            consumeFromMainHand(player, quantity);

            plugin.messages().send(player, "auction.listed", soldType.name(), quantity, formatPrice(pricePerUnit));
            return true;
        }

        plugin.messages().send(player, "auction.error");
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
        if (listing == null) {
            plugin.messages().send(player, "auction.gone");
            return false;
        }

        ListingPurchase.Outcome outcome;
        try {
            outcome = ListingPurchase.tryPurchase(listing, stockOf(listing), player.getUniqueId(), quantity, economyLedger());
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Achat HDV annulé (listing " + listingId + ") : " + e.getMessage());
            plugin.messages().send(player, "auction.busy");
            return false;
        }

        return switch (outcome.status()) {
            case PURCHASED -> {
                ItemStack item = listing.getItem().clone();
                item.setAmount(outcome.quantity());
                giveOrDrop(player, item);
                plugin.messages().send(player, "auction.bought", item.getType().name(),
                        outcome.quantity(), formatPrice(outcome.totalPrice()));
                yield true;
            }
            case OWN_LISTING -> {
                plugin.messages().send(player, "auction.self");
                yield false;
            }
            case INSUFFICIENT_FUNDS -> {
                plugin.messages().send(player, "auction.funds");
                yield false;
            }
            case INVALID_QUANTITY -> {
                plugin.messages().send(player, "auction.quantity");
                yield false;
            }
            case UNAVAILABLE -> {
                plugin.messages().send(player, "auction.gone");
                yield false;
            }
        };
    }

    private ListingPurchase.Ledger economyLedger() {
        return new ListingPurchase.Ledger() {
            @Override
            public boolean tryDebit(UUID account, double amount) {
                return plugin.getEconomyManager().withdraw(account, amount);
            }

            @Override
            public boolean tryCredit(UUID account, double amount) {
                return plugin.getEconomyManager().deposit(account, amount);
            }
        };
    }

    private ListingPurchase.Stock stockOf(AuctionListing listing) {
        return new ListingPurchase.Stock() {
            @Override
            public UUID sellerId() {
                return listing.getSellerUuid();
            }

            @Override
            public boolean isUnavailable() {
                return !activeListings.containsKey(listing.getId()) || listing.isSold() || listing.isExpired() || listing.getQuantity() <= 0;
            }

            @Override
            public int quantity() {
                return listing.getQuantity();
            }

            @Override
            public double pricePerUnit() {
                return listing.getPricePerUnit();
            }

            @Override
            public void applySale(UUID buyerId, int quantitySold) {
                int previousQuantity = listing.getQuantity();
                UUID previousBuyer = listing.getBuyerUuid();
                int left = previousQuantity - quantitySold;
                if (left <= 0) {
                    listing.setBuyerUuid(buyerId);
                    listing.setQuantity(0);
                } else {
                    listing.setQuantity(left);
                }
                if (!auctionDAO.updateListing(listing, previousQuantity)) {
                    listing.setBuyerUuid(previousBuyer);
                    listing.setQuantity(previousQuantity);
                    throw new IllegalStateException("persistance du listing " + listing.getId() + " refusée");
                }
                if (left <= 0) {
                    activeListings.remove(listing.getId());
                    removePlayerItemType(listing.getSellerUuid(), listing.getItemType());
                }
            }
        };
    }

    /** Pose au sol ce que l'inventaire ne peut pas recevoir. */
    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (leftover.isEmpty()) {
            return;
        }
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        plugin.messages().send(player, "auction.overflow");
    }

    public AuctionListing getListing(int listingId) {
        return activeListings.get(listingId);
    }

    public boolean cancelListing(Player player, int listingId) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            plugin.messages().send(player, "auction.gone");
            return false;
        }

        if (!listing.getSellerUuid().equals(player.getUniqueId())) {
            plugin.messages().send(player, "auction.not-yours");
            return false;
        }

        synchronized (listing) {
            if (!activeListings.containsKey(listingId) || listing.isSold()) {
                plugin.messages().send(player, "auction.sold");
                return false;
            }

            ItemStack item = listing.getItem().clone();
            item.setAmount(listing.getQuantity());
            activeListings.remove(listingId);
            removePlayerItemType(player.getUniqueId(), listing.getItemType());
            giveOrDrop(player, item);
            auctionDAO.deleteListing(listingId);
        }

        plugin.messages().send(player, "auction.cancelled");
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
                Player seller = plugin.getServer().getPlayer(listing.getSellerUuid());
                if (seller == null || !seller.isOnline()) {
                    continue;
                }
                synchronized (listing) {
                    if (!activeListings.containsKey(listing.getId()) || listing.isSold()) {
                        it.remove();
                        continue;
                    }
                    ItemStack item = listing.getItem().clone();
                    item.setAmount(listing.getQuantity());
                    giveOrDrop(seller, item);
                    it.remove();
                    removePlayerItemType(listing.getSellerUuid(), listing.getItemType());
                    auctionDAO.deleteListing(listing.getId());
                }
            }
        }
    }
}
