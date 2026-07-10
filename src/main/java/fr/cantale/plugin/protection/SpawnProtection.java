package fr.cantale.plugin.protection;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.WarpManager;
import fr.cantale.plugin.teleport.Warp;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class SpawnProtection {

    private final CantalePlugin plugin;
    private final WarpManager warpManager;
    private final List<ProtectedZone> protectedZones;
    private static final int CHUNK_RADIUS = 16; // 16 chunks = 256 blocs

    public SpawnProtection(CantalePlugin plugin) {
        this.plugin = plugin;
        this.warpManager = plugin.getWarpManager();
        this.protectedZones = new ArrayList<>();
    }

    public void loadProtectedZones() {
        protectedZones.clear();
        
        // Ajouter le spawn du monde
        for (World world : plugin.getServer().getWorlds()) {
            Location spawn = world.getSpawnLocation();
            protectedZones.add(new ProtectedZone(spawn, CHUNK_RADIUS, "spawn"));
        }
        
        // Ajouter tous les warps actifs
        for (Warp warp : warpManager.getWarps()) {
            if (warp.isActive()) {
                protectedZones.add(new ProtectedZone(warp.getLocation(), CHUNK_RADIUS, "warp:" + warp.getName()));
            }
        }
    }

    public boolean isProtected(Location location) {
        for (ProtectedZone zone : protectedZones) {
            if (zone.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean canClaim(Location location) {
        return !isProtected(location);
    }

    public boolean canBuild(Location location) {
        return !isProtected(location);
    }

    public void addProtectedZone(Location center, int radius, String name) {
        protectedZones.add(new ProtectedZone(center, radius, name));
    }

    public void removeProtectedZone(String name) {
        protectedZones.removeIf(zone -> zone.getName().equals(name));
    }

    private static class ProtectedZone {
        private final Location center;
        private final int radius; // en chunks
        private final String name;

        public ProtectedZone(Location center, int radius, String name) {
            this.center = center;
            this.radius = radius;
            this.name = name;
        }

        public boolean contains(Location location) {
            if (!location.getWorld().equals(center.getWorld())) {
                return false;
            }
            
            int centerChunkX = center.getBlockX() >> 4;
            int centerChunkZ = center.getBlockZ() >> 4;
            int locChunkX = location.getBlockX() >> 4;
            int locChunkZ = location.getBlockZ() >> 4;
            
            int dx = Math.abs(locChunkX - centerChunkX);
            int dz = Math.abs(locChunkZ - centerChunkZ);
            
            return dx <= radius && dz <= radius;
        }

        public String getName() {
            return name;
        }
    }
}
