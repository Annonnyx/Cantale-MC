package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persistance des plafonds quotidiens d'achat/vente du shop admin
 * (un compteur par joueur et par Material, remis à zéro chaque jour).
 */
public class ShopDAO {

    private final CantalePlugin plugin;

    public ShopDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /** Quantité déjà vendue aujourd'hui par ce joueur pour ce Material (0 si aucune ou jour différent). */
    public int getSoldToday(UUID uuid, Material material) {
        return getUsage(uuid, material)[0];
    }

    /** Quantité déjà achetée aujourd'hui par ce joueur pour ce Material (0 si aucune ou jour différent). */
    public int getBoughtToday(UUID uuid, Material material) {
        return getUsage(uuid, material)[1];
    }

    private int[] getUsage(UUID uuid, Material material) {
        String today = LocalDate.now().toString();
        String sql = "SELECT limit_date, sold_qty, bought_qty FROM shop_daily_limits WHERE player_uuid = ? AND material = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, material.name());
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && today.equals(rs.getString("limit_date"))) {
                return new int[]{rs.getInt("sold_qty"), rs.getInt("bought_qty")};
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Shop] Erreur getUsage : " + e.getMessage());
        }
        return new int[]{0, 0};
    }

    public void addSold(UUID uuid, Material material, int quantity) {
        upsert(uuid, material, quantity, 0);
    }

    public void addBought(UUID uuid, Material material, int quantity) {
        upsert(uuid, material, 0, quantity);
    }

    private void upsert(UUID uuid, Material material, int soldDelta, int boughtDelta) {
        String today = LocalDate.now().toString();
        int[] current = getUsage(uuid, material); // déjà remis à zéro si jour différent
        int newSold = current[0] + soldDelta;
        int newBought = current[1] + boughtDelta;

        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO shop_daily_limits (player_uuid, material, limit_date, sold_qty, bought_qty) VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO shop_daily_limits (player_uuid, material, limit_date, sold_qty, bought_qty) VALUES (?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE limit_date = VALUES(limit_date), sold_qty = VALUES(sold_qty), bought_qty = VALUES(bought_qty)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, material.name());
            stmt.setString(3, today);
            stmt.setInt(4, newSold);
            stmt.setInt(5, newBought);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Shop] Erreur upsert daily limit : " + e.getMessage());
        }
    }
}
