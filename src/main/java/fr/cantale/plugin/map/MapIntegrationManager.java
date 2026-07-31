package fr.cantale.plugin.map;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.teleport.Warp;
import fr.cantale.plugin.teleport.WarpManager;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;

public class MapIntegrationManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final WarpManager warpManager;

    // Codes couleurs (RGB) pour les différents types de marqueurs
    public static final int COLOR_OWN_FACTION = 0x00FF00;     // vert
    public static final int COLOR_ALLIED = 0x00AAFF;          // bleu
    public static final int COLOR_ENEMY = 0xFF0000;           // rouge
    public static final int COLOR_NEUTRAL = 0xFFAA00;         // orange/jaune
    public static final int COLOR_FORTIFIED = 0x800080;       // violet (chunks fortifiés)
    public static final int COLOR_PASDIC = 0x00FFFF;          // cyan
    public static final int COLOR_WARP = 0xFFFF00;            // jaune
    public static final int COLOR_SPAWN = 0xFFAAFF;           // rose

    public MapIntegrationManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.warpManager = plugin.getWarpManager();
    }

    /** Calcule la couleur d'un claim selon les relations diplomatiques avec le joueur. */
    public int getClaimColor(Faction claimFaction, Faction playerFaction) {
        if (playerFaction != null && playerFaction.getId() == claimFaction.getId()) return COLOR_OWN_FACTION;
        DiplomacyManager dipl = plugin.getDiplomatieManager();
        if (playerFaction != null && dipl != null) {
            if (dipl.isAllied(playerFaction.getId(), claimFaction.getId())) return COLOR_ALLIED;
            if (dipl.isAtWar(playerFaction.getId(), claimFaction.getId())) return COLOR_ENEMY;
        }
        return COLOR_NEUTRAL;
    }

    public void sendWaypointsToPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // 1) SPAWN du serveur (checkpoint auto)
        if (player.getWorld() != null) {
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            sendJourneyMapWaypoint(player, "Spawn", spawn.getX(), spawn.getY(), spawn.getZ(),
                    spawn.getWorld().getName(), "§dSpawn du serveur", COLOR_SPAWN);
            sendXaeroWaypoint(player, "Spawn", spawn.getX(), spawn.getY(), spawn.getZ(),
                    spawn.getWorld().getName(), "Spawn", COLOR_SPAWN);
        }

        // 2) Warps publics (checkpoints auto)
        for (Warp warp : warpManager.getWarps()) {
            Location l = warp.getLocation();
            if (l.getWorld() == null) continue;
            sendJourneyMapWaypoint(player, warp.getName(), l.getX(), l.getY(), l.getZ(),
                    l.getWorld().getName(), "§6Warp public", COLOR_WARP);
            sendXaeroWaypoint(player, warp.getName(), l.getX(), l.getY(), l.getZ(),
                    l.getWorld().getName(), "Warp", COLOR_WARP);
        }

        // 3) Spawn et warps de la faction du joueur
        Faction faction = factionManager.getPlayerFaction(uuid);
        if (faction != null) {
            // Spawn de faction
            if (faction.getSpawnX() != null && faction.getSpawnWorld() != null) {
                sendJourneyMapWaypoint(player, faction.getName() + " — Spawn",
                        faction.getSpawnX(), faction.getSpawnY(), faction.getSpawnZ(),
                        faction.getSpawnWorld(), "§aSpawn de faction", COLOR_OWN_FACTION);
                sendXaeroWaypoint(player, faction.getName() + "-Spawn",
                        faction.getSpawnX(), faction.getSpawnY(), faction.getSpawnZ(),
                        faction.getSpawnWorld(), "Faction Spawn", COLOR_OWN_FACTION);
            }
            // Warps de faction
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement st = c.prepareStatement("SELECT name, world, x, y, z FROM faction_warps WHERE faction_id = ?")) {
                st.setInt(1, faction.getId());
                java.sql.ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    String name = faction.getTag() + "-" + rs.getString("name");
                    sendJourneyMapWaypoint(player, name, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getString("world"), "§aFaction warp", COLOR_OWN_FACTION);
                    sendXaeroWaypoint(player, name, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getString("world"), "Faction warp", COLOR_OWN_FACTION);
                }
            } catch (java.sql.SQLException ex) {
                plugin.getLogger().warning("sendWaypoints: " + ex.getMessage());
            }
        }
    }

    public void sendJourneyMapWaypoint(Player player, String name, double x, double y, double z, String world, String description, int color) {
        JSONObject waypoint = new JSONObject();
        waypoint.put("name", name);
        waypoint.put("x", x);
        waypoint.put("y", y);
        waypoint.put("z", z);
        waypoint.put("world", world);
        waypoint.put("description", description);
        waypoint.put("color", color);
        waypoint.put("icon", "waypoint");
        
        // Envoyer via le channel JourneyMap
        sendMapPluginMessage(player, "journeymap:waypoint", waypoint.toString().getBytes());
    }

    public void sendXaeroWaypoint(Player player, String name, double x, double y, double z, String world, String description, int color) {
        JSONObject waypoint = new JSONObject();
        waypoint.put("name", name);
        waypoint.put("x", x);
        waypoint.put("y", y);
        waypoint.put("z", z);
        waypoint.put("world", world);
        waypoint.put("description", description);
        waypoint.put("color", color);
        waypoint.put("type", "normal");
        
        // Envoyer via le channel Xaero's Minimap
        sendMapPluginMessage(player, "xaerominimap:waypoint", waypoint.toString().getBytes());
    }

    public void sendJourneyMapRegion(Player player, String name, double x1, double y1, double z1, double x2, double y2, double z2, String world, int color) {
        JSONObject region = new JSONObject();
        region.put("name", name);
        region.put("x1", x1);
        region.put("y1", y1);
        region.put("z1", z1);
        region.put("x2", x2);
        region.put("y2", y2);
        region.put("z2", z2);
        region.put("world", world);
        region.put("color", color);
        region.put("type", "chunk");
        region.put("alpha", 64); // Transparence
        
        // Envoyer via le channel JourneyMap
        sendMapPluginMessage(player, "journeymap:region", region.toString().getBytes());
    }

    public void sendXaeroRegion(Player player, String name, double x1, double y1, double z1, double x2, double y2, double z2, String world, int color) {
        JSONObject region = new JSONObject();
        region.put("name", name);
        region.put("x1", x1);
        region.put("y1", y1);
        region.put("z1", z1);
        region.put("x2", x2);
        region.put("y2", y2);
        region.put("z2", z2);
        region.put("world", world);
        region.put("color", color);
        region.put("type", "chunk");
        region.put("alpha", 64); // Transparence
        
        // Envoyer via le channel Xaero's Minimap
        sendMapPluginMessage(player, "xaerominimap:region", region.toString().getBytes());
    }

    private void sendMapPluginMessage(Player player, String channel, byte[] data) {
        try {
            player.sendPluginMessage(plugin, channel, data);
        } catch (Exception e) {
            plugin.getLogger().fine("[MapIntegration] Échec envoi sur " + channel + ": " + e.getMessage());
        }
    }

    public void sendClaimsToPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Faction playerFaction = factionManager.getPlayerFaction(uuid);

        List<Faction> allFactions = factionManager.getAllFactions();
        for (Faction faction : allFactions) {
            // Cacher les factions secrètes aux non-membres
            if (faction.isSecret() && (playerFaction == null || playerFaction.getId() != faction.getId())) {
                continue;
            }

            int baseColor = getClaimColor(faction, playerFaction);

            for (Claim claim : faction.getClaims()) {
                int color = baseColor;
                if (claim.isPasdic()) color = COLOR_PASDIC;
                // Surligner les chunks fortifiés en violet (uniquement visible aux membres)
                if (playerFaction != null && playerFaction.getId() == faction.getId()
                        && plugin.getFortifiedChunkDAO() != null
                        && plugin.getFortifiedChunkDAO().isFortified(claim.getWorld(), claim.getChunkX(), claim.getChunkZ())) {
                    color = COLOR_FORTIFIED;
                }

                sendJourneyMapRegion(player, faction.getName(),
                        claim.getChunkX() * 16, 64, claim.getChunkZ() * 16,
                        claim.getChunkX() * 16 + 16, 64, claim.getChunkZ() * 16 + 16,
                        claim.getWorld(), color);
                sendXaeroRegion(player, faction.getName(),
                        claim.getChunkX() * 16, 64, claim.getChunkZ() * 16,
                        claim.getChunkX() * 16 + 16, 64, claim.getChunkZ() * 16 + 16,
                        claim.getWorld(), color);
            }
        }
    }

    /**
     * Exporte un fichier de waypoints au format Xaero (texte) pour distribution manuelle.
     * Le fichier peut être importé par les joueurs dans «%appdata%/.minecraft/xaero/minimap/multiplayer_<server>/dim%0/mw$default_1.txt ».
     * Format ligne : waypoint:Name:N:x:y:z:colorIdx:disabled:type:0:gui.xaero_default:false:0:false
     */
    public File exportXaeroWaypointsFile() {
        File out = new File(plugin.getDataFolder(), "cantale-xaero-waypoints.txt");
        try (PrintWriter pw = new PrintWriter(out)) {
            pw.println("# Cantale — Xaero's Minimap waypoints");
            pw.println("# A copier dans : %appdata%/.minecraft/xaero/minimap/multiplayer_<server>/dim%0/mw$default_1.txt");
            pw.println("#");
            pw.println("sets:gui.xaero_default");
            pw.println("#");

            // Spawn du serveur
            if (!Bukkit.getWorlds().isEmpty()) {
                Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                pw.println(formatXaeroLine("Spawn", "S", spawn.getX(), spawn.getY(), spawn.getZ(), 6));
            }
            // Warps publics
            for (Warp w : warpManager.getWarps()) {
                Location l = w.getLocation();
                if (l.getWorld() == null) continue;
                pw.println(formatXaeroLine(w.getName(), w.getName().substring(0, 1).toUpperCase(), l.getX(), l.getY(), l.getZ(), 4));
            }
            plugin.getLogger().info("Waypoints Xaero exportés : " + out.getAbsolutePath());
            return out;
        } catch (Exception e) {
            plugin.getLogger().warning("exportXaeroWaypointsFile: " + e.getMessage());
            return null;
        }
    }

    private String formatXaeroLine(String name, String initials, double x, double y, double z, int colorIdx) {
        return String.format("waypoint:%s:%s:%d:%d:%d:%d:false:0:gui.xaero_default:false:0:false:0",
                name.replace(":", ""),
                initials.replace(":", ""),
                (int) x, (int) y, (int) z, colorIdx);
    }

    public void onFactionClaimAdded(Faction faction, Claim claim) {
        // Envoyer le nouveau claim à tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
            int color = getClaimColor(faction, playerFaction);
            
            sendJourneyMapRegion(player, faction.getName(), 
                claim.getChunkX() * 16, 64, claim.getChunkZ() * 16, 
                claim.getChunkX() * 16 + 16, 64, claim.getChunkZ() * 16 + 16, 
                claim.getWorld(), color);
            sendXaeroRegion(player, faction.getName(), 
                claim.getChunkX() * 16, 64, claim.getChunkZ() * 16, 
                claim.getChunkX() * 16 + 16, 64, claim.getChunkZ() * 16 + 16, 
                claim.getWorld(), color);
        }
    }

    public void onWarpAdded(Warp warp) {
        // Envoyer le nouveau warp à tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendJourneyMapWaypoint(player, warp.getName(), 
                warp.getLocation().getX(), warp.getLocation().getY(), warp.getLocation().getZ(), 
                warp.getLocation().getWorld().getName(), "§6Warp Public", 0xFFFF00);
            sendXaeroWaypoint(player, warp.getName(), 
                warp.getLocation().getX(), warp.getLocation().getY(), warp.getLocation().getZ(), 
                warp.getLocation().getWorld().getName(), "Warp Public", 0xFFFF00);
        }
    }
}
