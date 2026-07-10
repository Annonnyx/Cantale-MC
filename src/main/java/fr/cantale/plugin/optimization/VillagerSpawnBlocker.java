package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;

/**
 * Bloque tout spawn de villageois (Villager, WanderingTrader, ZombieVillager)
 * lorsque {@link ServerOptimizer#isVillagersDisabled()} est vrai.
 *
 * Annule également la transformation Zombie → ZombieVillager si le mode est actif.
 */
public class VillagerSpawnBlocker implements Listener {

    private final CantalePlugin plugin;

    public VillagerSpawnBlocker(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getServerOptimizer().isVillagersDisabled()) return;

        if (event.getEntity() instanceof Villager
                || event.getEntity() instanceof WanderingTrader
                || event.getEntity() instanceof ZombieVillager) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!plugin.getServerOptimizer().isVillagersDisabled()) return;

        for (org.bukkit.entity.Entity transformed : event.getTransformedEntities()) {
            if (transformed instanceof Villager
                    || transformed instanceof WanderingTrader
                    || transformed instanceof ZombieVillager) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
