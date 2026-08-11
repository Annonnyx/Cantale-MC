package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Cycle de vie anti-cheat : cleanup quit + grâce post-téléportation.
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
        // Réaligner lastLocation sur la destination pour éviter un faux Speed au prochain move
        data.addPosition(event.getTo());
    }
}
