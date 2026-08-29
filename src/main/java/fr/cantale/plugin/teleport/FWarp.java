package fr.cantale.plugin.teleport;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Warp public créé par une faction via /fw : accessible à tous les joueurs.
 * Contrairement aux warps publics admin, aucune zone protégée n'est créée autour :
 * seule la protection de claims classique s'applique.
 */
public class FWarp {

    private int id;
    private final String name;
    private final int factionId;
    private final UUID creatorUuid;
    private final Location location;
    private final long createdAt;

    public FWarp(int id, String name, int factionId, UUID creatorUuid, Location location, long createdAt) {
        this.id = id;
        this.name = name;
        this.factionId = factionId;
        this.creatorUuid = creatorUuid;
        this.location = location;
        this.createdAt = createdAt;
    }

    public FWarp(String name, int factionId, UUID creatorUuid, Location location) {
        this(0, name, factionId, creatorUuid, location, System.currentTimeMillis() / 1000);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getFactionId() {
        return factionId;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public Location getLocation() {
        return location;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
