package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Détection de Step Hack.
 *
 * Détecte un changement Y supérieur à la hauteur normale d'un pas (0.6 blocs)
 * en un seul tick, sans saut légitime. Ignore les escaliers, slabs, et blocs
 * qui permettent naturellement un déplacement Y élevé.
 */
public class StepCheck extends Check {

    public StepCheck(AntiCheatConfig config) {
        super(CheckType.STEP, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        Location from = data.getLastLocation();
        Location to = player.getLocation();

        if (from == null || !from.getWorld().equals(to.getWorld())) {
            return CheckResult.pass(type);
        }

        // Knockback / explosion récent : deltaY non fiable
        if (data.isVelocityExempt() || data.isMovementExempt()) {
            return CheckResult.pass(type);
        }

        // Ignorer si le joueur est en vol ou en élytre
        if (player.isFlying() || player.isGliding() || player.getAllowFlight()) {
            return CheckResult.pass(type);
        }

        // Ignorer si dans un véhicule
        if (player.isInsideVehicle()) {
            return CheckResult.pass(type);
        }

        double deltaY = to.getY() - from.getY();

        // On ne vérifie que les montées (deltaY positif)
        if (deltaY <= 0) {
            return CheckResult.pass(type);
        }

        // Le deltaY d'un saut normal est ~0.42 au premier tick
        // Un step hack permet de monter d'un bloc entier (1.0) ou plus
        if (deltaY > config.getMaxStepHeight()) {
            // Vérifier si le bloc est un escalier ou un slab (montée légitime)
            if (isStairOrSlab(from) || isStairOrSlab(to)) {
                return CheckResult.pass(type);
            }

            // Vérifier la vélocité Y (un saut a un velocity Y positif élevé)
            double velocityY = player.getVelocity().getY();
            if (velocityY > 0.3) {
                // Probablement un saut légitime
                return CheckResult.pass(type);
            }

            // Tolérance pour slime blocks / piston push
            Location below = from.clone().subtract(0, 1, 0);
            Material blockBelow = below.getBlock().getType();
            if (blockBelow == Material.SLIME_BLOCK || blockBelow == Material.PISTON_HEAD) {
                return CheckResult.pass(type);
            }

            String details = String.format("deltaY=%.3f max=%.3f velY=%.3f",
                    deltaY, config.getMaxStepHeight(), velocityY);
            return CheckResult.violation(type, deltaY, details);
        }

        return CheckResult.pass(type);
    }

    private boolean isStairOrSlab(Location location) {
        Material type = location.getBlock().getType();
        String name = type.name();
        return name.contains("STAIRS") || name.contains("SLAB") || name.contains("STEP");
    }
}
