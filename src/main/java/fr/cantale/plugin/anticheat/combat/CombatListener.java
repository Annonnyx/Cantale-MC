package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Listener principal pour les checks de combat.
 *
 * Écoute les attaques (EntityDamageByEntityEvent) pour le reach et le killaura.
 * Écoute les clics (PlayerInteractEvent LEFT_CLICK) pour l'autoclicker.
 */
public class CombatListener implements Listener {

    private final AntiCheatManager manager;

    public CombatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Ignorer les modes créatif et spectateur
        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifier le bypass
        if (manager.shouldBypass(attacker)) {
            return;
        }

        if (!manager.getConfig().isEnabled()) {
            return;
        }

        PlayerData data = manager.getPlayerData(attacker.getUniqueId());
        Entity target = event.getEntity();

        // ── Reach Check ──
        Check reachCheck = manager.getCheckRegistry().get(CheckType.REACH);
        if (reachCheck != null && reachCheck.isEnabled() && reachCheck instanceof ReachCheck reach) {
            CheckResult result = reach.checkReach(attacker, target);
            if (result.isViolated()) {
                manager.handleViolation(attacker, result, reachCheck);
            }
        }

        // ── KillAura Check ──
        if (target instanceof LivingEntity) {
            Check killAuraCheck = manager.getCheckRegistry().get(CheckType.KILLAURA);
            if (killAuraCheck != null && killAuraCheck.isEnabled() && killAuraCheck instanceof KillAuraCheck killAura) {
                CheckResult result = killAura.checkTargetSwitch(attacker, target.getUniqueId(), data);
                if (result.isViolated()) {
                    manager.handleViolation(attacker, result, killAuraCheck);
                }
            }
        }

        // ── Aim Check ──
        Check aimCheck = manager.getCheckRegistry().get(CheckType.AIM);
        if (aimCheck != null && aimCheck.isEnabled()) {
            CheckResult result = aimCheck.check(attacker, data);
            if (result.isViolated()) {
                manager.handleViolation(attacker, result, aimCheck);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Seulement les clics gauche (attaque)
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (manager.shouldBypass(player)) {
            return;
        }

        if (!manager.getConfig().isEnabled()) {
            return;
        }

        // ── AutoClicker Check ──
        Check autoClickerCheck = manager.getCheckRegistry().get(CheckType.AUTOCLICKER);
        if (autoClickerCheck != null && autoClickerCheck.isEnabled() && autoClickerCheck instanceof AutoClickerCheck ac) {
            PlayerData data = manager.getPlayerData(player.getUniqueId());
            CheckResult result = ac.checkClick(player, data);
            if (result.isViolated()) {
                manager.handleViolation(player, result, autoClickerCheck);
            }
        }
    }
}
