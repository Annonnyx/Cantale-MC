package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection de NoFall Hack.
 *
 * Vérifie que les dégâts de chute sont cohérents avec la distance de chute.
 * Si le joueur tombe d'une hauteur significative sans prendre de dégâts → flag.
 *
 * Le check fonctionne en mesurant la distance de chute manuellement
 * (via les positions stockées dans PlayerData) puis en vérifiant
 * si le joueur atterrit sans dégâts correspondants.
 */
public class NoFallCheck extends Check {

    public NoFallCheck(AntiCheatConfig config) {
        super(CheckType.NOFALL, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        if (player.isFlying() || player.getAllowFlight() || player.isGliding()) {
            data.setFallDistance(0);
            data.setFalling(false);
            return CheckResult.pass(type);
        }

        // Ignorer si dans l'eau
        if (player.isInWater()) {
            data.setFallDistance(0);
            data.setFalling(false);
            return CheckResult.pass(type);
        }

        // Ignorer si Slow Falling actif
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
            data.setFallDistance(0);
            data.setFalling(false);
            return CheckResult.pass(type);
        }

        double currentY = player.getLocation().getY();

        if (!player.isOnGround()) {
            // Le joueur est en l'air
            if (!data.isFalling() && data.wasOnGround()) {
                // Début de la chute : enregistrer le Y de départ
                data.setLastGroundY(currentY);
                data.setFalling(true);
            }
        } else {
            // Le joueur est au sol
            if (data.isFalling()) {
                // Fin de la chute
                double fallHeight = data.getLastGroundY() - currentY;
                data.setFalling(false);

                if (fallHeight >= config.getMinFallDistance()) {
                    // Le joueur aurait dû prendre des dégâts
                    // Vérifier le fall damage de Bukkit (server-side)
                    float serverFallDistance = player.getFallDistance();

                    // Si le serveur dit que la fall distance est 0 mais on a mesuré une chute importante
                    if (serverFallDistance < 1.0 && fallHeight >= config.getMinFallDistance()) {
                        String details = String.format("fallHeight=%.1f serverFall=%.1f", fallHeight, serverFallDistance);
                        data.setFallDistance(0);
                        return CheckResult.violation(type, fallHeight, details);
                    }
                }

                data.setFallDistance(0);
            }
        }

        return CheckResult.pass(type);
    }
}
