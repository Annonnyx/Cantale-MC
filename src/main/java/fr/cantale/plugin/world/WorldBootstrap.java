package fr.cantale.plugin.world;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Crée les quatre mondes au démarrage s'ils n'existent pas.
 * {@code createWorld} recharge un dossier déjà présent : rien n'est effacé.
 */
public final class WorldBootstrap {

    private WorldBootstrap() {
    }

    public static void ensure(CantalePlugin plugin) {
        ensureOne(plugin, WorldTopology.HUB, WorldType.FLAT, false);
        ensureOne(plugin, WorldTopology.FACTIONS, WorldType.AMPLIFIED, true);
        ensureOne(plugin, WorldTopology.RESOURCES, WorldType.NORMAL, true);
        ensureOne(plugin, WorldTopology.WARZONE, WorldType.AMPLIFIED, true);
    }

    private static void ensureOne(CantalePlugin plugin, String name, WorldType type, boolean structures) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            applyFlags(existing);
            plugin.getLogger().info("[Mondes] " + name + " déjà présent, aucune suppression.");
            return;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.type(type);
        creator.generateStructures(structures);
        World created = Bukkit.createWorld(creator);
        if (created == null) {
            plugin.getLogger().severe("[Mondes] Impossible de créer " + name + ".");
            return;
        }
        applyFlags(created);
        plugin.getLogger().info("[Mondes] " + name + " prêt (" + type.name() + ").");
    }

    public static void applyFlags(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        if (WorldTopology.HUB.equals(name)) {
            world.setPVP(false);
            return;
        }
        if (WorldTopology.WARZONE.equals(name)) {
            world.setPVP(true);
        }
    }
}
