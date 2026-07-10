package fr.cantale.plugin.economy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final FactionDAO factionDAO;

    public BankManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.factionDAO = new FactionDAO(plugin);
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

        logTransaction(factionId, playerUuid, "DEPOSIT", amount, "Dépôt par joueur");
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

        logTransaction(factionId, playerUuid, "WITHDRAW", amount, "Retrait par joueur");
        return true;
    }

    public void logTransaction(int factionId, UUID playerUuid, String action, double amount, String reason) {
        String sql = "INSERT INTO faction_bank_log (faction_id, player_uuid, action, amount, reason) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, playerUuid != null ? playerUuid.toString() : null);
            stmt.setString(3, action);
            stmt.setDouble(4, amount);
            stmt.setString(5, reason);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur logTransaction : " + e.getMessage());
        }
    }

    public List<BankTransaction> getHistory(int factionId, int limit) {
        List<BankTransaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM faction_bank_log WHERE faction_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(new BankTransaction(
                        rs.getInt("id"),
                        rs.getInt("faction_id"),
                        rs.getString("player_uuid"),
                        rs.getString("action"),
                        rs.getDouble("amount"),
                        rs.getString("reason"),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getHistory : " + e.getMessage());
        }
        return transactions;
    }

    public static class BankTransaction {
        public final int id;
        public final int factionId;
        public final String playerUuid;
        public final String action;
        public final double amount;
        public final String reason;
        public final long createdAt;

        public BankTransaction(int id, int factionId, String playerUuid, String action, double amount, String reason, long createdAt) {
            this.id = id;
            this.factionId = factionId;
            this.playerUuid = playerUuid;
            this.action = action;
            this.amount = amount;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }
}
