package fr.cantale.plugin.spawners;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class SpawnerDAO {

    private final CantalePlugin plugin;

    public SpawnerDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public List<SpawnerRecord> loadAll() {
        List<SpawnerRecord> list = new ArrayList<>();
        String sql = "SELECT world, x, y, z, mob_type, tier, faction_id FROM spawners";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Optional<SpawnerMob> mob = SpawnerMob.fromName(rs.getString("mob_type"));
                if (mob.isEmpty()) continue;
                list.add(new SpawnerRecord(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        mob.get(),
                        rs.getInt("tier"),
                        rs.getInt("faction_id")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur loadAll spawners: " + e.getMessage(), e);
        }
        return list;
    }

    public void upsert(SpawnerRecord record) {
        String sql = plugin.getDatabaseManager().upsertSql(
                "spawners (world, x, y, z, mob_type, tier, faction_id)",
                "?, ?, ?, ?, ?, ?, ?",
                "mob_type = VALUES(mob_type), tier = VALUES(tier), faction_id = VALUES(faction_id)"
        );
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.world());
            stmt.setInt(2, record.x());
            stmt.setInt(3, record.y());
            stmt.setInt(4, record.z());
            stmt.setString(5, record.mob().name());
            stmt.setInt(6, record.tier());
            stmt.setInt(7, record.factionId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur upsert spawner: " + e.getMessage(), e);
        }
    }

    public void delete(String world, int x, int y, int z) {
        String sql = "DELETE FROM spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur delete spawner: " + e.getMessage(), e);
        }
    }

    public boolean chunkHasSpawners(String world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        String sql = "SELECT 1 FROM spawners WHERE world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? LIMIT 1";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, minX);
            stmt.setInt(3, maxX);
            stmt.setInt(4, minZ);
            stmt.setInt(5, maxZ);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chunkHasSpawners: " + e.getMessage(), e);
            return false;
        }
    }
}
