package fr.cantale.plugin.spawners;

import java.util.Set;

/**
 * Décisions du tick spawner qui ne touchent aucun objet Bukkit :
 * échéance, prochain cooldown, chunk déjà vu comme chargé.
 */
public final class SpawnerTickPlanner {

    private SpawnerTickPlanner() {
    }

    public static boolean isDue(long nextSpawnAtMs, long nowMs) {
        return nextSpawnAtMs <= nowMs;
    }

    public static long scheduleNext(long nowMs, long intervalMs) {
        return nowMs + Math.max(0L, intervalMs);
    }

    public static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + "|" + chunkX + "|" + chunkZ;
    }

    public static boolean chunkLoaded(Set<String> loaded, String world, int chunkX, int chunkZ) {
        return loaded != null && loaded.contains(chunkKey(world, chunkX, chunkZ));
    }
}
