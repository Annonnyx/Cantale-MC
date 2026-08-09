package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RtpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;
    private static final long COOLDOWN_MS = 20000; // 20 secondes
    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 25;
    private static final long TIMEOUT_SECONDS = 30;

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

        // Capturé sur le thread principal : la bordure borne la distance de recherche
        WorldBorder border = world.getWorldBorder();
        final double borderCenterX = border.getCenter().getX();
        final double borderCenterZ = border.getCenter().getZ();
        final double borderRadius = Math.max(200, border.getSize() / 2.0 - 100);
        final int minDist = (int) Math.min(MIN_DISTANCE, borderRadius * 0.5);
        final int maxDist = (int) Math.max(minDist + 250, Math.min(MAX_DISTANCE, borderRadius));

        player.sendMessage("§8[§6Cantale§8] §7Recherche d'une position sûre en cours...");

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            findSafeLocation(world, origin, 0, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius)
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

    private CompletableFuture<Location> findSafeLocation(World world, Location origin, int attempt,
                                                         int minDist, int maxDist,
                                                         double borderCenterX, double borderCenterZ, double borderRadius) {
        if (attempt >= MAX_ATTEMPTS) {
            return CompletableFuture.completedFuture(null);
        }

        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

        int x = origin.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = origin.getBlockZ() + (int) (Math.sin(angle) * distance);
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;

        CompletableFuture<org.bukkit.Chunk> chunkFuture;
        try {
            chunkFuture = world.getChunkAtAsync(chunkX, chunkZ, true);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur RTP getChunkAtAsync: " + e.getMessage());
            return findSafeLocation(world, origin, attempt + 1, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);
        }

        return chunkFuture.thenCompose(chunk -> {
            if (chunk == null) {
                return findSafeLocation(world, origin, attempt + 1, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);
            }

            // Chaque chunk chargé offre 256 colonnes candidates : on les scanne toutes
            // au lieu de n'en tester qu'une seule par chargement.
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, false);
            List<Location> candidates = new ArrayList<>();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int y = snapshot.getHighestBlockYAt(lx, lz);
                    if (y <= 0 || y + 2 >= world.getMaxHeight()) continue;

                    Material ground = snapshot.getBlockType(lx, y, lz);
                    if (isUnsafeGround(ground)) continue;

                    Biome biome = snapshot.getBiome(lx, y, lz);
                    if (isOceanBiome(biome)) continue;

                    Material above1 = snapshot.getBlockType(lx, y + 1, lz);
                    Material above2 = snapshot.getBlockType(lx, y + 2, lz);
                    if (!above1.isAir() || !above2.isAir()) continue;

                    double wx = (chunkX << 4) + lx + 0.5;
                    double wz = (chunkZ << 4) + lz + 0.5;
                    if (Math.abs(wx - borderCenterX) >= borderRadius
                            || Math.abs(wz - borderCenterZ) >= borderRadius) continue;

                    candidates.add(new Location(world, wx, y + 1.0, wz));
                }
            }

            if (candidates.isEmpty()) {
                return findSafeLocation(world, origin, attempt + 1, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);
            }

            Location target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            plugin.getLogger().info("[RTP] Position sûre trouvée après " + (attempt + 1) + " tentative(s) : "
                    + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
            return CompletableFuture.completedFuture(target);
        }).exceptionallyCompose(ex -> {
            plugin.getLogger().warning("Erreur RTP tentative " + attempt + ": " + ex.getMessage());
            return findSafeLocation(world, origin, attempt + 1, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);
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
