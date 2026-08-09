package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Log le statut du resource pack côté client (utile pour diagnostiquer
 * failed download / declined / success).
 */
public class ResourcePackStatusListener implements Listener {

    private final CantalePlugin plugin;

    public ResourcePackStatusListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        plugin.getLogger().info("[ResourcePack] " + event.getPlayer().getName()
                + " → " + event.getStatus());
    }
}
