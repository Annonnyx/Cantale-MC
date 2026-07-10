package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionRank;

import java.sql.*;
import java.util.*;

public class FactionDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public FactionDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public int createFaction(Faction faction) {
        String sql = "INSERT INTO factions (name, tag, description, leader_uuid, created_at, balance, power, type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, faction.getName());
            stmt.setString(2, faction.getTag());
            stmt.setString(3, faction.getDescription());
            stmt.setString(4, faction.getLeaderUuid().toString());
            stmt.setLong(5, faction.getCreatedAt());
            stmt.setDouble(6, faction.getBalance());
            stmt.setInt(7, faction.getPower());
            stmt.setString(8, faction.getType());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                faction.setId(id);

                // Add leader as member
                addMember(id, faction.getLeaderUuid(), FactionRank.LEADER);

                // Create initial claims
                int startChunks = plugin.getConfigManager().getStartChunks();
                // TODO: Create claims around spawn location

                return id;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createFaction : " + e.getMessage());
        }
        return -1;
    }

    public Faction getFactionById(int id) {
        String sql = "SELECT * FROM factions WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Faction faction = extractFaction(rs);
                loadMembers(faction);
                loadClaims(faction);
                return faction;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFactionById : " + e.getMessage());
        }
        return null;
    }

    public Faction getFactionByName(String name) {
        String sql = "SELECT * FROM factions WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Faction faction = extractFaction(rs);
                loadMembers(faction);
                loadClaims(faction);
                return faction;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFactionByName : " + e.getMessage());
        }
        return null;
    }

    public Faction getFactionByTag(String tag) {
        String sql = "SELECT * FROM factions WHERE tag = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tag);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Faction faction = extractFaction(rs);
                loadMembers(faction);
                loadClaims(faction);
                return faction;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFactionByTag : " + e.getMessage());
        }
        return null;
    }

    public Faction getFactionByPlayer(UUID playerUuid) {
        String sql = "SELECT f.* FROM factions f " +
                "INNER JOIN faction_members fm ON f.id = fm.faction_id " +
                "WHERE fm.player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Faction faction = extractFaction(rs);
                loadMembers(faction);
                loadClaims(faction);
                return faction;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFactionByPlayer : " + e.getMessage());
        }
        return null;
    }

    public List<Faction> getAllFactions() {
        List<Faction> factions = new ArrayList<>();
        String sql = "SELECT * FROM factions";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Faction faction = extractFaction(rs);
                loadMembers(faction);
                loadClaims(faction);
                factions.add(faction);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getAllFactions : " + e.getMessage());
        }
        return factions;
    }

    public boolean deleteFaction(int id) {
        String sql = "DELETE FROM factions WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteFaction : " + e.getMessage());
        }
        return false;
    }

    public void updatePower(int factionId, int power) {
        String sql = "UPDATE factions SET power = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, power);
            stmt.setInt(2, factionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateFaction(Faction faction) {
        String sql = "UPDATE factions SET name = ?, tag = ?, description = ?, leader_uuid = ?, " +
                "balance = ?, power = ?, type = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_world = ?, secret_until = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, faction.getName());
            stmt.setString(2, faction.getTag());
            stmt.setString(3, faction.getDescription());
            stmt.setString(4, faction.getLeaderUuid().toString());
            stmt.setDouble(5, faction.getBalance());
            stmt.setInt(6, faction.getPower());
            stmt.setString(7, faction.getType());
            stmt.setDouble(8, faction.getSpawnX() != null ? faction.getSpawnX() : 0);
            stmt.setDouble(9, faction.getSpawnY() != null ? faction.getSpawnY() : 0);
            stmt.setDouble(10, faction.getSpawnZ() != null ? faction.getSpawnZ() : 0);
            stmt.setString(11, faction.getSpawnWorld());
            stmt.setLong(12, faction.getSecretUntil());
            stmt.setInt(13, faction.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateFaction : " + e.getMessage());
        }
    }

    private Faction extractFaction(ResultSet rs) throws SQLException {
        int power = 0;
        String type = "normal";
        try { power = rs.getInt("power"); } catch (SQLException e) {}
        try { type = rs.getString("type"); if (type == null) type = "normal"; } catch (SQLException e) {}

        Faction faction = new Faction(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("tag"),
                rs.getString("description"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getLong("created_at"),
                rs.getDouble("balance"),
                power,
                type
        );

        // Load spawn coordinates (may not exist in old databases)
        try {
            double sx = rs.getDouble("spawn_x");
            if (!rs.wasNull()) faction.setSpawnX(sx);
            double sy = rs.getDouble("spawn_y");
            if (!rs.wasNull()) faction.setSpawnY(sy);
            double sz = rs.getDouble("spawn_z");
            if (!rs.wasNull()) faction.setSpawnZ(sz);
        } catch (SQLException e) {
            // Colonnes spawn inexistantes - ignoré
        }
        try {
            faction.setSpawnWorld(rs.getString("spawn_world"));
        } catch (SQLException e) {
            // Colonne spawn_world inexistante - ignoré
        }

        // Load secret status
        faction.setSecretUntil(rs.getLong("secret_until"));

        return faction;
    }

    private void loadMembers(Faction faction) {
        String sql = "SELECT player_uuid, rank FROM faction_members WHERE faction_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, faction.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                FactionRank rank = FactionRank.fromString(rs.getString("rank"));
                faction.getMembers().add(uuid);
                faction.getMemberRanks().put(uuid, rank);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadMembers : " + e.getMessage());
        }
    }

    private void loadClaims(Faction faction) {
        String sql = "SELECT * FROM claims WHERE faction_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, faction.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Claim claim = new Claim(
                        rs.getInt("id"),
                        rs.getInt("faction_id"),
                        rs.getString("world"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z"),
                        rs.getLong("created_at"),
                        rs.getInt("pasdic") == 1
                );
                faction.getClaims().add(claim);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadClaims : " + e.getMessage());
        }
    }

    public void addMember(int factionId, UUID playerUuid, FactionRank rank) {
        String sql = "INSERT INTO faction_members (faction_id, player_uuid, rank) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, rank.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur addMember : " + e.getMessage());
        }
    }

    public void removeMember(int factionId, UUID playerUuid) {
        String sql = "DELETE FROM faction_members WHERE faction_id = ? AND player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeMember : " + e.getMessage());
        }
    }

    public void updateMemberRank(int factionId, UUID playerUuid, FactionRank rank) {
        String sql = "UPDATE faction_members SET rank = ? WHERE faction_id = ? AND player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rank.name());
            stmt.setInt(2, factionId);
            stmt.setString(3, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateMemberRank : " + e.getMessage());
        }
    }

    public void addClaim(Claim claim) {
        String sql = "INSERT INTO claims (faction_id, world, chunk_x, chunk_z, pasdic) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, claim.getFactionId());
            stmt.setString(2, claim.getWorld());
            stmt.setInt(3, claim.getChunkX());
            stmt.setInt(4, claim.getChunkZ());
            stmt.setInt(5, claim.isPasdic() ? 1 : 0);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                claim.setId(rs.getInt(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur addClaim : " + e.getMessage());
        }
    }

    public void removeClaim(int claimId) {
        String sql = "DELETE FROM claims WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, claimId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeClaim : " + e.getMessage());
        }
    }

    public void updateClaimPasdic(int claimId, boolean pasdic) {
        String sql = "UPDATE claims SET pasdic = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pasdic ? 1 : 0);
            stmt.setInt(2, claimId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateClaimPasdic : " + e.getMessage());
        }
    }

    public Claim getClaimAt(String world, int chunkX, int chunkZ) {
        String sql = "SELECT * FROM claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Claim(
                        rs.getInt("id"),
                        rs.getInt("faction_id"),
                        rs.getString("world"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z"),
                        rs.getLong("created_at"),
                        rs.getInt("pasdic") == 1
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getClaimAt : " + e.getMessage());
        }
        return null;
    }

    public Map<String, String> getPermissions(int factionId, String rank) {
        Map<String, String> permissions = new HashMap<>();
        String sql = "SELECT permission, value FROM faction_permissions WHERE faction_id = ? AND rank = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, rank);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                permissions.put(rs.getString("permission"), rs.getString("value"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPermissions : " + e.getMessage());
        }
        return permissions;
    }

    public void setPermission(int factionId, String rank, String permission, String value) {
        String sql = "INSERT INTO faction_permissions (faction_id, rank, permission, value) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(faction_id, rank, permission) DO UPDATE SET value = excluded.value";
        if (databaseManager.isSQLite()) {
            sql = "INSERT OR REPLACE INTO faction_permissions (faction_id, rank, permission, value) " +
                    "VALUES (?, ?, ?, ?)";
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setString(2, rank);
            stmt.setString(3, permission);
            stmt.setString(4, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setPermission : " + e.getMessage());
        }
    }

    public int getClaimCount(int factionId) {
        String sql = "SELECT COUNT(*) FROM claims WHERE faction_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getClaimCount : " + e.getMessage());
        }
        return 0;
    }

    public void removeFactionBalance(int factionId, double amount) {
        String sql = "UPDATE factions SET balance = balance - ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, factionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeFactionBalance : " + e.getMessage());
        }
    }

    public void removeOldestClaims(int factionId, int count) {
        String sql = "DELETE FROM claims WHERE id IN (SELECT id FROM claims WHERE faction_id = ? ORDER BY created_at ASC LIMIT ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.setInt(2, count);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeOldestClaims : " + e.getMessage());
        }
    }
}
