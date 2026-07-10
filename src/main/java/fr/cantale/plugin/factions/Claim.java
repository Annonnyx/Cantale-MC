package fr.cantale.plugin.factions;

import org.bukkit.Location;
import org.bukkit.World;

public class Claim {
    private int id;
    private final int factionId;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final long createdAt;
    private boolean pasdic;

    public Claim(int id, int factionId, String world, int chunkX, int chunkZ, long createdAt, boolean pasdic) {
        this.id = id;
        this.factionId = factionId;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.createdAt = createdAt;
        this.pasdic = pasdic;
    }

    public Claim(int factionId, String world, int chunkX, int chunkZ) {
        this(0, factionId, world, chunkX, chunkZ, System.currentTimeMillis() / 1000, false);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFactionId() {
        return factionId;
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getChunkKey() {
        return world + "," + chunkX + "," + chunkZ;
    }

    public boolean isPasdic() {
        return pasdic;
    }

    public void setPasdic(boolean pasdic) {
        this.pasdic = pasdic;
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && location.getBlockX() >> 4 == chunkX
                && location.getBlockZ() >> 4 == chunkZ;
    }
}
