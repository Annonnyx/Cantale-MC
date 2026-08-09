package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Point d'entrée Bukkit des interactions de caisses.
 * Délègue aux handlers distincts de {@link CrateInteractionHandler}
 * (Mythic = jet eau, Epic = rituel autel — chemins séparés).
 */
public class CrateListener implements Listener {

    private final CrateInteractionHandler interactions;

    public CrateListener(CantalePlugin plugin, CrateManager crateManager) {
        this.interactions = new CrateInteractionHandler(plugin, crateManager);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        interactions.handleInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        interactions.handleDrop(event);
    }
}
