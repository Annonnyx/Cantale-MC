package fr.cantale.plugin.combat;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTagListener implements Listener {

    private static final long SAFE_ZONE_MESSAGE_COOLDOWN_MS = 2000;

    private final CantalePlugin plugin;
    private final CombatTagManager combatTagManager;
    private final Map<UUID, Long> lastSafeZoneMessage = new HashMap<>();

    public CombatTagListener(CantalePlugin plugin, CombatTagManager combatTagManager) {
        this.plugin = plugin;
        this.combatTagManager = combatTagManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player directAttacker) {
            attacker = directAttacker;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player projectileAttacker) {
            attacker = projectileAttacker;
        }

        if (attacker == null || attacker.equals(victim)) return;

        // Ignorer si l'un des joueurs est en créatif ou spectateur
        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR ||
            victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        combatTagManager.tagCombat(attacker, victim);
    }

    /**
     * Interdit d'entrer dans une zone no-PvP (PASDIC / claim de sa fac) pendant le tag combat.
     * Sortie libre ; déplacement à l'intérieur d'une zone déjà occupée autorisé.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Même chunk : pas de changement de claim possible
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() >> 4 == to.getBlockX() >> 4
                && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) {
            return;
        }

        Player player = event.getPlayer();
        if (!combatTagManager.isInCombat(player)) return;

        boolean fromSafe = combatTagManager.isNoPvpZone(player, from);
        boolean toSafe = combatTagManager.isNoPvpZone(player, to);
        if (fromSafe || !toSafe) return;

        event.setCancelled(true);
        sendSafeZoneBlockedMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!combatTagManager.isInCombat(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location to = event.getTo();

        // Ender pearls / portails : autorisés en combat, sauf destination no-PvP
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
            cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
            cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (to != null && combatTagManager.isNoPvpZone(player, to)) {
                event.setCancelled(true);
                sendSafeZoneBlockedMessage(player);
            }
            return;
        }

        // Bloquer les autres téléportations (commandes /spawn, /home, /warp, etc.)
        event.setCancelled(true);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getMessagePrefix() + "&cImpossible de se téléporter pendant un combat ! ("
                        + combatTagManager.getRemainingSeconds(player.getUniqueId()) + "s restantes)"));

        // Annuler toute téléportation différée éventuelle dans TeleportDelayManager
        if (plugin.getTeleportDelayManager() != null) {
            plugin.getTeleportDelayManager().cancelPending(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combatTagManager.isInCombat(player)) {
            combatTagManager.handleCombatLog(player);
        }
        lastSafeZoneMessage.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        combatTagManager.handleOpponentDeath(victim);
    }

    private void sendSafeZoneBlockedMessage(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long last = lastSafeZoneMessage.get(uuid);
        if (last != null && now - last < SAFE_ZONE_MESSAGE_COOLDOWN_MS) return;
        lastSafeZoneMessage.put(uuid, now);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getMessagePrefix()
                        + "&cImpossible d'entrer en zone protégée (PASDIC / claim) pendant un combat ! ("
                        + combatTagManager.getRemainingSeconds(uuid) + "s restantes)"));
    }
}
