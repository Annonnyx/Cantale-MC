package fr.cantale.plugin.diplomacy;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.ChatColor;

import java.util.*;

public class DiplomacyManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final Map<Integer, Set<Integer>> alliances; // factionId -> Set<alliedFactionIds>
    private final Map<Integer, Set<Integer>> wars; // factionId -> Set<enemyFactionIds>

    public DiplomacyManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.alliances = new HashMap<>();
        this.wars = new HashMap<>();
    }

    /**
     * Propose une alliance à une faction
     */
    public boolean proposeAlliance(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return false;
        }
        
        Faction faction = factionManager.getFaction(factionId);
        Faction target = factionManager.getFaction(targetFactionId);
        
        if (faction == null || target == null) {
            return false;
        }
        
        if (isAllied(factionId, targetFactionId)) {
            return false;
        }
        
        // Pour l'instant, alliance directe (cosmétique)
        // Plus tard : système de proposition avec acceptation
        createAlliance(factionId, targetFactionId);
        return true;
    }

    /**
     * Crée une alliance entre deux factions
     */
    private void createAlliance(int factionId1, int factionId2) {
        alliances.computeIfAbsent(factionId1, k -> new HashSet<>()).add(factionId2);
        alliances.computeIfAbsent(factionId2, k -> new HashSet<>()).add(factionId1);
        
        // Annuler les guerres existantes
        endWar(factionId1, factionId2);
        
        saveDiplomacy();
    }

    /**
     * Déclare la guerre à une faction
     */
    public boolean declareWar(int factionId, int targetFactionId) {
        if (factionId == targetFactionId) {
            return false;
        }
        
        Faction faction = factionManager.getFaction(factionId);
        Faction target = factionManager.getFaction(targetFactionId);
        
        if (faction == null || target == null) {
            return false;
        }
        
        if (isAtWar(factionId, targetFactionId)) {
            return false;
        }
        
        // Rompre l'alliance si elle existe
        endAlliance(factionId, targetFactionId);
        
        // Déclarer la guerre
        wars.computeIfAbsent(factionId, k -> new HashSet<>()).add(targetFactionId);
        wars.computeIfAbsent(targetFactionId, k -> new HashSet<>()).add(factionId);
        
        saveDiplomacy();
        return true;
    }

    /**
     * Termine une alliance
     */
    public boolean endAlliance(int factionId1, int factionId2) {
        if (!isAllied(factionId1, factionId2)) {
            return false;
        }
        
        alliances.getOrDefault(factionId1, new HashSet<>()).remove(factionId2);
        alliances.getOrDefault(factionId2, new HashSet<>()).remove(factionId1);
        
        saveDiplomacy();
        return true;
    }

    /**
     * Termine une guerre (paix)
     */
    public boolean endWar(int factionId1, int factionId2) {
        if (!isAtWar(factionId1, factionId2)) {
            return false;
        }
        
        wars.getOrDefault(factionId1, new HashSet<>()).remove(factionId2);
        wars.getOrDefault(factionId2, new HashSet<>()).remove(factionId1);
        
        saveDiplomacy();
        return true;
    }

    /**
     * Vérifie si deux factions sont alliées
     */
    public boolean isAllied(int factionId1, int factionId2) {
        Set<Integer> allies = alliances.get(factionId1);
        return allies != null && allies.contains(factionId2);
    }

    /**
     * Vérifie si deux factions sont en guerre
     */
    public boolean isAtWar(int factionId1, int factionId2) {
        Set<Integer> enemies = wars.get(factionId1);
        return enemies != null && enemies.contains(factionId2);
    }

    /**
     * Liste les alliances d'une faction
     */
    public String listAlliances(int factionId) {
        Set<Integer> allies = alliances.get(factionId);
        if (allies == null || allies.isEmpty()) {
            return "&7Aucune alliance.";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int allyId : allies) {
            Faction ally = factionManager.getFaction(allyId);
            if (ally != null) {
                sb.append(ChatColor.translateAlternateColorCodes('&', 
                    "&a✓ " + ally.getName() + " &7[" + ally.getTag() + "]\n"));
            }
        }
        return sb.toString();
    }

    /**
     * Liste les guerres d'une faction
     */
    public String listWars(int factionId) {
        Set<Integer> enemies = wars.get(factionId);
        if (enemies == null || enemies.isEmpty()) {
            return "&7Aucune guerre.";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int enemyId : enemies) {
            Faction enemy = factionManager.getFaction(enemyId);
            if (enemy != null) {
                sb.append(ChatColor.translateAlternateColorCodes('&', 
                    "&c✗ " + enemy.getName() + " &7[" + enemy.getTag() + "]\n"));
            }
        }
        return sb.toString();
    }

    /**
     * Sauvegarde la diplomatie dans la base de données
     */
    private void saveDiplomacy() {
        // Supprimer les anciennes relations
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM diplomacy");
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveDiplomacy (delete) : " + e.getMessage());
        }
        
        // Sauvegarder les alliances
        String allianceSql = "INSERT INTO diplomacy (faction_id, target_faction_id, type) VALUES (?, ?, 'alliance')";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(allianceSql)) {
            
            for (Map.Entry<Integer, Set<Integer>> entry : alliances.entrySet()) {
                int factionId = entry.getKey();
                for (int allyId : entry.getValue()) {
                    // Sauvegarder seulement une direction pour éviter les doublons
                    if (factionId < allyId) {
                        stmt.setInt(1, factionId);
                        stmt.setInt(2, allyId);
                        stmt.executeUpdate();
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveDiplomacy (alliances) : " + e.getMessage());
        }
        
        // Sauvegarder les guerres
        String warSql = "INSERT INTO diplomacy (faction_id, target_faction_id, type) VALUES (?, ?, 'war')";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(warSql)) {
            
            for (Map.Entry<Integer, Set<Integer>> entry : wars.entrySet()) {
                int factionId = entry.getKey();
                for (int enemyId : entry.getValue()) {
                    // Sauvegarder seulement une direction pour éviter les doublons
                    if (factionId < enemyId) {
                        stmt.setInt(1, factionId);
                        stmt.setInt(2, enemyId);
                        stmt.executeUpdate();
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur saveDiplomacy (wars) : " + e.getMessage());
        }
    }

    /**
     * Charge la diplomatie depuis la base de données
     */
    public void loadDiplomacy() {
        alliances.clear();
        wars.clear();
        
        String sql = "SELECT faction_id, target_faction_id, type FROM diplomacy";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int factionId = rs.getInt("faction_id");
                int targetId = rs.getInt("target_faction_id");
                String type = rs.getString("type");
                
                if ("alliance".equalsIgnoreCase(type)) {
                    alliances.computeIfAbsent(factionId, k -> new HashSet<>()).add(targetId);
                    alliances.computeIfAbsent(targetId, k -> new HashSet<>()).add(factionId);
                } else if ("war".equalsIgnoreCase(type)) {
                    wars.computeIfAbsent(factionId, k -> new HashSet<>()).add(targetId);
                    wars.computeIfAbsent(targetId, k -> new HashSet<>()).add(factionId);
                }
            }
            
            plugin.getLogger().info("Diplomatie chargée : " + alliances.size() + " alliances, " + wars.size() + " guerres.");
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Erreur loadDiplomacy : " + e.getMessage());
        }
    }
}
