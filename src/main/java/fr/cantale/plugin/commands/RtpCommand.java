package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.world.WorldTransit;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RtpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private static final int MIN_DISTANCE = 500;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 16;
    private static final long SEARCH_TIMEOUT_SECONDS = 15L;

    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public RtpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (!plugin.getTeleportDelayManager().checkTeleportAllowed(player, true)) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (!searching.add(playerId)) {
            player.sendMessage("§8[§6Cantale§8] §7Recherche d'une position déjà en cours...");
            return true;
        }

        String rtpName = WorldTransit.rtpWorld(player.getWorld().getName());
        World world = plugin.getServer().getWorld(rtpName);
        if (world == null) {
            searching.remove(playerId);
            player.sendMessage("§8[§6Cantale§8] §cLe monde de téléportation est introuvable.");
            return true;
        }
        Location origin = world.equals(player.getWorld())
                ? player.getLocation().clone()
                : world.getSpawnLocation().clone();

        WorldBorder border = world.getWorldBorder();
        double borderCenterX = border.getCenter().getX();
        double borderCenterZ = border.getCenter().getZ();
        double borderRadius = Math.max(200, border.getSize() / 2.0 - 50);

        int minDist = (int) Math.min(MIN_DISTANCE, borderRadius * 0.5);
        int maxDist = (int) Math.max(minDist + 250, Math.min(MAX_DISTANCE, borderRadius));

        player.sendMessage("§8[§6Cantale§8] §7Téléportation aléatoire...");

        SearchRequest request = new SearchRequest(
                playerId, world, origin, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);

        findLocationAsync(request)
                .orTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((target, error) -> runOnMain(() -> finishSearch(player, request, target, error)));
        return true;
    }

    private void finishSearch(Player player, SearchRequest request, Location target, Throwable error) {
        searching.remove(request.playerId());
        if (!player.isOnline() || !player.getUniqueId().equals(request.playerId())) {
            return;
        }
        if (error != null || target == null) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de trouver une position. Réessaie plus tard.");
            return;
        }

        Location origin = request.origin();
        plugin.getTeleportDelayManager().requestTeleport(player, target, true, () -> {
            World world = target.getWorld();
            if (world == null) {
                player.sendMessage("§8[§6Cantale§8] §cImpossible de trouver une position. Réessaie plus tard.");
                return false;
            }
            Location stillSafe = inspectIfSafe(world, target.getBlockX(), target.getBlockZ());
            if (stillSafe == null) {
                player.sendMessage("§8[§6Cantale§8] §cPosition devenue dangereuse. Réessaie /rtp.");
                return false;
            }
            player.teleport(stillSafe);
            player.sendMessage("§8[§6Cantale§8] §aTéléportation aléatoire effectuée ! §7("
                    + (int) stillSafe.distance(origin) + " blocs)");
            return true;
        });
    }

    private CompletableFuture<Location> findLocationAsync(SearchRequest request) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        tryAttempt(0, request, result);
        return result;
    }

    private void tryAttempt(int attempt, SearchRequest request, CompletableFuture<Location> result) {
        if (result.isDone()) {
            return;
        }
        if (attempt >= MAX_ATTEMPTS) {
            result.complete(null);
            return;
        }
        if (Bukkit.getPlayer(request.playerId()) == null) {
            result.complete(null);
            return;
        }

        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = request.minDist() + ThreadLocalRandom.current().nextDouble() * (request.maxDist() - request.minDist());

        int x = request.origin().getBlockX() + (int) (Math.cos(angle) * distance);
        int z = request.origin().getBlockZ() + (int) (Math.sin(angle) * distance);

        if (Math.abs(x - request.borderCenterX()) >= request.borderRadius()
                || Math.abs(z - request.borderCenterZ()) >= request.borderRadius()) {
            tryAttempt(attempt + 1, request, result);
            return;
        }

        World world = request.world();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            Location found = inspectIfSafe(world, x, z);
            if (found != null) {
                result.complete(found);
                return;
            }
            tryAttempt(attempt + 1, request, result);
            return;
        }

        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> runOnMain(() -> {
            if (result.isDone()) {
                return;
            }
            if (error != null || chunk == null) {
                tryAttempt(attempt + 1, request, result);
                return;
            }
            Location found = inspectLoadedChunk(chunk, x, z);
            if (found != null) {
                result.complete(found);
                return;
            }
            tryAttempt(attempt + 1, request, result);
        }));
    }

    private Location inspectIfSafe(World world, int x, int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return inspectLoadedChunk(world.getChunkAt(x >> 4, z >> 4), x, z);
    }

    private Location inspectLoadedChunk(Chunk chunk, int x, int z) {
        World world = chunk.getWorld();
        int localX = x & 15;
        int localZ = z & 15;

        // Dans le Nether, scanner à l'intérieur des cavernes (y = 120 -> 35) pour ne JAMAIS atterrir sur le toit.
        if (world.getEnvironment() == World.Environment.NETHER || world.getName().toLowerCase().contains("nether")) {
            for (int y = 120; y >= 35; y--) {
                Material ground = chunk.getBlock(localX, y, localZ).getType();
                if (ground.isSolid() && !isUnsafeGround(ground) && ground != Material.BEDROCK) {
                    Material above1 = chunk.getBlock(localX, y + 1, localZ).getType();
                    Material above2 = chunk.getBlock(localX, y + 2, localZ).getType();
                    if (above1.isAir() && above2.isAir()) {
                        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
                    }
                }
            }
            return null;
        }

        int y = world.getHighestBlockYAt(x, z);
        if (y <= 0 || y + 2 >= world.getMaxHeight()) {
            return null;
        }

        Material ground = chunk.getBlock(localX, y, localZ).getType();
        if (isUnsafeGround(ground)) {
            return null;
        }

        Material above1 = chunk.getBlock(localX, y + 1, localZ).getType();
        Material above2 = chunk.getBlock(localX, y + 2, localZ).getType();
        if (!above1.isAir() || !above2.isAir()) {
            return null;
        }

        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    private boolean isUnsafeGround(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN ||
               material == Material.KELP || material == Material.KELP_PLANT ||
               material == Material.SEAGRASS || material == Material.TALL_SEAGRASS ||
               material == Material.LAVA || material == Material.MAGMA_BLOCK ||
               material == Material.CACTUS || material == Material.SWEET_BERRY_BUSH ||
               material == Material.POWDER_SNOW || material == Material.FIRE ||
               material == Material.SOUL_FIRE || material == Material.CAMPFIRE ||
               material == Material.SOUL_CAMPFIRE || material == Material.VOID_AIR;
    }

    private void runOnMain(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private record SearchRequest(
            UUID playerId,
            World world,
            Location origin,
            int minDist,
            int maxDist,
            double borderCenterX,
            double borderCenterZ,
            double borderRadius
    ) {}
}
