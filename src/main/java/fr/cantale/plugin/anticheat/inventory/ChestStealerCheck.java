package fr.cantale.plugin.anticheat.inventory;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection de ChestStealer.
 *
 * Vérifie si le joueur vide un coffre trop rapidement (clics trop fréquents).
 * Un joueur normal met au minimum 1-2 ticks entre chaque clic dans un coffre.
 */
public class ChestStealerCheck extends Check {

    public ChestStealerCheck(AntiCheatConfig config) {
        super(CheckType.CHEST_STEALER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la vitesse de clic dans un coffre.
     *
     * @param player le joueur
     * @param data   les données du joueur
     * @return le résultat du check
     */
    public CheckResult checkClick(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        long lastClick = data.getLastInventoryClick();
        data.setLastInventoryClick(now);

        if (lastClick == 0) {
            return CheckResult.pass(type);
        }

        long elapsed = now - lastClick;
        long minTime = (long) config.getMinClickTicks() * 50; // ticks → ms

        if (elapsed < minTime) {
            String details = String.format("elapsed=%dms min=%dms", elapsed, minTime);
            return CheckResult.violation(type, 1.0, details);
        }

        return CheckResult.pass(type);
    }
}
