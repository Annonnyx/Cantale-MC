package fr.cantale.plugin.economy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankManager {

    public static final String ACTION_DEPOSIT = "DEPOSIT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_POWER_BUY = "POWER_BUY";
    public static final String ACTION_ADMIN_ADD = "ADMIN_ADD";
    public static final String ACTION_ADMIN_REMOVE = "ADMIN_REMOVE";

    private final CantalePlugin plugin;
    private final FactionManager factionManager;

    public BankManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
    }

    public double getBalance(int factionId) {
        Faction faction = factionManager.getFaction(factionId);
        return faction != null ? faction.getBalance() : 0;
    }

    public boolean deposit(int factionId, UUID playerUuid, double amount) {
        if (amount <= 0) return false;

        PlayerDAO playerDAO = new PlayerDAO(plugin);
        double playerBalance = playerDAO.getBalance(playerUuid);
        if (playerBalance < amount) return false;

        playerDAO.removeBalance(playerUuid, amount);

        Faction faction = factionManager.getFaction(factionId);
        if (faction == null) return false;

        faction.setBalance(faction.getBalance() + amount);
        factionManager.saveFaction(faction);

        logTransaction(factionId, playerUuid, ACTION_DEPOSIT, amount, faction.getBalance(), "Dépôt par joueur");
        return true;
    }

    public boolean withdraw(int factionId, UUID playerUuid, double amount) {
        if (amount <= 0) return false;

        Faction faction = factionManager.getFaction(factionId);
        if (faction == null || faction.getBalance() < amount) return false;

        PlayerDAO playerDAO = new PlayerDAO(plugin);
        playerDAO.addBalance(playerUuid, amount);

        faction.setBalance(faction.getBalance() - amount);
        factionManager.saveFaction(faction);

        logTransaction(factionId, playerUuid, ACTION_WITHDRAW, amount, faction.getBalance(), "Retrait par joueur");
        return true;
    }

    /**
     * Enregistre une opération banque. {@code balanceAfter} = solde faction après mutation.
     */
    public void logTransaction(int factionId, UUID playerUuid, String action, double amount,
                               double balanceAfter, String reason) {
        String playerName = resolvePlayerName(playerUuid);
        long now = System.currentTimeMillis() / 1000L;
        String sql = """
            INSERT INTO faction_bank_log
                (faction_id, player_uuid, player_name, action, amount, balance_after, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, playerUuid != null ? playerUuid.toString() : null);
            stmt.setString(3, playerName);
            stmt.setString(4, action);
            stmt.setDouble(5, amount);
            stmt.setDouble(6, balanceAfter);
            stmt.setString(7, reason);
            stmt.setLong(8, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur logTransaction : " + e.getMessage());
        }
    }

    public int countHistory(int factionId) {
        String sql = "SELECT COUNT(*) FROM faction_bank_log WHERE faction_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur countHistory : " + e.getMessage());
        }
        return 0;
    }

    public List<BankTransaction> getHistory(int factionId, int limit) {
        return getHistory(factionId, limit, 0);
    }

    public List<BankTransaction> getHistory(int factionId, int limit, int offset) {
        List<BankTransaction> transactions = new ArrayList<>();
        String sql = """
            SELECT id, faction_id, player_uuid, player_name, action, amount, balance_after, reason, created_at
            FROM faction_bank_log
            WHERE faction_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setInt(2, Math.max(1, limit));
            stmt.setInt(3, Math.max(0, offset));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(readTransaction(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getHistory : " + e.getMessage());
        }
        return transactions;
    }

    private static BankTransaction readTransaction(ResultSet rs) throws SQLException {
        double balanceAfter = rs.getDouble("balance_after");
        if (rs.wasNull()) {
            balanceAfter = Double.NaN;
        }
        return new BankTransaction(
                rs.getInt("id"),
                rs.getInt("faction_id"),
                rs.getString("player_uuid"),
                rs.getString("player_name"),
                rs.getString("action"),
                rs.getDouble("amount"),
                balanceAfter,
                rs.getString("reason"),
                rs.getLong("created_at")
        );
    }

    private static String resolvePlayerName(UUID playerUuid) {
        if (playerUuid == null) return null;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerUuid);
        String name = offline.getName();
        return name != null ? name : playerUuid.toString().substring(0, 8);
    }

    public static class BankTransaction {
        public final int id;
        public final int factionId;
        public final String playerUuid;
        public final String playerName;
        public final String action;
        public final double amount;
        /** {@link Double#NaN} si inconnu (anciennes lignes). */
        public final double balanceAfter;
        public final String reason;
        public final long createdAt;

        public BankTransaction(int id, int factionId, String playerUuid, String playerName,
                               String action, double amount, double balanceAfter,
                               String reason, long createdAt) {
            this.id = id;
            this.factionId = factionId;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.action = action;
            this.amount = amount;
            this.balanceAfter = balanceAfter;
            this.reason = reason;
            this.createdAt = createdAt;
        }

        public String displayName() {
            if (playerName != null && !playerName.isBlank()) return playerName;
            if (playerUuid == null || playerUuid.isBlank()) return "Système";
            return playerUuid.length() >= 8 ? playerUuid.substring(0, 8) : playerUuid;
        }
    }
}
