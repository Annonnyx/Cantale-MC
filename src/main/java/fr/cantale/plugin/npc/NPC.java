package fr.cantale.plugin.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public class NPC {

    private int id;
    private String name;
    private String ownerUuid;
    private String type;
    private Location location;
    private String skin;

    public NPC(int id, String name, String ownerUuid, String type, Location location, String skin) {
        this.id = id;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.type = type;
        this.location = location;
        this.skin = skin;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }
}
