package fr.cantale.plugin.auction;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class AuctionDAO {

    private final CantalePlugin plugin;

    public AuctionDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int createListing(AuctionListing listing) {
        String sql = "INSERT INTO auction_listings (seller_uuid, seller_name, item_data, item_type, quantity, price_per_unit, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, listing.getSellerUuid().toString());
            stmt.setString(2, listing.getSellerName());
            stmt.setString(3, serializeItem(listing.getItem()));
            stmt.setString(4, listing.getItemType().name());
            stmt.setInt(5, listing.getQuantity());
            stmt.setDouble(6, listing.getPricePerUnit());
            stmt.setLong(7, listing.getCreatedAt());
            stmt.setLong(8, listing.getExpiresAt());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createListing : " + e.getMessage());
        }
        return -1;
    }

    public AuctionListing getListing(int id) {
        String sql = "SELECT * FROM auction_listings WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractListing(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getListing : " + e.getMessage());
        }
        return null;
    }

    public List<AuctionListing> getActiveListings() {
        List<AuctionListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM auction_listings WHERE buyer_uuid IS NULL AND expires_at > ? ORDER BY created_at DESC";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                listings.add(extractListing(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getActiveListings : " + e.getMessage());
        }
        return listings;
    }

    /**
     * Réserve le stock seulement si la quantité en base est encore {@code expectedQuantity}
     * et que personne n'a déjà acheté le listing.
     */
    public boolean updateListing(AuctionListing listing, int expectedQuantity) {
        String sql = "UPDATE auction_listings SET quantity = ?, buyer_uuid = ? WHERE id = ? AND buyer_uuid IS NULL AND quantity = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("Erreur updateListing : pool SQL indisponible.");
                return false;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, listing.getQuantity());
                stmt.setString(2, listing.getBuyerUuid() != null ? listing.getBuyerUuid().toString() : null);
                stmt.setInt(3, listing.getId());
                stmt.setInt(4, expectedQuantity);
                return stmt.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateListing : " + e.getMessage());
            return false;
        }
    }

    public void deleteListing(int id) {
        String sql = "DELETE FROM auction_listings WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteListing : " + e.getMessage());
        }
    }

    private AuctionListing extractListing(ResultSet rs) throws SQLException {
        return new AuctionListing(
                rs.getInt("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                deserializeItem(rs.getString("item_data")),
                Material.valueOf(rs.getString("item_type")),
                rs.getInt("quantity"),
                rs.getDouble("price_per_unit"),
                rs.getLong("created_at"),
                rs.getLong("expires_at")
        );
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BukkitObjectOutputStream bos = new BukkitObjectOutputStream(os);
            bos.writeObject(item);
            bos.flush();
            return Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur serializeItem : " + e.getMessage());
            return null;
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream bis = new BukkitObjectInputStream(is);
            return (ItemStack) bis.readObject();
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur deserializeItem : " + e.getMessage());
            return null;
        }
    }
}
