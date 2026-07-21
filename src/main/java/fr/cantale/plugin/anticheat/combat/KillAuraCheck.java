package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Détection de KillAura.
 *
 * Vérifie si le joueur attaque plusieurs cibles différentes de manière
 * anormalement rapide (changements de cible en moins de 3 ticks).
 * Un joueur normal ne peut pas switcher de cible et attaquer aussi vite.
 */
public class KillAuraCheck extends Check {

    public KillAuraCheck(AntiCheatConfig config) {
        super(CheckType.KILLAURA, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Appelé via CombatListener
        return CheckResult.pass(type);
    }

    /**
     * Vérifie le changement de cible pour détecter un KillAura.
     *
     * @param attacker l'attaquant
     * @param targetId l'UUID de la nouvelle cible
     * @param data     les données du joueur
     * @return le résultat du check
     */
    public CheckResult checkTargetSwitch(Player attacker, UUID targetId, PlayerData data) {
        UUID lastTarget = data.getLastTargetId();
        long lastAttack = data.getLastAttackTimestamp();
        long now = System.currentTimeMillis();

        if (lastTarget != null && !lastTarget.equals(targetId)) {
            // Le joueur a changé de cible
            long timeSinceLastAttack = now - lastAttack;

            // Convertir le seuil en ticks vers ms (1 tick = 50ms)
            long minSwitchTime = (long) config.getMinTargetSwitchTicks() * 50;

            if (timeSinceLastAttack < minSwitchTime) {
                data.incrementRapidTargetSwitches();

                // Flag seulement si le joueur fait des switches rapides répétés
                if (data.getRapidTargetSwitches() >= 3) {
                    data.setRapidTargetSwitches(0);
                    String details = String.format("switchTime=%dms min=%dms switches=%d",
                            timeSinceLastAttack, minSwitchTime, 3);
                    return CheckResult.violation(type, 1.0, details);
                }
            } else {
                // Reset si le switch est assez lent
                data.setRapidTargetSwitches(0);
            }
        }

        // Mettre à jour les données
        data.setLastTargetId(targetId);
        data.setLastAttackTimestamp(now);

        return CheckResult.pass(type);
    }
}
