package fr.cantale.plugin.anticheat.inventory;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection d'AutoArmor.
 *
 * Compte les équipements d'armure dans une fenêtre glissante. Un humain shift-click
 * au mieux ~1 pièce par 150-200 ms ; un auto-armor équipe tout en 1-2 ticks.
 * Seuil : {@value #ACTIONS_NEEDED} équipements en moins de {@value #WINDOW_MS} ms.
 *
 * Ne se déclenche que sur les inventaires vanilla (voir InventoryListener) —
 * les GUIs Cantale sont exclues.
 */
public class AutoArmorCheck extends Check {

    /** Fenêtre glissante de comptage des équipements (ms) */
    private static final int WINDOW_MS = 500;
    /** Nombre d'équipements distincts dans la fenêtre pour flag */
    private static final int ACTIONS_NEEDED = 4;

    public AutoArmorCheck(AntiCheatConfig config) {
        super(CheckType.AUTO_ARMOR, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la cadence d'équipement d'armure.
     */
    public CheckResult checkEquip(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        int count = data.addArmorEquip(now, WINDOW_MS);

        if (count >= ACTIONS_NEEDED) {
            String details = String.format("equips=%d window=%dms", count, WINDOW_MS);
            return CheckResult.violation(type, count, details);
        }

        return CheckResult.pass(type);
    }
}
