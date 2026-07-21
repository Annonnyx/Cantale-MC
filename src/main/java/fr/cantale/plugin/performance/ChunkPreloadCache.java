package fr.cantale.plugin.performance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkPreloadCache {

    private final long cooldownMillis;
    private final Map<Long, Long> lastPreloadedAt = new ConcurrentHashMap<>();

    public ChunkPreloadCache(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public boolean shouldPreload(String worldName, int chunkX, int chunkZ, long nowMillis) {
        long key = composeKey(worldName, chunkX, chunkZ);
        Long lastTime = lastPreloadedAt.get(key);
        if (lastTime == null) {
            return true;
        }
        return nowMillis - lastTime >= cooldownMillis;
    }

    public void markPreloaded(String worldName, int chunkX, int chunkZ, long nowMillis) {
        lastPreloadedAt.put(composeKey(worldName, chunkX, chunkZ), nowMillis);
    }

    private long composeKey(String worldName, int chunkX, int chunkZ) {
        return ((long) worldName.hashCode() << 32) ^ ((chunkX & 0xFFFFFFFFL) << 16) ^ (chunkZ & 0xFFFFL);
    }
}
