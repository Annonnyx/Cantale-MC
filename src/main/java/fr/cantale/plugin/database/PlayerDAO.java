package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.ItemStackSerializer;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.UUID;

public class PlayerDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public PlayerDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void createOrUpdatePlayer(UUID uuid, String username) {
        String sql = databaseManager.isSQLite()
                ? "INSERT INTO players (uuid, username, lives, balance, deaths, kills, kill_streak, last_death, tutorial_progress) " +
                  "VALUES (?, ?, ?, ?, ?, 0, 0, ?, 0) " +
                  "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username"
                : "INSERT INTO players (uuid, username, lives, balance, deaths, kills, kill_streak, last_death, tutorial_progress) " +
                  "VALUES (?, ?, ?, ?, ?, 0, 0, ?, 0) " +
                  "ON DUPLICATE KEY UPDATE username = VALUES(username)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int defaultLives = plugin.getConfigManager().getDefaultLives();
            double startBalance = plugin.getConfigManager().getStartBalance();

            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setInt(3, defaultLives);
            stmt.setDouble(4, startBalance);
            stmt.setInt(5, 0);
            stmt.setLong(6, 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createOrUpdatePlayer : " + e.getMessage());
        }
    }

    /**
     * Lit les vies en DB. Fail-closed : ligne absente / erreur SQL → 0
     * (jamais le default 3 — sinon un ban/mort à 0 vie peut « réapparaître » avec 3 vies).
     * Les nouveaux joueurs doivent passer par {@link #createOrUpdatePlayer} avant.
     */
    public int getLives(UUID uuid) {
        String sql = "SELECT lives FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("lives");
            }
            plugin.getLogger().warning("getLives: aucune ligne pour " + uuid + " → 0 (fail-closed)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLives : " + e.getMessage());
        }
        return 0;
    }

    public void setLives(UUID uuid, int lives) {
        int value = Math.max(lives, plugin.getConfigManager().getMinLives());
        String sql = "UPDATE players SET lives = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, value);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                plugin.getLogger().warning("setLives: 0 ligne mise à jour pour " + uuid
                    + " (valeur demandée=" + value + ") — upsert de secours");
                createOrUpdatePlayer(uuid, "unknown");
                try (PreparedStatement retry = conn.prepareStatement(sql)) {
                    retry.setInt(1, value);
                    retry.setString(2, uuid.toString());
                    retry.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLives : " + e.getMessage());
        }
    }

    /**
     * Passe le joueur à {@code lives} uniquement s'il est encore à 0. Retourne false si une vie est déjà là.
     */
    public boolean grantLifeIfDepleted(UUID uuid, int lives) {
        if (uuid == null || lives <= 0) {
            return false;
        }
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null) {
                return false;
            }
            return PlayerLifeRepository.grantIfDepleted(conn, uuid, lives);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur grantLifeIfDepleted : " + e.getMessage());
            return false;
        }
    }

    public void decrementLives(UUID uuid) {
        int current = getLives(uuid);
        setLives(uuid, current - 1);
    }

    public void incrementLives(UUID uuid) {
        int current = getLives(uuid);
        setLives(uuid, current + 1);
    }

    public double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getBalance : " + e.getMessage());
        }
        return plugin.getConfigManager().getStartBalance();
    }

    public void setBalance(UUID uuid, double balance) {
        String sql = "UPDATE players SET balance = ?, balance_peak = CASE WHEN ? > COALESCE(balance_peak, 0) THEN ? ELSE balance_peak END WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, balance);
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.setString(4, uuid.toString());
            stmt.executeUpdate();
            if (plugin.getCollectionManager() != null) {
                plugin.getCollectionManager().set(uuid, "money", "PEAK", (long) balance);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setBalance : " + e.getMessage());
        }
    }

    public double getBalancePeak(UUID uuid) {
        String sql = "SELECT COALESCE(balance_peak, 0) FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getBalancePeak : " + e.getMessage());
        }
        return 0;
    }

    public long getPlaytime(UUID uuid) {
        String sql = "SELECT COALESCE(playtime, 0) FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPlaytime : " + e.getMessage());
        }
        return 0;
    }

    public void setLastIp(UUID uuid, String ip) {
        if (uuid == null || ip == null || ip.isBlank()) return;
        String sql = "UPDATE players SET last_ip = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip.trim());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setLastIp : " + e.getMessage());
        }
    }

    public String getLastIp(UUID uuid) {
        String sql = "SELECT last_ip FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String ip = rs.getString("last_ip");
                return ip == null || ip.isBlank() ? null : ip;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastIp : " + e.getMessage());
        }
        return null;
    }

    /**
     * Ajoute ou retire du Cantox en une requête. Un delta négatif est refusé si le solde deviendrait négatif.
     */
    public boolean applyBalanceDelta(UUID uuid, double delta) {
        if (uuid == null || delta == 0) {
            return delta == 0 && uuid != null;
        }
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("Solde non modifié : pool SQL indisponible.");
                return false;
            }
            return PlayerBalanceRepository.applyDelta(conn, uuid, delta);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur applyBalanceDelta : " + e.getMessage());
            return false;
        }
    }

    private void refreshBalancePeak(UUID uuid) {
        if (plugin.getCollectionManager() == null) {
            return;
        }
        plugin.getCollectionManager().set(uuid, "money", "PEAK", (long) getBalance(uuid));
    }

    public boolean addBalance(UUID uuid, double amount) {
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        boolean updated = applyBalanceDelta(uuid, amount);
        if (updated) {
            refreshBalancePeak(uuid);
        }
        return updated;
    }

    public boolean removeBalance(UUID uuid, double amount) {
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        return applyBalanceDelta(uuid, -amount);
    }

    public boolean transferBalance(UUID from, UUID to, double amount) {
        if (from == null || to == null || amount < 0 || from.equals(to)) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("Transfert Cantox impossible : pool SQL indisponible.");
                return false;
            }
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                if (!PlayerBalanceRepository.applyDelta(conn, from, -amount)) {
                    conn.rollback();
                    return false;
                }
                if (!PlayerBalanceRepository.applyDelta(conn, to, amount)) {
                    conn.rollback();
                    return false;
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Transfert Cantox échoué : " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Transfert Cantox échoué : " + e.getMessage());
            return false;
        }
        refreshBalancePeak(to);
        return true;
    }

    public int getDeaths(UUID uuid) {
        String sql = "SELECT deaths FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("deaths");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getDeaths : " + e.getMessage());
        }
        return 0;
    }

    public void incrementDeaths(UUID uuid) {
        incrementDeaths(uuid, null, null);
    }

    public void incrementDeaths(UUID uuid, Location location) {
        incrementDeaths(uuid, location, null);
    }

    /**
     * Incrémente les morts et horodate {@code last_death}.
     * Si {@code location} est fournie, enregistre aussi world+x+y+z.
     * Si {@code inventorySnapshot} est fourni, sérialise l'inventaire (stockage+armure+offhand)
     * pour la vue staff {@code /lastdeath} — migration-safe (colonne TEXT nullable).
     */
    public void incrementDeaths(UUID uuid, Location location, ItemStack[] inventorySnapshot) {
        boolean hasLoc = location != null && location.getWorld() != null;
        String invData = inventorySnapshot != null ? ItemStackSerializer.toBase64(inventorySnapshot) : null;
        boolean hasInv = invData != null && !invData.isEmpty();

        StringBuilder sql = new StringBuilder(
                "UPDATE players SET deaths = deaths + 1, last_death = ?, kill_streak = 0");
        if (hasLoc) {
            sql.append(", last_death_world = ?, last_death_x = ?, last_death_y = ?, last_death_z = ?");
        }
        if (hasInv) {
            sql.append(", last_death_inventory = ?");
        }
        sql.append(" WHERE uuid = ?");

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int i = 1;
            stmt.setLong(i++, System.currentTimeMillis() / 1000);
            if (hasLoc) {
                stmt.setString(i++, location.getWorld().getName());
                stmt.setDouble(i++, location.getX());
                stmt.setDouble(i++, location.getY());
                stmt.setDouble(i++, location.getZ());
            }
            if (hasInv) {
                stmt.setString(i++, invData);
            }
            stmt.setString(i, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur incrementDeaths : " + e.getMessage());
        }
    }

    /**
     * Inventaire à la dernière mort (null si jamais enregistré — morts avant migration).
     */
    public ItemStack[] getLastDeathInventory(UUID uuid) {
        String sql = "SELECT last_death_inventory FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return ItemStackSerializer.fromBase64(rs.getString("last_death_inventory"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastDeathInventory : " + e.getMessage());
        }
        return null;
    }

    public long getLastDeath(UUID uuid) {
        String sql = "SELECT last_death FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_death");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastDeath : " + e.getMessage());
        }
        return 0;
    }

    /**
     * Lieu de la dernière mort (null si jamais mort ou coords absentes — morts avant migration).
     */
    public LastDeathLocation getLastDeathLocation(UUID uuid) {
        String sql = "SELECT last_death_world, last_death_x, last_death_y, last_death_z FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String world = rs.getString("last_death_world");
                if (world == null || world.isBlank()) {
                    return null;
                }
                double x = rs.getDouble("last_death_x");
                if (rs.wasNull()) {
                    return null;
                }
                double y = rs.getDouble("last_death_y");
                double z = rs.getDouble("last_death_z");
                return new LastDeathLocation(world, x, y, z);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getLastDeathLocation : " + e.getMessage());
        }
        return null;
    }

    public UUID getUuidByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String sql = "SELECT uuid FROM players WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.trim());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String uuidStr = rs.getString("uuid");
                if (uuidStr != null && !uuidStr.isBlank()) {
                    return UUID.fromString(uuidStr);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getUuidByUsername : " + e.getMessage());
        }
        return null;
    }

    /** Pseudo connu en DB pour cet UUID, ou {@code null} si inconnu. */
    public String getUsername(UUID uuid) {
        if (uuid == null) return null;
        String sql = "SELECT username FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String name = rs.getString("username");
                if (name != null && !name.isBlank()) return name;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getUsername : " + e.getMessage());
        }
        return null;
    }

    public int getKills(UUID uuid) {
        String sql = "SELECT kills FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("kills");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getKills : " + e.getMessage());
        }
        return 0;
    }

    public void incrementKills(UUID uuid) {
        String sql = "UPDATE players SET kills = kills + 1, kill_streak = kill_streak + 1 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur incrementKills : " + e.getMessage());
        }
    }

    public void incrementChatReactions(UUID uuid) {
        String sql = "UPDATE players SET chat_reactions = chat_reactions + 1 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur incrementChatReactions : " + e.getMessage());
        }
    }

    public int getChatReactions(UUID uuid) {
        String sql = "SELECT chat_reactions FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("chat_reactions");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getChatReactions : " + e.getMessage());
        }
        return 0;
    }

    /** Préférence BossBar infos : true par défaut si absent. */
    public boolean isBossBarEnabled(UUID uuid) {
        String sql = "SELECT boss_bar_enabled FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("boss_bar_enabled") != 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur isBossBarEnabled : " + e.getMessage());
        }
        return true;
    }

    public void setBossBarEnabled(UUID uuid, boolean enabled) {
        String sql = "UPDATE players SET boss_bar_enabled = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, enabled ? 1 : 0);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setBossBarEnabled : " + e.getMessage());
        }
    }

    public int getKillStreak(UUID uuid) {
        String sql = "SELECT kill_streak FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("kill_streak");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getKillStreak : " + e.getMessage());
        }
        return 0;
    }

    public void resetKillStreak(UUID uuid) {
        String sql = "UPDATE players SET kill_streak = 0 WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur resetKillStreak : " + e.getMessage());
        }
    }

    public boolean playerExists(UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur playerExists : " + e.getMessage());
        }
        return false;
    }

    public int getTutorialProgress(UUID uuid) {
        String sql = "SELECT tutorial_progress FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("tutorial_progress");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getTutorialProgress : " + e.getMessage());
        }
        return 0;
    }

    public void setTutorialProgress(UUID uuid, int progress) {
        String sql = "UPDATE players SET tutorial_progress = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, progress);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setTutorialProgress : " + e.getMessage());
        }
    }

    public long getCreatedAt(UUID uuid) {
        String sql = "SELECT created_at FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("created_at");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getCreatedAt : " + e.getMessage());
        }
        return 0;
    }

    /**
     * Récupère la liste des joueurs morts définitivement (0 vies) triés par date de mort
     */
    public java.util.List<DeadPlayer> getPermanentlyDeadPlayers() {
        java.util.List<DeadPlayer> deadPlayers = new java.util.ArrayList<>();
        String sql = "SELECT uuid, username, deaths, last_death FROM players WHERE lives <= 0 AND last_death > 0 ORDER BY last_death DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                DeadPlayer player = new DeadPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("username"),
                    rs.getInt("deaths"),
                    rs.getLong("last_death")
                );
                deadPlayers.add(player);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPermanentlyDeadPlayers : " + e.getMessage());
        }
        return deadPlayers;
    }

    public void updatePlaytime(UUID uuid, long playtimeSeconds) {
        String sql = "UPDATE players SET playtime = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Long (pas Int) : aligne BIGINT côté snapshots site + évite le troncage.
            stmt.setLong(1, playtimeSeconds);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updatePlaytime : " + e.getMessage());
        }
    }

    /** True si le joueur a déjà utilisé /parrain avec succès. */
    public boolean hasUsedReferral(UUID uuid) {
        String sql = "SELECT referral_used FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("referral_used") != 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur hasUsedReferral : " + e.getMessage());
        }
        return false;
    }

    /**
     * Marque atomiquement l'utilisation de /parrain (une seule fois).
     * @return true si le marquage a réussi (première utilisation)
     */
    public boolean tryMarkReferralUsed(UUID inviteeUuid, UUID sponsorUuid) {
        String sql = "UPDATE players SET referral_used = 1, referred_by = ?, referral_at = ? "
                + "WHERE uuid = ? AND (referral_used IS NULL OR referral_used = 0)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sponsorUuid.toString());
            // Epoch en secondes (convention du plugin) : referral_at est un INTEGER
            stmt.setLong(2, System.currentTimeMillis() / 1000L);
            stmt.setString(3, inviteeUuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur tryMarkReferralUsed : " + e.getMessage());
        }
        return false;
    }

    /** Coordonnées persistées de la dernière mort. */
    public static class LastDeathLocation {
        private final String world;
        private final double x;
        private final double y;
        private final double z;

        public LastDeathLocation(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
    }

    /**
     * Classe interne représentant un joueur mort définitivement
     */
    public static class DeadPlayer {
        private final UUID uuid;
        private final String username;
        private final int deaths;
        private final long lastDeath;

        public DeadPlayer(UUID uuid, String username, int deaths, long lastDeath) {
            this.uuid = uuid;
            this.username = username;
            this.deaths = deaths;
            this.lastDeath = lastDeath;
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public int getDeaths() { return deaths; }
        public long getLastDeath() { return lastDeath; }
    }
}
