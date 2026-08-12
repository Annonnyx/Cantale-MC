package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import fr.cantale.plugin.anticheat.util.InstantBreakUtil;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Listener principal pour les checks de combat.
 *
 * Écoute les attaques (EntityDamageByEntityEvent) pour reach / killaura / aim / CPS.
 * Écoute les clics air (LEFT_CLICK_AIR) pour l'autoclicker.
 * Les LEFT_CLICK_BLOCK sur blocs soft/instant sont ignorés (spam légitime herbe/fleurs).
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

        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

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
            CheckResult result = reach.checkReach(attacker, target, data);
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

        // ── AutoClicker : un coup porté compte comme un clic combat ──
        runAutoClicker(attacker, data);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
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

        // Casse / dig : ne pas traiter comme CPS PvP
        if (action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            // Soft/instant (herbe, fleurs, neige…) → jamais d'AutoClicker
            if (clicked != null && InstantBreakUtil.isSoftOrInstant(player, clicked)) {
                return;
            }
            // Dig sur bloc solide → pas de CPS combat non plus (FastBreak gère le nuker)
            return;
        }

        PlayerData data = manager.getPlayerData(player.getUniqueId());
        runAutoClicker(player, data);
    }

    private void runAutoClicker(Player player, PlayerData data) {
        Check autoClickerCheck = manager.getCheckRegistry().get(CheckType.AUTOCLICKER);
        if (autoClickerCheck != null && autoClickerCheck.isEnabled() && autoClickerCheck instanceof AutoClickerCheck ac) {
            CheckResult result = ac.checkClick(player, data);
            if (result.isViolated()) {
                manager.handleViolation(player, result, autoClickerCheck);
            }
        }
    }
}
