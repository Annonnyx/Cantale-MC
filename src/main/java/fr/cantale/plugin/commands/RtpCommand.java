package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RtpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;
    private static final long COOLDOWN_MS = 20000; // 20 secondes
    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 20;
    private static final long TIMEOUT_SECONDS = 10;

    public RtpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (cooldownManager.hasCooldown(player, "rtp", COOLDOWN_MS)) {
            long remaining = cooldownManager.getRemainingCooldown(player, "rtp", COOLDOWN_MS);
            player.sendMessage("§8[§6Cantale§8] §cCooldown ! Attendez encore " + (remaining / 1000) + " secondes.");
            return true;
        }

        World world = player.getWorld();
        Location origin = player.getLocation();

        player.sendMessage("§8[§6Cantale§8] §7Recherche d'une position sûre en cours...");

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            findSafeLocation(world, origin, 0)
                    .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((target, throwable) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null || target == null) {
                            player.sendMessage("§8[§6Cantale§8] §cImpossible de trouver une position sûre. Réessaie plus tard.");
                            return;
                        }

                        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
                            player.teleport(target);
                            player.sendMessage("§8[§6Cantale§8] §aTéléportation aléatoire effectuée ! §7(" + (int) target.distance(origin) + " blocs)");
                            cooldownManager.setCooldown(player, "rtp");
                        });
                    }));
        });

        return true;
    }

    private CompletableFuture<Location> findSafeLocation(World world, Location origin, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return CompletableFuture.completedFuture(null);
        }

        double angle = Math.random() * 2 * Math.PI;
        double distance = MIN_DISTANCE + Math.random() * (MAX_DISTANCE - MIN_DISTANCE);

        int x = origin.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = origin.getBlockZ() + (int) (Math.sin(angle) * distance);
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        final int xLocal = x & 15;
        final int zLocal = z & 15;

        CompletableFuture<org.bukkit.Chunk> chunkFuture;
        try {
            chunkFuture = world.getChunkAtAsync(chunkX, chunkZ, true);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur RTP getChunkAtAsync: " + e.getMessage());
            return findSafeLocation(world, origin, attempt + 1);
        }

        return chunkFuture.thenCompose(chunk -> {
            if (chunk == null) {
                return findSafeLocation(world, origin, attempt + 1);
            }

            ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, false);
            int y = snapshot.getHighestBlockYAt(xLocal, zLocal);
            Material ground = snapshot.getBlockType(xLocal, y, zLocal);
            Biome biome = snapshot.getBiome(xLocal, y, zLocal);

            if (isUnsafeGround(ground) || isOceanBiome(biome) || y <= 0) {
                return findSafeLocation(world, origin, attempt + 1);
            }

            // Vérifier que les 2 blocs au-dessus sont traversables (pas dans un arbre / roche)
            Material above1 = snapshot.getBlockType(xLocal, y + 1, zLocal);
            Material above2 = snapshot.getBlockType(xLocal, y + 2, zLocal);
            if (!above1.isAir() || !above2.isAir()) {
                return findSafeLocation(world, origin, attempt + 1);
            }

            return CompletableFuture.completedFuture(new Location(world, x + 0.5, y + 1.0, z + 0.5));
        }).exceptionallyCompose(ex -> {
            plugin.getLogger().warning("Erreur RTP tentative " + attempt + ": " + ex.getMessage());
            return findSafeLocation(world, origin, attempt + 1);
        });
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

    private boolean isOceanBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("ocean") || name.contains("beach") || name.contains("river");
    }
}
