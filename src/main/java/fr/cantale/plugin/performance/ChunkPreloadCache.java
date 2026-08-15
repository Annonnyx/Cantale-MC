package fr.cantale.plugin.performance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cooldown anti-spam sur le préchargement d'un chunk (monde + X + Z).
 * Clé exacte (record monde/x/z, sans collision) et map bornée : éviction FIFO
 * au-delà de {@link #MAX_ENTRIES} pour éviter toute croissance mémoire non bornée.
 */
public class ChunkPreloadCache {

    private static final int MAX_ENTRIES = 10_000;

    private final long cooldownMillis;
    private final Map<Key, Long> lastPreloadedAt;

    private record Key(String world, int x, int z) {
    }

    public ChunkPreloadCache(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
        this.lastPreloadedAt = Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Long> eldest) {
                return size() > MAX_ENTRIES;
            }
        });
    }

    public boolean shouldPreload(String worldName, int chunkX, int chunkZ, long nowMillis) {
        Long lastTime = lastPreloadedAt.get(new Key(worldName, chunkX, chunkZ));
        if (lastTime == null) {
            return true;
        }
        return nowMillis - lastTime >= cooldownMillis;
    }

    public void markPreloaded(String worldName, int chunkX, int chunkZ, long nowMillis) {
        lastPreloadedAt.put(new Key(worldName, chunkX, chunkZ), nowMillis);
    }
}
