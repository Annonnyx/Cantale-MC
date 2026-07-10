package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ModerationTraceListener implements Listener {

    private final CantalePlugin plugin;
    private final ModerationTraceManager traceManager;

    public ModerationTraceListener(CantalePlugin plugin, ModerationTraceManager traceManager) {
        this.plugin = plugin;
        this.traceManager = traceManager;
        
        // Tâche pour logger l'inventaire toutes les 10 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                for (var player : plugin.getServer().getOnlinePlayers()) {
                    if (traceManager.isInTrace(player.getUniqueId())) {
                        traceManager.logInventory(player.getUniqueId(), player.getInventory().getContents());
                    }
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // 10 secondes = 200 ticks

        // Tâche pour logger la position toutes les 30 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                for (var player : plugin.getServer().getOnlinePlayers()) {
                    if (traceManager.isInTrace(player.getUniqueId())) {
                        traceManager.logPosition(player.getUniqueId(), player.getLocation());
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // 30 secondes = 600 ticks
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (traceManager.isInTrace(event.getPlayer().getUniqueId())) {
            traceManager.logAction(event.getPlayer().getUniqueId(),
                "BREAK: " + event.getBlock().getType().name() + " at " + formatLocation(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (traceManager.isInTrace(event.getPlayer().getUniqueId())) {
            traceManager.logAction(event.getPlayer().getUniqueId(),
                "PLACE: " + event.getBlock().getType().name() + " at " + formatLocation(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (traceManager.isInTrace(event.getPlayer().getUniqueId())) {
            traceManager.logAction(event.getPlayer().getUniqueId(), "COMMAND: " + event.getMessage());
        }
    }

    private String formatLocation(org.bukkit.Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
