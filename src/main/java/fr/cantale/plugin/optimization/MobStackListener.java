package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.DyeColor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Dégâts létaux sur un stack : −1 + loot d'une mort (entité conservée).
 * Spawn : tentative de merge dans un stack proche.
 * <p>
 * Tonte / reproduction : comportement vanilla sur l'entité visible
 * (un mouton tondu = tout le stack apparaît tondu ; on stacke wool/sheared à part).
 */
public class MobStackListener implements Listener {

    private final CantalePlugin plugin;
    private final MobStackManager manager;

    public MobStackListener(CantalePlugin plugin, MobStackManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (manager.getStackSize(living) <= 1) return;

        if (living.getHealth() - event.getFinalDamage() > 0) return;

        Entity killer = resolveKiller(event);
        if (manager.tryAbsorbLethalHit(living, killer)) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    /**
     * Filet de sécurité : si la mort a lieu malgré l'absorb (autre plugin, setHealth…),
     * loot vanilla d'une mort + respawn du reste du stack.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!manager.isEnabled()) return;
        LivingEntity dead = event.getEntity();
        int size = manager.getStackSize(dead);
        if (size <= 1) return;

        // Snapshot immédiat (l'entité sera invalide au tick suivant)
        final int remaining = size - 1;
        final var type = dead.getType();
        final var loc = dead.getLocation().clone();
        final DyeColor sheepColor;
        final boolean sheepSheared;
        final Rabbit.Type rabbitType;
        final MushroomCow.Variant mooshVariant;
        final boolean adult;
        if (dead instanceof Sheep sheep) {
            sheepColor = sheep.getColor();
            sheepSheared = sheep.isSheared();
        } else {
            sheepColor = null;
            sheepSheared = false;
        }
        if (dead instanceof Rabbit rabbit) {
            rabbitType = rabbit.getRabbitType();
        } else {
            rabbitType = null;
        }
        if (dead instanceof MushroomCow moosh) {
            mooshVariant = moosh.getVariant();
        } else {
            mooshVariant = null;
        }
        adult = !(dead instanceof Ageable ageable) || ageable.isAdult();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (loc.getWorld() == null) return;
                LivingEntity replacement = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
                if (replacement instanceof Sheep sheep && sheepColor != null) {
                    sheep.setColor(sheepColor);
                    sheep.setSheared(sheepSheared);
                }
                if (replacement instanceof Rabbit rabbit && rabbitType != null) {
                    rabbit.setRabbitType(rabbitType);
                }
                if (replacement instanceof MushroomCow moosh && mooshVariant != null) {
                    moosh.setVariant(mooshVariant);
                }
                if (replacement instanceof Ageable ageable) {
                    if (adult) ageable.setAdult();
                    else ageable.setBaby();
                }
                manager.setStackSize(replacement, remaining);
                manager.markSkipMerge(replacement, 60);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (living.isValid() && !living.isDead()) {
                    manager.tryMergeNearby(living);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private static Entity resolveKiller(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent by)) return null;
        Entity damager = by.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) return entity;
        }
        return damager;
    }
}
