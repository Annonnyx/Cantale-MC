package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * Cycle de vie anti-cheat : cleanup quit + grâce post-téléportation +
 * exemptions vélocité (knockback/explosion) + validation des dégâts de chute (NoFall).
 */
public class AntiCheatListener implements Listener {

    private static final long TELEPORT_GRACE_MS = 3000L;

    private final AntiCheatManager manager;

    public AntiCheatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        PlayerData data = manager.getPlayerData(event.getPlayer().getUniqueId());
        data.exemptMovement(TELEPORT_GRACE_MS);
        data.resetSpeedBuffer();
        data.resetAimBuffer();
        data.resetReachBuffer();
        data.setAirTicks(0);
        // Reset du tracking de chute : une TP en l'air ne doit pas déclencher NoFall
        data.setFalling(false);
        data.clearFallExpectation();
        // Réaligner lastLocation sur la destination pour éviter un faux Speed au prochain move
        data.addPosition(event.getTo());
    }

    /**
     * Réception du dégât de chute attendu → le joueur est légitime (annule l'attente NoFall).
     * Tout autre dégât subi déclenche une exemption vélocité (knockback).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = manager.getPlayerData(player.getUniqueId());

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && data.isExpectingFallDamage()) {
            data.clearFallExpectation();
            return;
        }

        // Knockback de combat / explosion / wind charge : vélocité non fiable pendant un court délai
        data.exemptVelocity(manager.getConfig().getVelocityExemptMs());
    }

    /**
     * Tout changement de vélocité côté serveur (knockback, explosion, riptide, canne à pêche…)
     * ouvre une courte fenêtre d'exemption pour les checks sensibles à la vélocité.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        PlayerData data = manager.getPlayerData(event.getPlayer().getUniqueId());
        data.exemptVelocity(manager.getConfig().getVelocityExemptMs());
    }
}
