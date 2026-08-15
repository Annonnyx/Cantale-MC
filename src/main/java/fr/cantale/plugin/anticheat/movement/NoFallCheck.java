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
 * Détection de NoFall Hack.
 *
 * Approche : on track manuellement le pic de la chute (fallPeakY) pendant que le
 * joueur est en l'air. À l'atterrissage, si la hauteur de chute dépasse le seuil,
 * on attend le {@code EntityDamageEvent} FALL correspondant. Si aucun dégât de
 * chute n'arrive dans les ticks suivants → violation.
 *
 * Ne lit JAMAIS {@code getFallDistance()} au moment de l'atterrissage (déjà reset
 * par le serveur) — source historique de faux positifs.
 *
 * Exemptions : eau (MLG), slime, lit, hay bale, powder snow, cobweb, échelles/lianes,
 * slow falling, élytra, véhicule, knockback récent.
 */
public class NoFallCheck extends Check {

    /** Délai de grâce après l'atterrissage pour recevoir le dégât de chute (ms) */
    private static final long FALL_DAMAGE_GRACE_MS = 400L;

    public NoFallCheck(AntiCheatConfig config) {
        super(CheckType.NOFALL, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Vérification différée : un atterrissage est en attente de son dégât de chute
        if (data.isExpectingFallDamage()
                && System.currentTimeMillis() - data.getFallDamageExpectSince() > FALL_DAMAGE_GRACE_MS) {
            double fallHeight = data.getExpectedFallHeight();
            data.clearFallExpectation();
            String details = String.format("fallHeight=%.1f aucun dégât de chute reçu", fallHeight);
            return CheckResult.violation(type, fallHeight, details);
        }

        if (isGloballyExempt(player, data)) {
            resetFallTracking(data);
            return CheckResult.pass(type);
        }

        double currentY = player.getLocation().getY();

        if (!player.isOnGround()) {
            // En l'air : suivre le pic de la chute
            if (!data.isFalling()) {
                data.setFalling(true);
                data.setFallPeakY(currentY);
            } else {
                data.setFallPeakY(Math.max(data.getFallPeakY(), currentY));
            }
            return CheckResult.pass(type);
        }

        // Au sol
        if (data.isFalling()) {
            data.setFalling(false);
            double fallHeight = data.getFallPeakY() - currentY;

            if (fallHeight >= config.getMinFallDistance() && !isLandingExempt(player)) {
                // Attendre le EntityDamageEvent FALL ; vérifié au prochain tick (ci-dessus)
                data.expectFallDamage(fallHeight);
            }
        }

        return CheckResult.pass(type);
    }

    private void resetFallTracking(PlayerData data) {
        data.setFalling(false);
        data.clearFallExpectation();
        data.setFallDistance(0);
    }

    private boolean isGloballyExempt(Player player, PlayerData data) {
        return player.isFlying()
                || player.getAllowFlight()
                || player.isGliding()
                || player.isRiptiding()
                || player.isInsideVehicle()
                || player.isInWater()
                || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)
                || data.isVelocityExempt()
                || data.isMovementExempt();
    }

    /**
     * Surfaces / contextes qui annulent ou réduisent fortement les dégâts de chute
     * de manière légitime.
     */
    private boolean isLandingExempt(Player player) {
        Location loc = player.getLocation();
        Material feet = loc.getBlock().getType();
        Block below = loc.clone().subtract(0, 0.5, 0).getBlock();
        Material belowType = below.getType();

        if (feet == Material.WATER || belowType == Material.WATER || player.isInWater()) {
            return true; // MLG water
        }

        return isFallNegating(feet) || isFallNegating(belowType);
    }

    private boolean isFallNegating(Material type) {
        String name = type.name();
        return type == Material.SLIME_BLOCK
                || type == Material.HAY_BLOCK
                || type == Material.POWDER_SNOW
                || type == Material.COBWEB
                || type == Material.LADDER
                || type == Material.SCAFFOLDING
                || name.contains("BED")              // rebond / dégâts réduits
                || name.contains("VINE")             // lianes (stoppe la chute)
                || name.contains("WATER");
    }
}
