package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection d'AutoClicker.
 *
 * Mesure le CPS (Clicks Per Second) sur une fenêtre glissante de 1 seconde.
 * Un CPS trop élevé (>20) est suspect. Détecte aussi la régularité anormale
 * des intervalles entre clics (écart-type trop faible = autoclicker).
 */
public class AutoClickerCheck extends Check {

    /** Écart-type minimum entre clics en ms (en dessous = suspect) */
    private static final double MIN_CLICK_DEVIATION = 15.0;

    public AutoClickerCheck(AntiCheatConfig config) {
        super(CheckType.AUTOCLICKER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Appelé via CombatListener
        return CheckResult.pass(type);
    }

    /**
     * Enregistre un clic et vérifie le CPS + la régularité.
     *
     * @param player le joueur
     * @param data   les données du joueur
     * @return le résultat du check
     */
    public CheckResult checkClick(Player player, PlayerData data) {
        data.addClick(System.currentTimeMillis());

        int cps = data.getCPS();
        int maxCPS = config.getMaxCPS();

        if (cps > maxCPS) {
            String details = String.format("cps=%d max=%d", cps, maxCPS);
            return CheckResult.violation(type, cps - maxCPS, details);
        }

        // Vérifier la régularité anormale des clics
        double deviation = data.getClickDeviation();
        if (deviation < MIN_CLICK_DEVIATION && cps >= 10) {
            String details = String.format("deviation=%.1fms cps=%d (trop régulier)", deviation, cps);
            return CheckResult.violation(type, 1.0, details);
        }

        return CheckResult.pass(type);
    }
}
