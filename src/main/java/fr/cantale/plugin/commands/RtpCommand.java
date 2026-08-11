package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;
    /** Cooldown joueur / aventurier — VIP+ sans cooldown. */
    private static final long COOLDOWN_MS = 120_000L; // 2 minutes
    private static final int MIN_DISTANCE = 500;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 16;

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

        PlayerRank rank = plugin.getRankManager().getRank(player.getUniqueId());
        boolean bypassCooldown = rank.isAtLeastVIP()
                || player.hasPermission("cantale.admin")
                || player.hasPermission("cantale.cooldown.bypass");

        if (!bypassCooldown && cooldownManager.hasCooldown(player, "rtp", COOLDOWN_MS)) {
            long remaining = cooldownManager.getRemainingCooldown(player, "rtp", COOLDOWN_MS);
            long seconds = Math.max(1, (remaining + 999) / 1000);
            player.sendMessage("§8[§6Cantale§8] §cCooldown ! Attendez encore " + seconds + " seconde(s).");
            return true;
        }

        World world = player.getWorld();
        Location origin = player.getLocation();

        WorldBorder border = world.getWorldBorder();
        double borderCenterX = border.getCenter().getX();
        double borderCenterZ = border.getCenter().getZ();
        double borderRadius = Math.max(200, border.getSize() / 2.0 - 50);

        int minDist = (int) Math.min(MIN_DISTANCE, borderRadius * 0.5);
        int maxDist = (int) Math.max(minDist + 250, Math.min(MAX_DISTANCE, borderRadius));

        player.sendMessage("§8[§6Cantale§8] §7Téléportation aléatoire...");

        Location target = findLocation(world, origin, minDist, maxDist, borderCenterX, borderCenterZ, borderRadius);
        if (target == null) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de trouver une position. Réessaie plus tard.");
            return true;
        }

        final boolean applyCooldown = !bypassCooldown;
        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(target);
            player.sendMessage("§8[§6Cantale§8] §aTéléportation aléatoire effectuée ! §7(" + (int) target.distance(origin) + " blocs)");
            if (applyCooldown) {
                cooldownManager.setCooldown(player, "rtp");
            }
        });
        return true;
    }

    private Location findLocation(World world, Location origin, int minDist, int maxDist,
                                  double borderCenterX, double borderCenterZ, double borderRadius) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

            int x = origin.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) (Math.sin(angle) * distance);

            if (Math.abs(x - borderCenterX) >= borderRadius || Math.abs(z - borderCenterZ) >= borderRadius) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);
            if (y <= 0) continue;

            Material ground = world.getBlockAt(x, y, z).getType();
            if (isUnsafeGround(ground)) continue;

            Material above1 = world.getBlockAt(x, y + 1, z).getType();
            Material above2 = world.getBlockAt(x, y + 2, z).getType();
            if (!above1.isAir() || !above2.isAir()) continue;

            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
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
}
