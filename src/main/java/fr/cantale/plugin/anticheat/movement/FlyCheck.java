package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Détection de Fly Hack.
 *
 * Vérifie si le joueur reste en l'air un nombre anormal de ticks
 * sans justification (élytre, creative, slime block, eau, etc.).
 */
public class FlyCheck extends Check {

    public FlyCheck(AntiCheatConfig config) {
        super(CheckType.FLY, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Knockback / explosion récent : trajectoire non fiable
        if (data.isVelocityExempt() || data.isMovementExempt()) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si le joueur est autorisé à voler
        if (player.isFlying() || player.getAllowFlight()) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si en élytre
        if (player.isGliding()) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si dans l'eau ou la lave
        if (player.isInWater() || isInLava(player)) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si dans un véhicule
        if (player.isInsideVehicle()) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si le joueur grimpe (échelle, vigne)
        if (isClimbing(player)) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si sur un slime block ou un bed (rebond)
        if (isOnBouncyBlock(player)) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si le joueur a du Levitation ou Slow Falling
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)
                || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        // Ignorer si le joueur utilise Riptide (trident)
        if (player.isRiptiding()) {
            data.setAirTicks(0);
            return CheckResult.pass(type);
        }

        if (player.isOnGround()) {
            data.setAirTicks(0);
            data.setWasOnGround(true);
            return CheckResult.pass(type);
        }

        // Le joueur est en l'air
        data.incrementAirTicks();

        // Vélocité Y positive ou nulle pendant trop longtemps = suspect
        double velocityY = player.getVelocity().getY();
        int airTicks = data.getAirTicks();

        if (airTicks > config.getMaxAirTicks()) {
            // Vérifier que ce n'est pas juste un grand saut
            if (velocityY >= -0.1) {
                String details = String.format("airTicks=%d max=%d velY=%.3f",
                        airTicks, config.getMaxAirTicks(), velocityY);
                data.setAirTicks(0); // Reset pour ne pas spam
                return CheckResult.violation(type, airTicks - config.getMaxAirTicks(), details);
            }
        }

        return CheckResult.pass(type);
    }

    private boolean isInLava(Player player) {
        Material block = player.getLocation().getBlock().getType();
        return block == Material.LAVA;
    }

    private boolean isClimbing(Player player) {
        Material block = player.getLocation().getBlock().getType();
        return block == Material.LADDER
                || block == Material.VINE
                || block == Material.TWISTING_VINES
                || block == Material.WEEPING_VINES
                || block == Material.CAVE_VINES;
    }

    private boolean isOnBouncyBlock(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.5, 0);
        Block block = below.getBlock();
        Material type = block.getType();
        return type == Material.SLIME_BLOCK
                || type.name().contains("BED");
    }
}
