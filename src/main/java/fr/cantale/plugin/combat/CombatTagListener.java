package fr.cantale.plugin.combat;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CombatTagListener implements Listener {

    private final CantalePlugin plugin;
    private final CombatTagManager combatTagManager;

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!combatTagManager.isInCombat(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        // Les Ender Pearls et les Portails Nether/End sont explicitement autorisés en combat
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
            cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
            cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
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
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        combatTagManager.handleOpponentDeath(victim);
    }
}
