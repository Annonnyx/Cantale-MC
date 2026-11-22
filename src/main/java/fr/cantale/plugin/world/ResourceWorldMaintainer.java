package fr.cantale.plugin.world;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reset réel des zones de récolte : un chunk par tick, filons reposés sur les blocs naturels.
 * Les joueurs présents sont sautés puis réessayés. L'inventaire n'est jamais modifié.
 */
public final class ResourceWorldMaintainer {

    /** Passages supplémentaires pour les chunks où un joueur est encore trop près. */
    private static final int MAX_OCCUPIED_PASSES = 4;

    private final CantalePlugin plugin;
    private final List<Pending> queue = new ArrayList<>();
    private final List<Pending> retry = new ArrayList<>();
    private BukkitTask task;
    private int idleTicks;
    private boolean running;
    private String activeWorld;
    private long activeCycle;
    private int occupiedPasses;

    public ResourceWorldMaintainer(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        WorldBootstrap.applyFlags(Bukkit.getWorld(WorldTopology.HUB));
        WorldBootstrap.applyFlags(Bukkit.getWorld(WorldTopology.WARZONE));
        if (running) {
            processOne();
            return;
        }
        if (++idleTicks < 600) {
            return;
        }
        idleTicks = 0;
        consider(WorldTopology.RESOURCES, false, ResourceCyclePlanner.RESOURCE_PERIOD_SECONDS);
        consider(WorldTopology.WARZONE, true, ResourceCyclePlanner.WARZONE_DEPOSIT_PERIOD_SECONDS);
    }

    private void consider(String worldName, boolean rare, long period) {
        if (running) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        WorldCycleRepository.Cycle cycle = readCycle(worldName);
        if (!ResourceCyclePlanner.isDue(now, cycle.lastResetAt(), period)) {
            return;
        }
        queue.clear();
        retry.clear();
        occupiedPasses = 0;
        activeWorld = worldName;
        activeCycle = cycle.cycle() + 1;
        for (int cx = -ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS; cx <= ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS; cx++) {
            for (int cz = -ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS; cz <= ResourceCyclePlanner.HARVEST_RADIUS_CHUNKS; cz++) {
                if (!ResourceCyclePlanner.isHarvestChunk(cx, cz)) {
                    continue;
                }
                queue.add(new Pending(worldName, rare, activeCycle, cx, cz));
            }
        }
        running = !queue.isEmpty();
        if (running) {
            plugin.getLogger().info("[Mondes] Reset de récolte " + worldName
                    + " (" + queue.size() + " chunks, cycle " + (cycle.cycle() + 1) + ").");
        }
    }

    private void processOne() {
        if (queue.isEmpty()) {
            if (!retry.isEmpty() && occupiedPasses < MAX_OCCUPIED_PASSES) {
                occupiedPasses++;
                queue.addAll(retry);
                retry.clear();
                return;
            }
            finish();
            return;
        }
        Pending next = queue.remove(0);
        World world = Bukkit.getWorld(next.world);
        if (world == null) {
            retry.add(next);
            return;
        }
        if (playerNear(world, next.chunkX, next.chunkZ)) {
            retry.add(next);
            return;
        }
        refreshChunk(world, next);
        if (queue.isEmpty() && retry.isEmpty()) {
            finish();
        }
    }

    private void finish() {
        if (!running) {
            return;
        }
        String worldName = activeWorld;
        long cycle = activeCycle;
        int skipped = retry.size();
        running = false;
        activeWorld = null;
        retry.clear();
        occupiedPasses = 0;
        if (worldName == null) {
            return;
        }
        if (Bukkit.getWorld(worldName) == null) {
            plugin.getLogger().warning("[Mondes] Reset de " + worldName
                    + " interrompu : monde absent, cycle non validé.");
            return;
        }
        persist(worldName, cycle);
        if (skipped > 0) {
            plugin.getLogger().info("[Mondes] Reset de récolte terminé (" + skipped
                    + " chunks occupés reportés au prochain cycle).");
        } else {
            plugin.getLogger().info("[Mondes] Reset de récolte terminé.");
        }
    }

    private void refreshChunk(World world, Pending pending) {
        int minY = pending.rare ? HarvestLayout.RARE_MIN_Y : HarvestLayout.RESOURCE_MIN_Y;
        int maxY = pending.rare ? HarvestLayout.RARE_MAX_Y : HarvestLayout.RESOURCE_MAX_Y;
        int baseX = pending.chunkX << 4;
        int baseZ = pending.chunkZ << 4;
        for (int x = baseX; x < baseX + 16; x++) {
            for (int z = baseZ; z < baseZ + 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    String node = HarvestLayout.nodeId(x, y, z, pending.cycle, pending.rare);
                    if (node == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(node);
                    if (material == null) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (!canRefresh(block.getType())) {
                        continue;
                    }
                    if (block.getType() != material) {
                        block.setType(material, false);
                    }
                }
            }
        }
    }

    private void persist(String world, long cycle) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().warning("[Mondes] Cycle de " + world + " non écrit : pool SQL indisponible.");
                return;
            }
            WorldCycleRepository.write(conn, world, cycle, now);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Mondes] Échec d'écriture du cycle " + world + " : " + e.getMessage());
        }
    }

    private WorldCycleRepository.Cycle readCycle(String world) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return new WorldCycleRepository.Cycle(0L, 0L);
            }
            return WorldCycleRepository.read(conn, world);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Mondes] Lecture du cycle " + world + " impossible : " + e.getMessage());
            return new WorldCycleRepository.Cycle(0L, System.currentTimeMillis() / 1000L);
        }
    }

    private static boolean playerNear(World world, int chunkX, int chunkZ) {
        for (Player player : world.getPlayers()) {
            int px = player.getLocation().getBlockX() >> 4;
            int pz = player.getLocation().getBlockZ() >> 4;
            if (Math.abs(px - chunkX) <= 2 && Math.abs(pz - chunkZ) <= 2) {
                return true;
            }
        }
        return false;
    }

    static boolean canRefresh(Material current) {
        if (current == null || current.isAir()) {
            return true;
        }
        String name = current.name();
        if (name.endsWith("_ORE") || name.startsWith("DEEPSLATE_") && name.endsWith("_ORE")) {
            return true;
        }
        return current == Material.STONE
                || current == Material.DEEPSLATE
                || current == Material.TUFF
                || current == Material.ANDESITE
                || current == Material.DIORITE
                || current == Material.GRANITE
                || current == Material.DIRT
                || current == Material.GRAVEL
                || current == Material.NETHERRACK
                || current == Material.BASALT
                || current == Material.BLACKSTONE
                || current == Material.ANCIENT_DEBRIS;
    }

    private record Pending(String world, boolean rare, long cycle, int chunkX, int chunkZ) {
    }
}
