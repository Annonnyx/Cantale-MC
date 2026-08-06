package fr.cantale.plugin.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class FactionEventListener implements Listener {

    private final FactionEventManager manager;

    public FactionEventListener(FactionEventManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.onOreMined(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            manager.onFactionKill(killer, victim);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isActive()) return;
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                fr.cantale.plugin.CantalePlugin.getInstance(),
                () -> {
                    if (manager.isActive() && event.getPlayer().isOnline()) {
                        manager.showScoreboard(event.getPlayer());
                    }
                },
                40L
        );
    }
}
