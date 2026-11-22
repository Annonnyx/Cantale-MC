package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.world.WorldTransit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Point unique pour toute téléportation joueur :
 * combat-tag, cooldown global 30 s, cooldown RTP 5 min, warmup 3 s immobile
 * avec préchargement du chunk, puis TP et pose des cooldowns.
 * Exemptés : {@code cantale.admin} et mode trace ({@code /moderation}).
 * VIP / Chèvre ne sont pas exemptés.
 */
public class TeleportDelayManager implements Listener {

    public static final int WARMUP_SECONDS = 3;
    public static final long GLOBAL_COOLDOWN_MS = 30_000L;
    public static final long RTP_COOLDOWN_MS = 5 * 60_000L;

    private static final String PREFIX = "§8[§6Cantale§8] ";

    private final CantalePlugin plugin;
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingDestinations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTeleportMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRtpMs = new ConcurrentHashMap<>();

    public TeleportDelayManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Admin, ou staff en mode trace ({@code /moderation}). Vanish seul ne suffit pas.
     */
    public boolean isTeleportExempt(Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("cantale.admin")) {
            return true;
        }
        var modCmd = plugin.getModerationCommand();
        return modCmd != null
                && modCmd.getTraceManager() != null
                && modCmd.getTraceManager().isInTrace(player.getUniqueId());
    }

    public int getTeleportDelay(Player player) {
        return isTeleportExempt(player) ? 0 : WARMUP_SECONDS;
    }

    /**
     * Combat-tag et cooldowns. Envoie le message FR si bloqué.
     *
     * @param rtp si true, attend aussi le cooldown RTP 5 min (max avec le global 30 s)
     */
    public boolean checkTeleportAllowed(Player player, boolean rtp) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(player)) {
            player.sendMessage(PREFIX + "§cImpossible de se téléporter pendant un combat ! ("
                    + plugin.getCombatTagManager().getRemainingSeconds(player.getUniqueId()) + "s restantes)");
            return false;
        }
        if (isTeleportExempt(player)) {
            return true;
        }
        long globalLeft = remainingMs(lastTeleportMs, player.getUniqueId(), GLOBAL_COOLDOWN_MS);
        long rtpLeft = rtp ? remainingMs(lastRtpMs, player.getUniqueId(), RTP_COOLDOWN_MS) : 0L;
        long waitMs = Math.max(globalLeft, rtpLeft);
        if (waitMs > 0) {
            sendCooldownMessage(player, waitMs, rtpLeft >= globalLeft && rtpLeft > 0);
            return false;
        }
        return true;
    }

    /**
     * Téléporte {@code player} vers {@code destination} après les checks / warmup.
     *
     * @param onSuccess message (ou logique extra) après un TP réussi ; peut être null
     * @return false si bloqué avant de démarrer
     */
    public boolean requestTeleport(Player player, Location destination, boolean rtp, Runnable onSuccess) {
        return requestTeleport(player, destination, rtp, false, onSuccess);
    }

    /**
     * @param explicitWorldEntry {@code true} si le joueur a choisi cette destination
     *                           (warp, home, tpa, spawn de faction). {@code /spawn} et {@code /rtp}
     *                           restent à {@code false} : ils n'entrent pas en warzone tout seuls.
     */
    public boolean requestTeleport(Player player, Location destination, boolean rtp,
                                   boolean explicitWorldEntry, Runnable onSuccess) {
        Location dest = destination != null ? destination.clone() : null;
        return requestTeleport(player, dest, rtp, explicitWorldEntry, () -> {
            if (!player.isOnline()) {
                return false;
            }
            if (dest == null || dest.getWorld() == null) {
                player.sendMessage(PREFIX + "§cTéléportation annulée : destination introuvable.");
                return false;
            }
            player.teleport(dest);
            if (onSuccess != null) {
                onSuccess.run();
            }
            return true;
        });
    }

    /**
     * Warmup + preload, puis exécute {@code teleportAction}.
     * Les cooldowns ne sont posés que si l'action retourne {@code true}.
     */
    public boolean requestTeleport(Player player, Location destination, boolean rtp, BooleanSupplier teleportAction) {
        return requestTeleport(player, destination, rtp, false, teleportAction);
    }

    public boolean requestTeleport(Player player, Location destination, boolean rtp,
                                   boolean explicitWorldEntry, BooleanSupplier teleportAction) {
        if (player == null || !player.isOnline() || teleportAction == null) {
            return false;
        }
        if (!warzoneEntryAllowed(player, destination, explicitWorldEntry)) {
            player.sendMessage(PREFIX + "§cCette téléportation n'entre pas en terres de guerre sans destination choisie.");
            return false;
        }
        if (!checkTeleportAllowed(player, rtp)) {
            return false;
        }

        Location dest = destination != null ? destination.clone() : null;
        Runnable complete = () -> finishTeleport(player, dest, rtp, explicitWorldEntry, teleportAction);

        if (getTeleportDelay(player) <= 0) {
            complete.run();
            return true;
        }

        cancelPending(player);
        preloadDestination(dest);

        player.sendMessage(PREFIX + "§7Téléportation dans §e" + WARMUP_SECONDS
                + " §7secondes. Ne bougez pas et ne subissez pas de dégâts.");

        BukkitTask task = new BukkitRunnable() {
            int remaining = WARMUP_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    cleanup(player.getUniqueId());
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    cleanup(player.getUniqueId());
                    complete.run();
                    return;
                }

                if (remaining == 1) {
                    preloadDestination(dest);
                }

                player.sendTitle("", "§7Téléportation dans §e" + remaining + "§7s...", 0, 25, 5);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        UUID uuid = player.getUniqueId();
        pendingTeleports.put(uuid, task);
        initialLocations.put(uuid, player.getLocation().getBlock().getLocation());
        if (dest != null) {
            pendingDestinations.put(uuid, dest);
        }
        return true;
    }

    /**
     * Ancien point d'entrée : warmup + combat, sans destination connue (pas de preload ciblé).
     * Préférer {@link #requestTeleport}.
     */
    public void delayTeleport(Player player, Runnable teleportAction) {
        requestTeleport(player, null, false, () -> {
            teleportAction.run();
            return true;
        });
    }

    public void cancelPending(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = pendingTeleports.get(uuid);
        if (task != null) {
            task.cancel();
            cleanup(uuid);
        }
    }

    public boolean hasPending(Player player) {
        return player != null && pendingTeleports.containsKey(player.getUniqueId());
    }

    private boolean warzoneEntryAllowed(Player player, Location destination, boolean explicitWorldEntry) {
        if (destination == null || destination.getWorld() == null || player.getWorld() == null) {
            return true;
        }
        return WorldTransit.allows(player.getWorld().getName(), destination.getWorld().getName(), explicitWorldEntry);
    }

    private void finishTeleport(Player player, Location destination, boolean rtp,
                                boolean explicitWorldEntry, BooleanSupplier teleportAction) {
        if (!player.isOnline()) {
            return;
        }
        if (!warzoneEntryAllowed(player, destination, explicitWorldEntry)) {
            player.sendMessage(PREFIX + "§cCette téléportation n'entre pas en terres de guerre sans destination choisie.");
            return;
        }
        if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(player)) {
            player.sendMessage(PREFIX + "§cTéléportation annulée : combat.");
            return;
        }

        Runnable runAction = () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean success = teleportAction.getAsBoolean();
            if (success && !isTeleportExempt(player)) {
                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();
                lastTeleportMs.put(uuid, now);
                if (rtp) {
                    lastRtpMs.put(uuid, now);
                }
            }
        };

        if (destination == null || destination.getWorld() == null) {
            runAction.run();
            return;
        }

        World world = destination.getWorld();
        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            runAction.run();
            return;
        }
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) ->
                plugin.getServer().getScheduler().runTask(plugin, runAction));
    }

    private void preloadDestination(Location dest) {
        if (dest == null || dest.getWorld() == null) {
            return;
        }
        if (plugin.getChunkLoader() != null) {
            plugin.getChunkLoader().preloadChunksAround(dest);
        }
        dest.getWorld().getChunkAtAsync(dest.getBlockX() >> 4, dest.getBlockZ() >> 4, true);
    }

    private void sendCooldownMessage(Player player, long waitMs, boolean rtpCooldown) {
        String wait = formatDuration(waitMs);
        if (rtpCooldown) {
            player.sendMessage(PREFIX + "§cRTP en cooldown. Attendez encore §e" + wait + "§c.");
        } else {
            player.sendMessage(PREFIX + "§cTéléportation en cooldown. Attendez encore §e" + wait + "§c.");
        }
    }

    static String formatDuration(long millis) {
        long totalSec = Math.max(1L, (millis + 999L) / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        if (min > 0) {
            if (sec == 0) {
                return min + " min";
            }
            return min + " min " + sec + "s";
        }
        return totalSec + "s";
    }

    private static long remainingMs(Map<UUID, Long> map, UUID uuid, long durationMs) {
        Long last = map.get(uuid);
        if (last == null) {
            return 0L;
        }
        return Math.max(0L, durationMs - (System.currentTimeMillis() - last));
    }

    private void cleanup(UUID uuid) {
        pendingTeleports.remove(uuid);
        initialLocations.remove(uuid);
        pendingDestinations.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingTeleports.containsKey(uuid)) {
            return;
        }
        Location initial = initialLocations.get(uuid);
        if (initial == null) {
            return;
        }
        Location current = player.getLocation().getBlock().getLocation();
        if (initial.getBlockX() != current.getBlockX()
                || initial.getBlockY() != current.getBlockY()
                || initial.getBlockZ() != current.getBlockZ()) {
            cancelPending(player);
            player.sendMessage(PREFIX + "§cTéléportation annulée : vous avez bougé.");
            player.sendTitle("§cTéléportation annulée", "§7Vous avez bougé", 5, 30, 5);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!pendingTeleports.containsKey(player.getUniqueId())) {
            return;
        }
        cancelPending(player);
        player.sendMessage(PREFIX + "§cTéléportation annulée : vous avez subi des dégâts.");
        player.sendTitle("§cTéléportation annulée", "§7Dégâts subis", 5, 30, 5);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer());
    }
}
