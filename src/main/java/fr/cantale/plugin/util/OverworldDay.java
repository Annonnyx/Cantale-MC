package fr.cantale.plugin.util;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.World.Environment;

/**
 * Jour Minecraft du overworld (horloge vanilla {@code World#getFullTime()}).
 * Source de vérité = {@code level.dat} DayTime, pas un compteur plugin.
 * Équivalent de {@code /time query day} : {@code fullTime / 24000}.
 */
public final class OverworldDay {

    public static final long TICKS_PER_DAY = 24_000L;

    /** Monde spawn Cantale ({@code /spawn} utilise ce nom). */
    public static final String SPAWN_WORLD_NAME = "world";

    private OverworldDay() {
    }

    /**
     * Overworld principal : monde {@code world} s'il est NORMAL, sinon le premier
     * monde {@link Environment#NORMAL}, sinon le premier monde chargé.
     */
    public static World resolveOverworld(Server server) {
        World named = server.getWorld(SPAWN_WORLD_NAME);
        if (named != null && named.getEnvironment() == Environment.NORMAL) {
            return named;
        }
        for (World world : server.getWorlds()) {
            if (world.getEnvironment() == Environment.NORMAL) {
                return world;
            }
        }
        return server.getWorlds().isEmpty() ? null : server.getWorlds().get(0);
    }

    /** {@code /time query day} — jours écoulés depuis la création (0-indexé). */
    public static long currentDay(World world) {
        return world.getFullTime() / TICKS_PER_DAY;
    }

    /** Ticks dans le jour courant (0–23999), comme {@code /time query daytime}. */
    public static long timeOfDay(World world) {
        return Math.floorMod(world.getFullTime(), TICKS_PER_DAY);
    }
}
