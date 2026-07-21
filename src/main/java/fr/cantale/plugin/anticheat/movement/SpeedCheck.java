package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Détection de Speed Hack.
 *
 * Compare la distance horizontale parcourue par tick à la vitesse maximale autorisée.
 * Prend en compte les effets de potion (Speed), la glace, le sprint, etc.
 * Utilise un buffer de violations consécutives pour éviter les faux positifs.
 */
public class SpeedCheck extends Check {

    public SpeedCheck(AntiCheatConfig config) {
        super(CheckType.SPEED, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        Location from = data.getLastLocation();
        Location to = player.getLocation();

        if (from == null || !from.getWorld().equals(to.getWorld())) {
            return CheckResult.pass(type);
        }

        // Distance horizontale seulement (ignorer Y)
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Calculer la vitesse max autorisée
        double maxSpeed = config.getMaxSpeed();

        // Multiplicateur pour l'effet Speed
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            maxSpeed += amplifier * 0.07;
        }

        // Multiplicateur pour l'effet Slowness
        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            maxSpeed -= amplifier * 0.05;
        }

        // Tolérance pour la glace (soul speed, glace bleue)
        if (isOnIce(from)) {
            maxSpeed *= 1.6;
        }

        // Tolérance si le joueur est en l'air (saut + sprint = plus rapide)
        if (!player.isOnGround()) {
            maxSpeed *= 1.3;
        }

        // Tolérance si dans un véhicule
        if (player.isInsideVehicle()) {
            return CheckResult.pass(type);
        }

        // Appliquer le multiplicateur de sévérité basé sur le trust
        // (plus le trust est bas, plus le seuil est réduit)

        if (horizontalDistance > maxSpeed) {
            data.incrementSpeedBuffer();
            if (data.getSpeedBuffer() >= config.getSpeedBuffer()) {
                data.resetSpeedBuffer();
                String details = String.format("dist=%.3f max=%.3f", horizontalDistance, maxSpeed);
                return CheckResult.violation(type, horizontalDistance - maxSpeed, details);
            }
        } else {
            // Reset le buffer si le mouvement est légitime
            if (data.getSpeedBuffer() > 0) {
                data.setSpeedBuffer(data.getSpeedBuffer() - 1);
            }
        }

        return CheckResult.pass(type);
    }

    private boolean isOnIce(Location location) {
        if (location == null) return false;
        org.bukkit.block.Block below = location.clone().subtract(0, 1, 0).getBlock();
        org.bukkit.Material type = below.getType();
        return type == org.bukkit.Material.ICE
                || type == org.bukkit.Material.PACKED_ICE
                || type == org.bukkit.Material.BLUE_ICE
                || type == org.bukkit.Material.FROSTED_ICE;
    }
}
