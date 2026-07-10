package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RtpCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;
    private static final long COOLDOWN_MS = 20000; // 20 secondes
    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 30;

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

        // Vérifier le cooldown
        if (cooldownManager.hasCooldown(player, "rtp", COOLDOWN_MS)) {
            long remaining = cooldownManager.getRemainingCooldown(player, "rtp", COOLDOWN_MS);
            player.sendMessage("§8[§6Cantale§8] §cCooldown ! Attendez encore " + (remaining / 1000) + " secondes.");
            return true;
        }

        World world = player.getWorld();
        Location origin = player.getLocation();
        Location target = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = MIN_DISTANCE + Math.random() * (MAX_DISTANCE - MIN_DISTANCE);

            int x = origin.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) (Math.sin(angle) * distance);

            // Pré-charger le chunk (synchrone, mais RTP est déjà une commande joueur)
            world.getChunkAt(x >> 4, z >> 4).load(true);

            int y = world.getHighestBlockYAt(x, z);
            Block highest = world.getBlockAt(x, y, z);

            // Éviter l'eau et les biomes océaniques
            if (isWater(highest.getType())) continue;
            Biome biome = world.getBiome(x, y, z);
            if (isOceanBiome(biome)) continue;

            // Vérifier qu'on ne tombe pas dans le vide (y > 0)
            if (y <= 0) continue;

            target = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            break;
        }

        if (target == null) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de trouver une position sûre. Réessaie plus tard.");
            return true;
        }

        final Location finalTarget = target;
        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(finalTarget);
            player.sendMessage("§8[§6Cantale§8] §aTéléportation aléatoire effectuée ! §7(" +
                (int) finalTarget.distance(origin) + " blocs)");
            cooldownManager.setCooldown(player, "rtp");
        });
        return true;
    }

    private boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN ||
               material == Material.KELP || material == Material.KELP_PLANT ||
               material == Material.SEAGRASS || material == Material.TALL_SEAGRASS;
    }

    private boolean isOceanBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("ocean") || name.contains("beach");
    }
}
