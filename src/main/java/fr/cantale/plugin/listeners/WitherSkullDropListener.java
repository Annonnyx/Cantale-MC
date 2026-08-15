package fr.cantale.plugin.listeners;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Les têtes de wither skeleton ne droppent plus (raid = sink shop uniquement).
 * Les têtes déjà en inventaire / posées restent utilisables.
 */
public class WitherSkullDropListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.WITHER_SKELETON) return;
        event.getDrops().removeIf(WitherSkullDropListener::isWitherSkull);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (event.getEntityType() != EntityType.WITHER_SKELETON) return;
        ItemStack stack = event.getItemDrop().getItemStack();
        if (isWitherSkull(stack)) {
            event.setCancelled(true);
        }
    }

    private static boolean isWitherSkull(ItemStack stack) {
        return stack != null && stack.getType() == Material.WITHER_SKELETON_SKULL;
    }
}
