package fr.cantale.plugin.anticheat.inventory;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection de FastUse (FastEat / FastPlace).
 *
 * Vérifie si le joueur consomme de la nourriture ou utilise des items
 * plus rapidement que la vitesse normale. La consommation vanilla
 * prend environ 32 ticks (1.6 secondes).
 */
public class FastUseCheck extends Check {

    /** Temps minimum de consommation en millisecondes (32 ticks = 1600ms, avec marge) */
    private static final long MIN_CONSUME_TIME_MS = 1400;

    private long lastConsumeStart;

    public FastUseCheck(AntiCheatConfig config) {
        super(CheckType.FAST_USE, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Appelé quand un joueur commence à consommer un item.
     */
    public void onConsumeStart() {
        this.lastConsumeStart = System.currentTimeMillis();
    }

    /**
     * Vérifie la vitesse de consommation.
     *
     * @param player le joueur
     * @return le résultat du check
     */
    public CheckResult checkConsume(Player player) {
        if (lastConsumeStart == 0) {
            return CheckResult.pass(type);
        }

        long elapsed = System.currentTimeMillis() - lastConsumeStart;
        lastConsumeStart = 0;

        if (elapsed < MIN_CONSUME_TIME_MS) {
            String details = String.format("consumeTime=%dms min=%dms", elapsed, MIN_CONSUME_TIME_MS);
            return CheckResult.violation(type, 1.0, details);
        }

        return CheckResult.pass(type);
    }
}
