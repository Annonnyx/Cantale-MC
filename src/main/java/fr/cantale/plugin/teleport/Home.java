package fr.cantale.plugin.teleport;

import org.bukkit.Location;

import java.util.UUID;

public class Home {
    private int id;
    private final UUID playerUuid;
    private final String name;
    private final Location location;
    private final long createdAt;

    public Home(int id, UUID playerUuid, String name, Location location, long createdAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.name = name;
        this.location = location;
        this.createdAt = createdAt;
    }

    public Home(UUID playerUuid, String name, Location location) {
        this(0, playerUuid, name, location, System.currentTimeMillis() / 1000);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
