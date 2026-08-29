package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.Warp;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zone protégée de 16x16 chunks autour de chaque warp public actif :
 * aucune casse / pose / interaction (blocs, entités décoratives, seaux, explosions).
 * Message d'entrée / sortie calqué sur celui des claims.
 */
public class WarpProtectionListener implements Listener {

    private static final long ZONE_MESSAGE_COOLDOWN_MS = 2000;

    /** Entités « décoratives » inviolables en zone protégée (le PvP et les mobs restent libres). */
    private static final Set<EntityType> PROTECTED_ENTITIES = EnumSet.of(
            EntityType.ARMOR_STAND,
            EntityType.ITEM_FRAME,
            EntityType.GLOW_ITEM_FRAME,
            EntityType.PAINTING
    );

    private final CantalePlugin plugin;
    private final Map<UUID, Long> lastZoneMessage = new HashMap<>();

    public WarpProtectionListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBypass(Player player) {
        return player.isOp()
                || player.hasPermission("cantale.admin")
                || plugin.getAdminBypassManager().isBypassing(player);
    }

    private boolean isProtected(org.bukkit.Location location) {
        return plugin.getWarpManager() != null && plugin.getWarpManager().isProtectedZone(location);
    }

    private void deny(Player player) {
        player.sendMessage("§8[§6Cantale§8] §cZone protégée : impossible de modifier ou d'interagir autour d'un warp public.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || canBypass(player)) return;
        if (isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!PROTECTED_ENTITIES.contains(event.getEntityType())) return;
        if (!(event.getDamager() instanceof Player player) || canBypass(player)) return;
        if (isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    /** Message d'entrée / sortie de zone protégée, uniquement au changement de chunk. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if ((event.getFrom().getBlockX() >> 4) == (event.getTo().getBlockX() >> 4)
                && (event.getFrom().getBlockZ() >> 4) == (event.getTo().getBlockZ() >> 4)
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }

        Warp fromWarp = plugin.getWarpManager() == null ? null : plugin.getWarpManager().getProtectedWarpAt(event.getFrom());
        Warp toWarp = plugin.getWarpManager() == null ? null : plugin.getWarpManager().getProtectedWarpAt(event.getTo());
        if (fromWarp == null && toWarp == null) return;

        Player player = event.getPlayer();
        if (fromWarp == null) {
            sendZoneMessage(player, "§eVous entrez dans une §6zone protégée§e.");
        } else if (toWarp == null) {
            sendZoneMessage(player, "§eVous quittez la zone protégée.");
        }
    }

    private void sendZoneMessage(Player player, String message) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long last = lastZoneMessage.get(uuid);
        if (last != null && now - last < ZONE_MESSAGE_COOLDOWN_MS) return;
        lastZoneMessage.put(uuid, now);
        player.sendMessage(message);
    }
}
