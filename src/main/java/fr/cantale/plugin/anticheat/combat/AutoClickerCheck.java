package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection d'AutoClicker (CPS combat uniquement).
 *
 * Les clics sur blocs soft/instant (herbe, fleurs, neige…) et le minage en général
 * ne passent pas par ce check — voir {@code FastBreakCheck} pour le débit de casse solide.
 *
 * Mesure le CPS sur une fenêtre configurable (défaut 2 s), avec buffer VL avant
 * pénalité trust. Détecte aussi une régularité anormale des intervalles.
 */
public class AutoClickerCheck extends Check {

    /** Écart-type minimum entre clics en ms (en dessous = suspect) */
    private static final double MIN_CLICK_DEVIATION = 12.0;

    public AutoClickerCheck(AntiCheatConfig config) {
        super(CheckType.AUTOCLICKER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Enregistre un clic combat (air ou hit d'entité) et vérifie CPS + régularité.
     */
    public CheckResult checkClick(Player player, PlayerData data) {
        data.addClick(System.currentTimeMillis());

        int windowMs = config.getCpsWindowMs();
        int cps = data.getCPS(windowMs);
        int maxCPS = config.getMaxCPS();
        int bufferNeeded = config.getAutoClickerBuffer();

        if (cps > maxCPS) {
            data.incrementAutoClickerBuffer();
            if (data.getAutoClickerBuffer() < bufferNeeded) {
                return CheckResult.pass(type);
            }
            String details = String.format("cps=%d max=%d window=%dms", cps, maxCPS, windowMs);
            return CheckResult.violation(type, cps - maxCPS, details);
        }

        // Régularité anormale : échantillon plus large, CPS déjà élevé
        double deviation = data.getClickDeviation(windowMs);
        if (deviation < MIN_CLICK_DEVIATION && cps >= 14) {
            data.incrementAutoClickerBuffer();
            if (data.getAutoClickerBuffer() < bufferNeeded) {
                return CheckResult.pass(type);
            }
            String details = String.format("deviation=%.1fms cps=%d (trop régulier)", deviation, cps);
            return CheckResult.violation(type, 1.0, details);
        }

        data.resetAutoClickerBuffer();
        return CheckResult.pass(type);
    }
}
