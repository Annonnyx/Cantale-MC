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
 * Vérifie si le joueur s'équipe d'armure trop rapidement (moins de X ticks
 * entre chaque pièce). Un joueur normal met au moins 2-3 ticks entre
 * chaque shift-click pour équiper une pièce.
 */
public class AutoArmorCheck extends Check {

    public AutoArmorCheck(AntiCheatConfig config) {
        super(CheckType.AUTO_ARMOR, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    /**
     * Vérifie la vitesse d'équipement d'armure.
     *
     * @param player le joueur
     * @param data   les données du joueur
     * @return le résultat du check
     */
    public CheckResult checkEquip(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        long lastEquip = data.getLastArmorEquip();
        data.setLastArmorEquip(now);

        if (lastEquip == 0) {
            return CheckResult.pass(type);
        }

        long elapsed = now - lastEquip;
        long minTime = (long) config.getMinEquipTicks() * 50; // ticks → ms

        if (elapsed < minTime) {
            String details = String.format("elapsed=%dms min=%dms", elapsed, minTime);
            return CheckResult.violation(type, 1.0, details);
        }

        return CheckResult.pass(type);
    }
}
