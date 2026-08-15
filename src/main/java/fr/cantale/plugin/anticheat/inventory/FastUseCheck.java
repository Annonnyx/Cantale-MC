package fr.cantale.plugin.anticheat.inventory;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection de FastUse (FastEat).
 *
 * La consommation vanilla prend 32 ticks (1,6 s). Le début de consommation est
 * tracké par joueur dans {@link PlayerData} (via PlayerInteractEvent) — l'état
 * partagé historique entre joueurs a été supprimé (faux positifs croisés).
 */
public class FastUseCheck extends Check {

    /** Temps minimum de consommation en millisecondes (32 ticks = 1600ms, avec marge) */
    private static final long MIN_CONSUME_TIME_MS = 1400;

    public FastUseCheck(AntiCheatConfig config) {
        super(CheckType.FAST_USE, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la durée de consommation à la réception du PlayerItemConsumeEvent.
     */
    public CheckResult checkConsume(Player player, PlayerData data) {
        long start = data.getConsumeStartTime();
        if (start == 0) {
            return CheckResult.pass(type);
        }
        data.setConsumeStartTime(0);

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed < MIN_CONSUME_TIME_MS) {
            String details = String.format("consumeTime=%dms min=%dms", elapsed, MIN_CONSUME_TIME_MS);
            return CheckResult.violation(type, 1.0, details);
        }

        return CheckResult.pass(type);
    }
}
