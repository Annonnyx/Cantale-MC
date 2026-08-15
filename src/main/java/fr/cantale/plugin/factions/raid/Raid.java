package fr.cantale.plugin.factions.raid;

import java.util.Objects;

/**
 * Représente un raid en cours entre deux factions.
 */
public class Raid {

    private final int id;
    private final int attackerFactionId;
    private final int defenderFactionId;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final long startedAt;
    private final long endsAt;

    public Raid(int id, int attackerFactionId, int defenderFactionId, String world, int chunkX, int chunkZ, long startedAt, long endsAt) {
        this.id = id;
        this.attackerFactionId = attackerFactionId;
        this.defenderFactionId = defenderFactionId;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public int getId() { return id; }
    public int getAttackerFactionId() { return attackerFactionId; }
    public int getDefenderFactionId() { return defenderFactionId; }
    public String getWorld() { return world; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public long getStartedAt() { return startedAt; }
    public long getEndsAt() { return endsAt; }

    public boolean isActive(long now) {
        return now < endsAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Raid)) return false;
        Raid other = (Raid) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
