package fr.cantale.plugin.performance;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.atomic.AtomicLong;

public class ChunkLoader {

    private static final int DEFAULT_RADIUS = 1;
    private static final long DEFAULT_COOLDOWN_MILLIS = 2_000L;

    private final CantalePlugin plugin;
    private final ChunkPreloadCache preloadCache;
    private final AtomicLong lastMaintenanceMillis = new AtomicLong(0L);

    public ChunkLoader(CantalePlugin plugin) {
        this.plugin = plugin;
        this.preloadCache = new ChunkPreloadCache(DEFAULT_COOLDOWN_MILLIS);
    }

    public void preloadChunksAround(Location center, int radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        long now = System.currentTimeMillis();

        int effectiveRadius = Math.max(0, Math.min(radius, DEFAULT_RADIUS));
        for (int x = -effectiveRadius; x <= effectiveRadius; x++) {
            for (int z = -effectiveRadius; z <= effectiveRadius; z++) {
                int chunkX = centerChunkX + x;
                int chunkZ = centerChunkZ + z;
                if (!preloadCache.shouldPreload(world.getName(), chunkX, chunkZ, now)) {
                    continue;
                }
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                    if (chunk != null) {
                        preloadCache.markPreloaded(world.getName(), chunkX, chunkZ, System.currentTimeMillis());
                    }
                });
            }
        }
    }

    public void preloadChunksAround(Location center) {
        preloadChunksAround(center, DEFAULT_RADIUS);
    }

    public void maintainClaimChunks() {
        long now = System.currentTimeMillis();
        if (now - lastMaintenanceMillis.get() < 10_000L) {
            return;
        }
        lastMaintenanceMillis.set(now);
    }
}
