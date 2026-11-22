package fr.cantale.plugin.spawners;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerTickPlannerTest {

    @Test
    void dueSpawnersAdvanceCooldownOnlyWhenTheirChunkSnapshotIsLoaded() {
        long now = 5_000L;
        assertFalse(SpawnerTickPlanner.isDue(now + 1, now));
        assertTrue(SpawnerTickPlanner.isDue(now, now));
        assertEquals(now + 12_000L, SpawnerTickPlanner.scheduleNext(now, 12_000L));

        Set<String> loaded = Set.of(SpawnerTickPlanner.chunkKey("world", 3, -2));
        assertTrue(SpawnerTickPlanner.chunkLoaded(loaded, "world", 3, -2));
        assertFalse(SpawnerTickPlanner.chunkLoaded(loaded, "world", 4, -2));
        assertFalse(SpawnerTickPlanner.chunkLoaded(Set.of(), "world", 3, -2));
    }
}
