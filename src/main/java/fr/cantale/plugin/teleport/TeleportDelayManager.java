package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportDelayManager implements Listener {

    private final CantalePlugin plugin;
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> initialLocations = new HashMap<>();

    public TeleportDelayManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public int getTeleportDelay(Player player) {
        if (player.hasPermission("cantale.admin")) {
            return 0;
        }
        if (player.hasPermission("cantale.moderator")) {
            var modCmd = plugin.getModerationCommand();
            if (modCmd != null && modCmd.getTraceManager().isInTrace(player.getUniqueId())) {
                return 0;
            }
        }

        PlayerRank rank = plugin.getRankManager().getRank(player);
        if (rank == PlayerRank.CHEVRE || rank == PlayerRank.VIP || rank == PlayerRank.MODERATOR || rank == PlayerRank.ADMIN || rank == PlayerRank.OWNER) {
            return 0;
        }
        return 3;
    }

    public void delayTeleport(Player player, Runnable teleportAction) {
        if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(player)) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de se téléporter pendant un combat ! (" 
                    + plugin.getCombatTagManager().getRemainingSeconds(player.getUniqueId()) + "s restantes)");
            return;
        }

        int delay = getTeleportDelay(player);
        if (delay <= 0) {
            teleportAction.run();
            return;
        }

        cancelPending(player);

        player.sendMessage("§8[§6Cantale§8] §7Téléportation dans §e" + delay + " §7secondes. Ne bougez pas et ne subissez pas de dégâts.");

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    cleanup(player.getUniqueId());
                    return;
                }

                if (remaining <= 0) {
                    teleportAction.run();
                    cancel();
                    cleanup(player.getUniqueId());
                    return;
                }

                // Show action bar or subtitle for a premium feel
                player.sendTitle("", "§7Téléportation dans §e" + remaining + "§7s...", 0, 25, 5);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), task);
        initialLocations.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
    }

    public void cancelPending(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            pendingTeleports.get(uuid).cancel();
            cleanup(uuid);
        }
    }

    private void cleanup(UUID uuid) {
        pendingTeleports.remove(uuid);
        initialLocations.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingTeleports.containsKey(uuid)) {
            Location initial = initialLocations.get(uuid);
            if (initial != null) {
                Location current = player.getLocation().getBlock().getLocation();
                // Check if they changed block coordinates (x, y, or z)
                if (initial.getBlockX() != current.getBlockX() ||
                    initial.getBlockY() != current.getBlockY() ||
                    initial.getBlockZ() != current.getBlockZ()) {
                    
                    cancelPending(player);
                    player.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : vous avez bougé.");
                    player.sendTitle("§cTéléportation annulée", "§7Vous avez bougé", 5, 30, 5);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (pendingTeleports.containsKey(uuid)) {
                cancelPending(player);
                player.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : vous avez subi des dégâts.");
                player.sendTitle("§cTéléportation annulée", "§7Dégâts subis", 5, 30, 5);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer());
    }
}
