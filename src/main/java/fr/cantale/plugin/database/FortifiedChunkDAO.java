package fr.cantale.plugin.database;

import fr.cantale.plugin.CantalePlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAO pour les chunks fortifiés via Runes de Fortification.
 * 1 rune = 1 chunk entièrement protégé contre le break par les non-membres.
 */
public class FortifiedChunkDAO {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    // Cache : key = world,chunkX,chunkZ → factionId (-1 = absent en cache)
    private final Map<String, Integer> chunkCache = new ConcurrentHashMap<>();

    public FortifiedChunkDAO(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    private String key(String world, int chunkX, int chunkZ) {
        return world + "," + chunkX + "," + chunkZ;
    }

    /** -1 si pas fortifié, sinon factionId propriétaire. */
    public int getFortifyingFaction(String world, int chunkX, int chunkZ) {
        String k = key(world, chunkX, chunkZ);
        Integer cached = chunkCache.get(k);
        if (cached != null) return cached;

        String sql = "SELECT faction_id FROM fortified_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int factionId = rs.getInt("faction_id");
                chunkCache.put(k, factionId);
                return factionId;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getFortifyingFaction : " + e.getMessage());
        }
        chunkCache.put(k, -1);
        return -1;
    }

    public boolean isFortified(String world, int chunkX, int chunkZ) {
        return getFortifyingFaction(world, chunkX, chunkZ) > 0;
    }

    public boolean fortifyChunk(String world, int chunkX, int chunkZ, int factionId, UUID playerUuid) {
        String sql = "INSERT INTO fortified_chunks (world, chunk_x, chunk_z, faction_id, fortified_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            stmt.setInt(4, factionId);
            stmt.setString(5, playerUuid != null ? playerUuid.toString() : null);
            stmt.executeUpdate();
            chunkCache.put(key(world, chunkX, chunkZ), factionId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur fortifyChunk : " + e.getMessage());
            return false;
        }
    }

    public boolean unfortifyChunk(String world, int chunkX, int chunkZ) {
        String sql = "DELETE FROM fortified_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            int rows = stmt.executeUpdate();
            chunkCache.remove(key(world, chunkX, chunkZ));
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur unfortifyChunk : " + e.getMessage());
            return false;
        }
    }

    /** Compte le nombre de chunks fortifiés par une faction (pour calcul de taxe). */
    public int countByFaction(int factionId) {
        String sql = "SELECT COUNT(*) FROM fortified_chunks WHERE faction_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur countByFaction : " + e.getMessage());
        }
        return 0;
    }

    /** Map { factionId -> count } pour calcul global de taxe. */
    public Map<Integer, Integer> countAllByFaction() {
        Map<Integer, Integer> result = new HashMap<>();
        String sql = "SELECT faction_id, COUNT(*) AS c FROM fortified_chunks GROUP BY faction_id";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getInt("faction_id"), rs.getInt("c"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur countAllByFaction : " + e.getMessage());
        }
        return result;
    }

    /** Supprime tous les chunks fortifiés d'une faction (utilisé quand faction supprimée). */
    public void removeAllForFaction(int factionId) {
        String sql = "DELETE FROM fortified_chunks WHERE faction_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factionId);
            stmt.executeUpdate();
            chunkCache.entrySet().removeIf(e -> e.getValue() == factionId);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removeAllForFaction : " + e.getMessage());
        }
    }

    public void invalidateCache() {
        chunkCache.clear();
    }
}
