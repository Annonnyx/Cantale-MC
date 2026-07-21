package fr.cantale.plugin.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPreloadCacheTest {

    @Test
    void shouldAllowFreshChunkAndSuppressFrequentDuplicates() {
        ChunkPreloadCache cache = new ChunkPreloadCache(2000L);

        assertTrue(cache.shouldPreload("world", 12, -4, 1000L));
        cache.markPreloaded("world", 12, -4, 1000L);

        assertFalse(cache.shouldPreload("world", 12, -4, 1200L));
        assertTrue(cache.shouldPreload("world", 12, -5, 1200L));
    }

    @Test
    void shouldReallowAfterCooldownExpires() {
        ChunkPreloadCache cache = new ChunkPreloadCache(1000L);

        assertTrue(cache.shouldPreload("world", 1, 2, 0L));
        cache.markPreloaded("world", 1, 2, 0L);

        assertFalse(cache.shouldPreload("world", 1, 2, 500L));
        assertTrue(cache.shouldPreload("world", 1, 2, 1500L));
    }
}
