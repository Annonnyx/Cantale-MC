package fr.cantale.plugin.rank;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public class RankManager {

    private final CantalePlugin plugin;

    public RankManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerRank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    public PlayerRank getRank(UUID uuid) {
        String sql = "SELECT role FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return PlayerRank.fromString(rs.getString("role"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getRank: " + e.getMessage());
        }
        return PlayerRank.NONE;
    }

    public boolean setRank(UUID uuid, PlayerRank rank) {
        String sql = "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, rank.name());
            stmt.setInt(3, (int) (System.currentTimeMillis() / 1000));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setRank: " + e.getMessage());
            return false;
        }
    }

    public boolean removeRank(UUID uuid) {
        String sql = "DELETE FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeRank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Grant monthly VIE items to Aventurier and Héros players.
     * Returns the number of items granted, or 0 if not applicable/already claimed.
     */
    public int grantMonthlyLives(Player player) {
        PlayerRank rank = getRank(player);
        if (!rank.hasMonthlyLives()) return 0;

        String currentMonth = LocalDate.now().withDayOfMonth(1).toString();
        String lastClaim = getLastMonthlyLivesClaim(player.getUniqueId());

        if (currentMonth.equals(lastClaim)) return 0; // Already claimed this month

        int livesCount = rank.getMonthlyLives();
        fr.cantale.plugin.custom.CustomItemManager itemManager = plugin.getCustomItemManager();
        org.bukkit.inventory.ItemStack vieItem = itemManager.createCustomItem(fr.cantale.plugin.custom.CustomItemType.VIE);

        // Give items to player (drop if inventory full)
        for (int i = 0; i < livesCount; i++) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(vieItem.clone());
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), vieItem.clone());
            }
        }

        setLastMonthlyLivesClaim(player.getUniqueId(), currentMonth);
        return livesCount;
    }

    private String getLastMonthlyLivesClaim(UUID uuid) {
        String sql = "SELECT last_monthly_lives FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("last_monthly_lives");
            }
        } catch (SQLException e) {
            // Column might not exist yet
        }
        return null;
    }

    private void setLastMonthlyLivesClaim(UUID uuid, String month) {
        ensureMonthlyLivesColumn();

        String sql = "UPDATE player_permissions SET last_monthly_lives = ? WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, month);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                String insert = "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at, last_monthly_lives) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, uuid.toString());
                    PlayerRank rank = getRank(uuid);
                    ins.setString(2, rank.name());
                    ins.setInt(3, (int) (System.currentTimeMillis() / 1000));
                    ins.setString(4, month);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLastMonthlyLivesClaim: " + e.getMessage());
        }
    }

    private void ensureMonthlyLivesColumn() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE player_permissions ADD COLUMN last_monthly_lives TEXT DEFAULT NULL");
        } catch (SQLException e) {
            // Column already exists — ignore
        }
    }

    /**
     * Grant daily Cantox bonus if the player hasn't received it today.
     * Returns the amount granted, or 0 if already received today.
     */
    public int grantDailyCantox(Player player) {
        PlayerRank rank = getRank(player);
        if (rank == PlayerRank.NONE) return 0;

        String today = LocalDate.now().toString();
        String lastClaim = getLastCantoxClaim(player.getUniqueId());

        if (today.equals(lastClaim)) return 0; // Already claimed today

        // Grant the bonus
        plugin.getEconomyManager().deposit(player, rank.getDailyCantox());
        setLastCantoxClaim(player.getUniqueId(), today);

        return rank.getDailyCantox();
    }

    private String getLastCantoxClaim(UUID uuid) {
        String sql = "SELECT last_cantox_claim FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("last_cantox_claim");
            }
        } catch (SQLException e) {
            // Column might not exist yet — that's fine, return null
        }
        return null;
    }

    private void setLastCantoxClaim(UUID uuid, String date) {
        // Ensure the column exists
        ensureCantoxClaimColumn();

        String sql = "UPDATE player_permissions SET last_cantox_claim = ? WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                // Row doesn't exist, insert it
                String insert = "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at, last_cantox_claim) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, uuid.toString());
                    PlayerRank rank = getRank(uuid);
                    ins.setString(2, rank.name());
                    ins.setInt(3, (int) (System.currentTimeMillis() / 1000));
                    ins.setString(4, date);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLastCantoxClaim: " + e.getMessage());
        }
    }

    private void ensureCantoxClaimColumn() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE player_permissions ADD COLUMN last_cantox_claim TEXT DEFAULT NULL");
        } catch (SQLException e) {
            // Column already exists — ignore
        }
    }
}
