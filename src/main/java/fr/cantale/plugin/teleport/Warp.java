package fr.cantale.plugin.teleport;

import org.bukkit.Location;

public class Warp {
    private int id;
    private final String name;
    private final Location location;
    private final boolean isEvent;
    private boolean isActive;
    private final long createdAt;

    public Warp(int id, String name, Location location, boolean isEvent, boolean isActive, long createdAt) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.isEvent = isEvent;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public Warp(String name, Location location, boolean isEvent) {
        this(0, name, location, isEvent, true, System.currentTimeMillis() / 1000);
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

    public Location getLocation() {
        return location;
    }

    public boolean isEvent() {
        return isEvent;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
