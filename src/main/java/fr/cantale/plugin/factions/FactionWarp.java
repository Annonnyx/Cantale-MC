package fr.cantale.plugin.factions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Warp de faction public (table historique {@code faction_warps}).
 * Accessible via {@code /fw <nom>} pour tout le serveur.
 */
public final class FactionWarp {

    private final int id;
    private final int factionId;
    private final String name;
    private final String factionName;
    private final String world;
    private final double x;
    private final double y;
    private final double z;

    public FactionWarp(int id, int factionId, String name, String factionName,
                       String world, double x, double y, double z) {
        this.id = id;
        this.factionId = factionId;
        this.name = name;
        this.factionName = factionName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getId() {
        return id;
    }

    public int getFactionId() {
        return factionId;
    }

    public String getName() {
        return name;
    }

    public String getFactionName() {
        return factionName != null ? factionName : "?";
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }
}
