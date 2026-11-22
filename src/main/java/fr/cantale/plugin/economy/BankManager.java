package fr.cantale.plugin.economy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionBalanceRepository;
import fr.cantale.plugin.database.PlayerBalanceRepository;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankManager {

    public static final String ACTION_DEPOSIT = "DEPOSIT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_POWER_BUY = "POWER_BUY";
    public static final String ACTION_ADMIN_ADD = "ADMIN_ADD";
    public static final String ACTION_ADMIN_REMOVE = "ADMIN_REMOVE";
    public static final String ACTION_SECRET_TAX = "SECRET_TAX";
    public static final String ACTION_PASDIC = "PASDIC";
    public static final String ACTION_WAR_REPARATION = "WAR_REPARATION";

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
        if (amount <= 0 || playerUuid == null) return false;
        Faction faction = factionManager.getFaction(factionId);
        if (faction == null) return false;

        Double updated = moveBalances(factionId, playerUuid, amount, -amount);
        if (updated == null) return false;
        if (!updated.isNaN()) {
            faction.setBalance(updated);
        }
        logTransaction(factionId, playerUuid, ACTION_DEPOSIT, amount, updated, "Dépôt par joueur");
        return true;
    }

    public boolean withdraw(int factionId, UUID playerUuid, double amount) {
        if (amount <= 0 || playerUuid == null) return false;
        Faction faction = factionManager.getFaction(factionId);
        if (faction == null) return false;

        Double updated = moveBalances(factionId, playerUuid, -amount, amount);
        if (updated == null) return false;
        if (!updated.isNaN()) {
            faction.setBalance(updated);
        }
        if (plugin.getCollectionManager() != null) {
            plugin.getCollectionManager().set(playerUuid, "money", "PEAK",
                    (long) new PlayerDAO(plugin).getBalance(playerUuid));
        }
        logTransaction(factionId, playerUuid, ACTION_WITHDRAW, amount, updated, "Retrait par joueur");
        return true;
    }

    /**
     * Débit et crédit dans la même transaction.
     * {@code null} si le solde joueur ou faction deviendrait négatif, ou si aucune ligne n'est touchée.
     */
    private Double moveBalances(int factionId, UUID playerUuid, double factionDelta, double playerDelta) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("Mouvement de banque refusé : pool SQL indisponible.");
                return null;
            }
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (!PlayerBalanceRepository.applyDelta(conn, playerUuid, playerDelta)) {
                    conn.rollback();
                    return null;
                }
                if (!FactionBalanceRepository.applyDelta(conn, factionId, factionDelta)) {
                    conn.rollback();
                    return null;
                }
                conn.commit();
                committed = true;
                return FactionBalanceRepository.readBalance(conn, factionId);
            } catch (SQLException e) {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // la connexion sera rendue au pool
                    }
                    plugin.getLogger().severe("Mouvement de banque échoué : " + e.getMessage());
                    return null;
                }
                plugin.getLogger().severe("Banque mise à jour, solde non relu : " + e.getMessage());
                return Double.NaN;
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Mouvement de banque échoué : " + e.getMessage());
            return null;
        }
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

    private BankTransaction readTransaction(ResultSet rs) throws SQLException {
        double balanceAfter = rs.getDouble("balance_after");
        if (rs.wasNull()) {
            balanceAfter = Double.NaN;
        }
        String storedName = rs.getString("player_name");
        String playerUuid = rs.getString("player_uuid");
        String resolvedName = resolveDisplayName(storedName, playerUuid);
        long createdAt = readCreatedAtSeconds(rs);
        return new BankTransaction(
                rs.getInt("id"),
                rs.getInt("faction_id"),
                playerUuid,
                resolvedName,
                rs.getString("action"),
                rs.getDouble("amount"),
                balanceAfter,
                rs.getString("reason"),
                createdAt
        );
    }

    /**
     * Lit {@code created_at} en secondes Unix, y compris colonnes TIMESTAMP JDBC
     * ou anciennes valeurs stockées en millisecondes.
     */
    private static long readCreatedAtSeconds(ResultSet rs) throws SQLException {
        long raw = rs.getLong("created_at");
        if (!rs.wasNull() && raw > 0) {
            return normalizeEpochSeconds(raw);
        }
        try {
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) {
                return normalizeEpochSeconds(ts.getTime());
            }
        } catch (SQLException ignored) {
            // colonne entière pure : pas de Timestamp
        }
        return 0L;
    }

    /** Accepte secondes ou millisecondes epoch. */
    public static long normalizeEpochSeconds(long value) {
        if (value <= 0) return 0L;
        // > ~année 2286 en secondes → traité comme millis
        if (value > 10_000_000_000L) {
            return value / 1000L;
        }
        return value;
    }

    private String resolveDisplayName(String storedName, String playerUuidStr) {
        if (isUsablePlayerName(storedName)) {
            return storedName.trim();
        }
        if (playerUuidStr == null || playerUuidStr.isBlank()) {
            return "Système";
        }
        try {
            UUID uuid = UUID.fromString(playerUuidStr.trim());
            String resolved = resolvePlayerName(uuid);
            if (isUsablePlayerName(resolved)) {
                return resolved.trim();
            }
        } catch (IllegalArgumentException ignored) {
            // uuid invalide
        }
        return "Joueur inconnu";
    }

    private String resolvePlayerName(UUID playerUuid) {
        if (playerUuid == null) return null;

        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null && online.getName() != null && !online.getName().isBlank()) {
            return online.getName();
        }

        String dbName = new PlayerDAO(plugin).getUsername(playerUuid);
        if (isUsablePlayerName(dbName)) {
            return dbName.trim();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerUuid);
        String name = offline.getName();
        if (isUsablePlayerName(name)) {
            return name.trim();
        }
        return null;
    }

    /** Nom affichable : pas vide, pas un fragment / UUID brut. */
    static boolean isUsablePlayerName(String name) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("Système") || trimmed.equalsIgnoreCase("Joueur inconnu")) {
            return true;
        }
        if (trimmed.length() == 36 && trimmed.chars().filter(c -> c == '-').count() == 4) {
            try {
                UUID.fromString(trimmed);
                return false;
            } catch (IllegalArgumentException ignored) {
                // pas un UUID
            }
        }
        // Ancien fallback : 8 premiers chars d'UUID hex
        if (trimmed.length() <= 8 && trimmed.matches("(?i)[0-9a-f]{8}")) {
            return false;
        }
        return true;
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
            if (isUsablePlayerName(playerName)) return playerName.trim();
            if (playerUuid == null || playerUuid.isBlank()) return "Système";
            return "Joueur inconnu";
        }
    }
}
