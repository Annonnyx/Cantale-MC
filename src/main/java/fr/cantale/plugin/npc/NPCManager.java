package fr.cantale.plugin.npc;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NPCManager {

    private final CantalePlugin plugin;
    private final Map<Integer, NPC> loadedNPCs;
    private static final int NPC_PRICE = 2000000; // 2M Cantix

    public NPCManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.loadedNPCs = new HashMap<>();
    }

    /**
     * Achète un NPC pour un joueur
     */
    public boolean buyNPC(Player player, String name, String type, Location location) {
        UUID uuid = player.getUniqueId();
        double balance = plugin.getEconomyManager().getBalance(uuid);

        if (balance < NPC_PRICE) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cVous n'avez pas assez d'argent ! Prix : &6" + NPC_PRICE + " &7Cantox"));
            return false;
        }

        // Déduire l'argent
        plugin.getEconomyManager().withdraw(player, NPC_PRICE);

        // Créer le NPC
        NPC npc = new NPC(0, name, uuid.toString(), type, location, "default");
        saveNPC(npc);
        spawnNPC(npc);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&aNPC &6" + name + " &aacheté avec succès pour &6" + NPC_PRICE + " &7Cantox !"));
        return true;
    }

    /**
     * Sauvegarde un NPC dans la base de données
     */
    private void saveNPC(NPC npc) {
        String sql = "INSERT INTO npcs (name, owner_uuid, type, world, x, y, z, yaw, pitch, skin) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, npc.getName());
            stmt.setString(2, npc.getOwnerUuid());
            stmt.setString(3, npc.getType());
            stmt.setString(4, npc.getLocation().getWorld().getName());
            stmt.setDouble(5, npc.getLocation().getX());
            stmt.setDouble(6, npc.getLocation().getY());
            stmt.setDouble(7, npc.getLocation().getZ());
            stmt.setFloat(8, npc.getLocation().getYaw());
            stmt.setFloat(9, npc.getLocation().getPitch());
            stmt.setString(10, npc.getSkin());
            
            stmt.executeUpdate();
            
            java.sql.ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                npc.setId(rs.getInt(1));
                loadedNPCs.put(npc.getId(), npc);
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveNPC : " + e.getMessage());
        }
    }

    /**
     * Spawn un NPC dans le monde
     */
    private void spawnNPC(NPC npc) {
        // Pour l'instant, implémentation simple
        // Plus tard : utiliser Citizens API pour des NPCs avancés
        plugin.getLogger().info("NPC spawn à : " + npc.getLocation());
    }

    /**
     * Charge tous les NPCs depuis la base de données
     */
    public void loadNPCs() {
        String sql = "SELECT * FROM npcs";
        
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String ownerUuid = rs.getString("owner_uuid");
                String type = rs.getString("type");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                String skin = rs.getString("skin");
                
                Location loc = new Location(plugin.getServer().getWorld(world), x, y, z, yaw, pitch);
                NPC npc = new NPC(id, name, ownerUuid, type, loc, skin);
                loadedNPCs.put(id, npc);
                spawnNPC(npc);
            }
            
            plugin.getLogger().info(loadedNPCs.size() + " NPCs chargés.");
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur loadNPCs : " + e.getMessage());
        }
    }

    /**
     * Supprime un NPC
     */
    public boolean removeNPC(Player player, int npcId) {
        NPC npc = loadedNPCs.get(npcId);
        if (npc == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNPC introuvable."));
            return false;
        }

        if (!npc.getOwnerUuid().equals(player.getUniqueId().toString())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cCe NPC ne vous appartient pas."));
            return false;
        }

        // Supprimer de la base de données
        String sql = "DELETE FROM npcs WHERE id = ?";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, npcId);
            stmt.executeUpdate();
            
            loadedNPCs.remove(npcId);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aNPC supprimé avec succès."));
            return true;
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur removeNPC : " + e.getMessage());
            return false;
        }
    }

    /**
     * Liste les NPCs d'un joueur
     */
    public void listNPCs(Player player) {
        UUID uuid = player.getUniqueId();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Vos NPCs ==="));
        
        int count = 0;
        for (NPC npc : loadedNPCs.values()) {
            if (npc.getOwnerUuid().equals(uuid.toString())) {
                count++;
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&e#" + npc.getId() + " &7- " + npc.getName() + " &7(" + npc.getType() + ")"));
            }
        }
        
        if (count == 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Vous n'avez aucun NPC."));
        }
    }

    public int getNPCPrice() {
        return NPC_PRICE;
    }
}
