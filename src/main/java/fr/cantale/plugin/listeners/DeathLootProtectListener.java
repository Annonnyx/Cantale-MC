package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Protège le loot de mort : feu/lave et ramassage par les autres joueurs,
 * pendant {@link DeathLootProtection#DURATION_MS}. Même clé PDC, pas un second système.
 */
public class DeathLootProtectListener implements Listener {

    private static final double TAG_RADIUS = 3.5;

    private final CantalePlugin plugin;
    private final NamespacedKey deathLootKey;
    private final NamespacedKey ownerKey;

    public DeathLootProtectListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.deathLootKey = new NamespacedKey(plugin, DeathLootProtection.UNTIL_KEY);
        this.ownerKey = new NamespacedKey(plugin, DeathLootProtection.OWNER_KEY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        if (event.getDrops() == null || event.getDrops().isEmpty()) return;

        Location loc = event.getEntity().getLocation().clone();
        int expected = event.getDrops().size();
        long until = DeathLootProtection.until(System.currentTimeMillis());
        UUID owner = event.getEntity().getUniqueId();

        // Les Item entities spawnent après l'event — tag au tick suivant.
        Bukkit.getScheduler().runTask(plugin, () -> tagNearbyDeathItems(loc, expected, until, owner));
        // Filet : un 2e passage si le spawn est légèrement différé.
        Bukkit.getScheduler().runTaskLater(plugin, () -> tagNearbyDeathItems(loc, expected, until, owner), 5L);
    }

    private void tagNearbyDeathItems(Location loc, int expected, long untilMs, UUID owner) {
        if (loc.getWorld() == null) return;
        int tagged = 0;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, TAG_RADIUS, TAG_RADIUS, TAG_RADIUS)) {
            if (!(entity instanceof Item item) || item.isDead()) continue;
            if (item.getTicksLived() > 40) continue; // trop vieux pour être ce drop
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;

            item.getPersistentDataContainer().set(deathLootKey, PersistentDataType.LONG, untilMs);
            if (owner != null) {
                item.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
            }
            item.setInvulnerable(true);
            item.setFireTicks(0);
            tagged++;
            if (tagged >= expected + 8) break; // marge (tête Cantalame, etc.)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Item item = event.getItem();
        Long until = item.getPersistentDataContainer().get(deathLootKey, PersistentDataType.LONG);
        if (until == null) return;
        long now = System.currentTimeMillis();
        if (DeathLootProtection.expired(until, now)) {
            clearProtection(item);
            return;
        }
        UUID owner = readOwner(item);
        if (DeathLootProtection.blocksPickup(owner, player.getUniqueId(), until, now)) {
            event.setCancelled(true);
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
        if (DeathLootProtection.expired(until, System.currentTimeMillis())) {
            clearProtection(item);
            return false;
        }
        return true;
    }

    private UUID readOwner(Item item) {
        String raw = item.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void clearProtection(Item item) {
        item.getPersistentDataContainer().remove(deathLootKey);
        item.getPersistentDataContainer().remove(ownerKey);
        item.setInvulnerable(false);
    }
}
