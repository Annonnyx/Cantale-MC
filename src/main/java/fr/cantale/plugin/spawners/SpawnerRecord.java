package fr.cantale.plugin.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class SpawnerRecord {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final SpawnerMob mob;
    private int tier;
    private final int factionId;
    private volatile long nextSpawnAtMs;

    public SpawnerRecord(String world, int x, int y, int z, SpawnerMob mob, int tier, int factionId) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mob = mob;
        this.tier = tier;
        this.factionId = factionId;
        this.nextSpawnAtMs = 0L;
    }

    public SpawnerRecord withMob(SpawnerMob newMob) {
        SpawnerRecord copy = new SpawnerRecord(world, x, y, z, newMob, tier, factionId);
        copy.setNextSpawnAtMs(nextSpawnAtMs);
        return copy;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public SpawnerMob mob() {
        return mob;
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int factionId() {
        return factionId;
    }

    public long nextSpawnAtMs() {
        return nextSpawnAtMs;
    }

    public void setNextSpawnAtMs(long nextSpawnAtMs) {
        this.nextSpawnAtMs = nextSpawnAtMs;
    }

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
