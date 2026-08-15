package fr.cantale.plugin.rank;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final CantalePlugin plugin;
    /** NONE est un grade valide : on le cache aussi (pas de SQL à chaque PlayerMoveEvent). */
    private final Map<UUID, PlayerRank> rankCache = new ConcurrentHashMap<>();

    public RankManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerRank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    public PlayerRank getRank(UUID uuid) {
        PlayerRank cached = rankCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT role FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                PlayerRank rank = PlayerRank.fromString(rs.getString("role"));
                rankCache.put(uuid, rank);
                return rank;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getRank: " + e.getMessage());
            return PlayerRank.NONE;
        }
        rankCache.put(uuid, PlayerRank.NONE);
        return PlayerRank.NONE;
    }

    public boolean setRank(UUID uuid, PlayerRank rank) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?)"
                : "INSERT INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = VALUES(updated_at)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, rank.name());
            stmt.setInt(3, (int) (System.currentTimeMillis() / 1000));
            stmt.executeUpdate();
            rankCache.put(uuid, rank);
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
            rankCache.put(uuid, PlayerRank.NONE);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeRank: " + e.getMessage());
            return false;
        }
    }

    /** Relit le SQL au prochain getRank (join : grade éventuellement changé hors-ligne). */
    public void invalidate(UUID uuid) {
        rankCache.remove(uuid);
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
                String insert = plugin.getDatabaseManager().isSQLite()
                        ? "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at, last_monthly_lives) VALUES (?, ?, ?, ?)"
                        : "INSERT INTO player_permissions (uuid, role, updated_at, last_monthly_lives) VALUES (?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = VALUES(updated_at), last_monthly_lives = VALUES(last_monthly_lives)";
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
     * baseAmount is granted to everyone, plus the rank's daily bonus.
     * Returns the total amount granted, or 0 if already received today.
     */
    public int grantDailyCantox(Player player, int baseAmount) {
        return grantDailyCantox(player.getUniqueId(), baseAmount);
    }

    /**
     * Version UUID pour les réclamations hors ligne (ex: Discord).
     */
    public int grantDailyCantox(UUID uuid, int baseAmount) {
        PlayerRank rank = getRank(uuid);

        String today = LocalDate.now().toString();
        String lastClaim = getLastCantoxClaim(uuid);

        if (today.equals(lastClaim)) return 0; // Already claimed today

        int total = Math.max(0, baseAmount) + rank.getDailyCantox();
        if (total <= 0) return 0;

        plugin.getEconomyManager().addBalance(uuid, total);
        setLastCantoxClaim(uuid, today);

        return total;
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
        ensureCantoxClaimColumn();

        String sql = "UPDATE player_permissions SET last_cantox_claim = ? WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                String insert = plugin.getDatabaseManager().isSQLite()
                        ? "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at, last_cantox_claim) VALUES (?, ?, ?, ?)"
                        : "INSERT INTO player_permissions (uuid, role, updated_at, last_cantox_claim) VALUES (?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = VALUES(updated_at), last_cantox_claim = VALUES(last_cantox_claim)";
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
