package fr.cantale.plugin.performance;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public class ChunkLoader {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;

    public ChunkLoader(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
    }

    /**
     * Précharge les chunks autour d'une location
     * @param center Location centrale
     * @param radius Rayon en chunks
     */
    public void preloadChunksAround(Location center, int radius) {
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = centerChunkX + x;
                int chunkZ = centerChunkZ + z;
                
                // Charger le chunk
                world.loadChunk(chunkX, chunkZ);
            }
        }
    }

    /**
     * Précharge les chunks autour des claims d'une faction
     * @param factionId ID de la faction
     */
    public void preloadFactionClaims(int factionId) {
        // Précharger tous les chunks des claims de la faction
        // Pour l'instant, implémentation simple
    }

    /**
     * Garde les chunks chargés autour des claims actifs
     * À appeler périodiquement pour maintenir les chunks chargés
     */
    public void maintainClaimChunks() {
        // Implémentation future : maintenir les chunks des claims actifs chargés
    }
}
