package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Protège brièvement le loot de mort contre le feu / la lave (fréquents au Nether)
 * sans toucher à {@code keepInventory} ni au clearlag (âge déjà géré ailleurs).
 */
public class DeathLootProtectListener implements Listener {

    /** Durée de grâce anti-feu/lave (secondes). */
    private static final int FIRE_PROTECT_SECONDS = 90;
    private static final double TAG_RADIUS = 3.5;

    private final CantalePlugin plugin;
    private final NamespacedKey deathLootKey;

    public DeathLootProtectListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.deathLootKey = new NamespacedKey(plugin, "death_loot_until");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        if (event.getDrops() == null || event.getDrops().isEmpty()) return;

        Location loc = event.getEntity().getLocation().clone();
        int expected = event.getDrops().size();
        long until = System.currentTimeMillis() + FIRE_PROTECT_SECONDS * 1000L;

        // Les Item entities spawnent après l'event — tag au tick suivant.
        Bukkit.getScheduler().runTask(plugin, () -> tagNearbyDeathItems(loc, expected, until));
        // Filet : un 2e passage si le spawn est légèrement différé.
        Bukkit.getScheduler().runTaskLater(plugin, () -> tagNearbyDeathItems(loc, expected, until), 5L);
    }

    private void tagNearbyDeathItems(Location loc, int expected, long untilMs) {
        if (loc.getWorld() == null) return;
        int tagged = 0;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, TAG_RADIUS, TAG_RADIUS, TAG_RADIUS)) {
            if (!(entity instanceof Item item) || item.isDead()) continue;
            if (item.getTicksLived() > 40) continue; // trop vieux pour être ce drop
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;

            item.getPersistentDataContainer().set(deathLootKey, PersistentDataType.LONG, untilMs);
            item.setInvulnerable(true);
            item.setFireTicks(0);
            tagged++;
            if (tagged >= expected + 8) break; // marge (tête Cantalame, etc.)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (isProtectedDeathLoot(item)) {
            event.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (!isProtectedDeathLoot(item)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    private boolean isProtectedDeathLoot(Item item) {
        Long until = item.getPersistentDataContainer().get(deathLootKey, PersistentDataType.LONG);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            item.getPersistentDataContainer().remove(deathLootKey);
            item.setInvulnerable(false);
            return false;
        }
        return true;
    }
}
