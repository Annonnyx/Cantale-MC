package fr.cantale.plugin.world;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Sanctuaire : pas de PvP, pas de faim, pas de grief joueur.
 * La warzone reste en PvP (drapeau monde). Les claims n'y protègent pas.
 */
public final class WorldRulesListener implements Listener {

    private final CantalePlugin plugin;

    public WorldRulesListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!WorldRules.isHub(event.getBlock().getWorld().getName())) {
            return;
        }
        if (isStaff(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "world.hub");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!WorldRules.isHub(event.getBlock().getWorld().getName())) {
            return;
        }
        if (isStaff(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "world.hub");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!WorldRules.isHub(victim.getWorld().getName())) {
            return;
        }
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null) {
            return;
        }
        event.setCancelled(true);
        if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
            plugin.messages().send(attacker, "world.hub-pvp");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!denyHubChange(event.getPlayer(), event.getBlock().getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!denyHubChange(event.getPlayer(), event.getBlock().getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!WorldRules.isHub(event.getBlock().getWorld().getName())) {
            return;
        }
        if (event.getIgnitingEntity() instanceof Player player && isStaff(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getIgnitingEntity() instanceof Player player) {
            plugin.messages().send(player, "world.hub");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity().getWorld() == null
                || !WorldRules.isHub(event.getEntity().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && isStaff(player)) {
            return;
        }
        event.setCancelled(true);
        if (player != null) {
            plugin.messages().send(player, "world.hub");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null || event.getEntity().getWorld() == null) {
            return;
        }
        if (!denyHubChange(event.getPlayer(), event.getEntity().getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity().getWorld() == null
                || !WorldRules.isHub(event.getEntity().getWorld().getName())) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player
                && isStaff(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!denyHubChange(player, event.getBlock().getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!WorldRules.isHub(player.getWorld().getName())) {
            return;
        }
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() != null
                && WorldRules.isHub(event.getLocation().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (WorldRules.isHub(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /** @return {@code true} si l'action du joueur dans le sanctuaire doit être annulée */
    private boolean denyHubChange(Player player, String worldName) {
        if (!WorldRules.isHub(worldName) || isStaff(player)) {
            return false;
        }
        plugin.messages().send(player, "world.hub");
        return true;
    }

    private boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission("cantale.admin")
                || plugin.getAdminBypassManager().isBypassing(player);
    }

    private static Player playerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
