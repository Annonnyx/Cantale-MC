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
 * Compte les clics de prise d'item dans un contenant vanilla sur une fenêtre
 * glissante. Un humain atteint difficilement {@value #ACTIONS_NEEDED} clics
 * utiles en {@value #WINDOW_MS} ms (~12 clics/s soutenus) ; un stealer le fait
 * systématiquement.
 *
 * Ne se déclenche que sur les contenants vanilla (coffre, barrel, shulker…) —
 * les GUIs Cantale et l'ender chest sont exclus (voir InventoryListener).
 */
public class ChestStealerCheck extends Check {

    /** Fenêtre glissante de comptage des clics (ms) */
    private static final int WINDOW_MS = 500;
    /** Nombre de prises dans la fenêtre pour flag */
    private static final int ACTIONS_NEEDED = 6;

    public ChestStealerCheck(AntiCheatConfig config) {
        super(CheckType.CHEST_STEALER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la cadence de vidage d'un contenant vanilla.
     */
    public CheckResult checkClick(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        int count = data.addChestClick(now, WINDOW_MS);

        if (count >= ACTIONS_NEEDED) {
            String details = String.format("clicks=%d window=%dms", count, WINDOW_MS);
            return CheckResult.violation(type, count, details);
        }

        return CheckResult.pass(type);
    }
}
