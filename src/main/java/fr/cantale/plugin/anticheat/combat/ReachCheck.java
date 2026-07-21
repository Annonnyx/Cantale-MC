package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Détection de Reach Hack.
 *
 * Vérifie que la distance entre l'attaquant et la cible ne dépasse pas
 * la portée maximale autorisée. La portée vanilla est de 3.0 blocs ;
 * un seuil de 3.5 est utilisé par défaut pour compenser le lag réseau.
 */
public class ReachCheck extends Check {

    public ReachCheck(AntiCheatConfig config) {
        super(CheckType.REACH, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Ce check est appelé manuellement depuis CombatListener
        // avec les données de la cible déjà définies
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la distance d'attaque entre un joueur et une cible.
     *
     * @param attacker l'attaquant
     * @param target   la cible
     * @return le résultat du check
     */
    public CheckResult checkReach(Player attacker, org.bukkit.entity.Entity target) {
        Location attackerLoc = attacker.getLocation();
        Location targetLoc = target.getLocation();

        if (!attackerLoc.getWorld().equals(targetLoc.getWorld())) {
            return CheckResult.pass(type);
        }

        // Calculer la distance 3D entre l'attaquant et la cible
        double distance = attackerLoc.distance(targetLoc);

        // Compenser la hitbox de la cible (environ 0.3 blocs de chaque côté)
        distance -= 0.3;

        double maxReach = config.getMaxReach();

        if (distance > maxReach) {
            String details = String.format("dist=%.2f max=%.2f", distance + 0.3, maxReach);
            return CheckResult.violation(type, distance - maxReach, details);
        }

        return CheckResult.pass(type);
    }
}
