package fr.cantale.plugin.map;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MapIntegrationListener implements Listener {

    private final CantalePlugin plugin;
    private final MapIntegrationManager mapManager;

    public MapIntegrationListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.mapManager = new MapIntegrationManager(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Attendre un peu que le client soit prêt
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            mapManager.sendWaypointsToPlayer(player);
            mapManager.sendClaimsToPlayer(player);
        }, 40L); // 2 secondes
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nettoyer si nécessaire
    }
}
