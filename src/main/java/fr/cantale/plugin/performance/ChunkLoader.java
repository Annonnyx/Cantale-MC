package fr.cantale.plugin.performance;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.OverworldDay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Préchargement async de chunks (téléports, homes) + préchauffage du spawn au boot.
 * Tout passe par {@link World#getChunkAtAsync} : jamais de génération/chargement sync.
 */
public class ChunkLoader {

    private static final long DEFAULT_COOLDOWN_MILLIS = 2_000L;

    private final CantalePlugin plugin;
    private final ChunkPreloadCache preloadCache;
    /** Plafond de rayon pour les préchargements à la demande (config). */
    private int preloadRadius;

    public ChunkLoader(CantalePlugin plugin) {
        this.plugin = plugin;
        this.preloadCache = new ChunkPreloadCache(DEFAULT_COOLDOWN_MILLIS);
        loadConfig();
    }

    public void loadConfig() {
        this.preloadRadius = Math.max(0, plugin.getConfig().getInt("optimization.chunk-preload-radius", 2));
    }

    public void preloadChunksAround(Location center, int radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        long now = System.currentTimeMillis();

        int effectiveRadius = Math.max(0, Math.min(radius, preloadRadius));
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

    /** Précharge autour du point avec le rayon configuré ({@code optimization.chunk-preload-radius}). */
    public void preloadChunksAround(Location center) {
        preloadChunksAround(center, preloadRadius);
    }

    /**
     * Préchauffe les chunks autour du spawn de l'overworld (appelé au boot, décalé
     * de quelques ticks). Le spawn est la zone la plus visitée : Paper la garde
     * ensuite chargée à peu de frais plutôt que de charger/décharger en boucle.
     */
    public void prewarmSpawn() {
        int radius = Math.max(0, plugin.getConfig().getInt("optimization.spawn-prewarm-radius", 6));
        if (radius <= 0) return;
        World world = OverworldDay.resolveOverworld(Bukkit.getServer());
        if (world == null) return;

        Location spawn = world.getSpawnLocation();
        int centerChunkX = spawn.getBlockX() >> 4;
        int centerChunkZ = spawn.getBlockZ() >> 4;
        long now = System.currentTimeMillis();

        int requested = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = centerChunkX + x;
                int chunkZ = centerChunkZ + z;
                requested++;
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                    if (chunk != null) {
                        preloadCache.markPreloaded(world.getName(), chunkX, chunkZ, System.currentTimeMillis());
                    }
                });
            }
        }
        plugin.getLogger().info("[ChunkLoader] Préchauffage spawn : " + requested + " chunks demandés (rayon " + radius + ")");
    }
}
