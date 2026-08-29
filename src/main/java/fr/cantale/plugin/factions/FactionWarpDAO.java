package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès à la table historique {@code faction_warps} (réutilisée par {@code /fw}).
 */
public class FactionWarpDAO {

    private static final String SELECT_COLUMNS =
            "fw.id, fw.faction_id, fw.name, f.name AS faction_name, fw.world, fw.x, fw.y, fw.z";

    private final CantalePlugin plugin;

    public FactionWarpDAO(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<FactionWarp> findByName(String name) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM faction_warps fw LEFT JOIN factions f ON f.id = fw.faction_id"
                + " WHERE LOWER(fw.name) = LOWER(?) LIMIT 1";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, name);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return Optional.of(extract(rs));
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.findByName : " + ex.getMessage());
        }
        return Optional.empty();
    }

    public List<FactionWarp> findByFaction(int factionId) {
        List<FactionWarp> out = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM faction_warps fw LEFT JOIN factions f ON f.id = fw.faction_id"
                + " WHERE fw.faction_id = ? ORDER BY fw.name";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setInt(1, factionId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                out.add(extract(rs));
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.findByFaction : " + ex.getMessage());
        }
        return out;
    }

    public List<FactionWarp> findAll() {
        List<FactionWarp> out = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM faction_warps fw LEFT JOIN factions f ON f.id = fw.faction_id"
                + " ORDER BY fw.name";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(extract(rs));
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.findAll : " + ex.getMessage());
        }
        return out;
    }

    public int countByFaction(int factionId) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT COUNT(*) FROM faction_warps WHERE faction_id = ?")) {
            st.setInt(1, factionId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.countByFaction : " + ex.getMessage());
        }
        return 0;
    }

    public boolean insert(int factionId, String name, Location loc) {
        String sql = "INSERT INTO faction_warps (faction_id, name, world, x, y, z) VALUES (?,?,?,?,?,?)";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setInt(1, factionId);
            st.setString(2, name);
            st.setString(3, loc.getWorld().getName());
            st.setDouble(4, loc.getX());
            st.setDouble(5, loc.getY());
            st.setDouble(6, loc.getZ());
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.insert : " + ex.getMessage());
            return false;
        }
    }

    public boolean updateLocation(int id, Location loc) {
        String sql = "UPDATE faction_warps SET world = ?, x = ?, y = ?, z = ? WHERE id = ?";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, loc.getWorld().getName());
            st.setDouble(2, loc.getX());
            st.setDouble(3, loc.getY());
            st.setDouble(4, loc.getZ());
            st.setInt(5, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.updateLocation : " + ex.getMessage());
            return false;
        }
    }

    public boolean deleteById(int id) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement("DELETE FROM faction_warps WHERE id = ?")) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("FactionWarpDAO.deleteById : " + ex.getMessage());
            return false;
        }
    }

    private static FactionWarp extract(ResultSet rs) throws SQLException {
        return new FactionWarp(
                rs.getInt("id"),
                rs.getInt("faction_id"),
                rs.getString("name"),
                rs.getString("faction_name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z")
        );
    }
}
