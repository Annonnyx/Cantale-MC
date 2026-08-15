package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Détection de Speed Hack.
 *
 * Compare la distance horizontale parcourue par tick à la vitesse maximale autorisée.
 * Exempte TP, véhicules, élytra, riptide, nage/eau, vol autorisé.
 */
public class SpeedCheck extends Check {

    public SpeedCheck(AntiCheatConfig config) {
        super(CheckType.SPEED, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Grâce post-TP / knockback / explosion : la vélocité fausse la mesure
        if (data.isMovementExempt() || data.isVelocityExempt()) {
            data.resetSpeedBuffer();
            return CheckResult.pass(type);
        }

        // Véhicules (bateau, cheval, minecart, cochon…)
        if (player.isInsideVehicle()) {
            data.resetSpeedBuffer();
            return CheckResult.pass(type);
        }

        // Élytra / riptide / vol légitime — vitesses hors échelle sprint
        if (player.isGliding() || player.isRiptiding() || player.isFlying() || player.getAllowFlight()) {
            data.resetSpeedBuffer();
            return CheckResult.pass(type);
        }

        Location from = data.getLastLocation();
        Location to = player.getLocation();

        if (from == null || to.getWorld() == null || from.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return CheckResult.pass(type);
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        double maxSpeed = config.getMaxSpeed();

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            maxSpeed += amplifier * 0.07;
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            maxSpeed -= amplifier * 0.05;
        }

        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            maxSpeed *= 2.2;
        }

        // Nage / eau / lave : vitesses variables (courants, depth strider) — large tolérance
        if (player.isSwimming() || player.isInWater() || isInLava(player)) {
            maxSpeed = Math.max(maxSpeed, 0.95);
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.DEPTH_STRIDER)) {
                int level = boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
                maxSpeed += level * 0.15;
            }
        }

        if (isOnIce(from) || isOnIce(to)) {
            maxSpeed *= 1.6;
        }

        if (isOnSoul(from) || isOnSoul(to)) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.SOUL_SPEED)) {
                int level = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
                maxSpeed *= 1.4 + (level * 0.25);
            }
        }

        if (!player.isOnGround()) {
            maxSpeed *= 1.35;
        }

        if (horizontalDistance > maxSpeed) {
            data.incrementSpeedBuffer();
            if (data.getSpeedBuffer() >= config.getSpeedBuffer()) {
                data.resetSpeedBuffer();
                String details = String.format("dist=%.3f max=%.3f", horizontalDistance, maxSpeed);
                return CheckResult.violation(type, horizontalDistance - maxSpeed, details);
            }
        } else if (data.getSpeedBuffer() > 0) {
            data.setSpeedBuffer(data.getSpeedBuffer() - 1);
        }

        return CheckResult.pass(type);
    }

    private boolean isOnIce(Location location) {
        Material type = blockBelow(location);
        return type == Material.ICE
                || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE
                || type == Material.FROSTED_ICE;
    }

    private boolean isOnSoul(Location location) {
        Material type = blockBelow(location);
        return type == Material.SOUL_SAND || type == Material.SOUL_SOIL;
    }

    private Material blockBelow(Location location) {
        if (location == null || location.getWorld() == null) return Material.AIR;
        Block below = location.clone().subtract(0, 0.2, 0).getBlock();
        return below.getType();
    }

    private boolean isInLava(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LAVA || type == Material.LAVA_CAULDRON;
    }
}
