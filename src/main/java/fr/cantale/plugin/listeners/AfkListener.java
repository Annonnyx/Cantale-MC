package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.moderation.VanishManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkListener implements Listener {

    /** Défauts Java si les clés config sont absentes (fichier live non fusionné). */
    private static final int DEFAULT_NONE = 600;
    private static final int DEFAULT_AVENTURIER = 900;
    private static final int DEFAULT_VIP = 1800;
    private static final int DEFAULT_CHEVRE = 3600;
    private static final int DEFAULT_MODERATOR = 3600;

    private final CantalePlugin plugin;
    private final Map<UUID, Long> lastActivity;

    public AfkListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.lastActivity = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            lastActivity.put(online.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
            event.getFrom().getYaw() != event.getTo().getYaw() ||
            event.getFrom().getPitch() != event.getTo().getPitch()) {
            updateActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    public void updateActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Timeout AFK en millisecondes, ou {@code -1} si le joueur est exempté.
     * Exemptés : permission {@code cantale.admin}, grades ADMIN et OWNER.
     * Les modos (MODERATOR) ont une limite configurable.
     */
    public long getAfkTimeoutMs(Player player) {
        if (player.hasPermission("cantale.admin")) {
            return -1L;
        }
        PlayerRank rank = plugin.getRankManager() != null
                ? plugin.getRankManager().getRank(player)
                : PlayerRank.NONE;
        if (rank == PlayerRank.ADMIN || rank == PlayerRank.OWNER) {
            return -1L;
        }
        int seconds = plugin.getConfig().getInt(
                "afk.timeout-seconds." + rank.name().toLowerCase(Locale.ROOT),
                defaultSeconds(rank));
        if (seconds <= 0) {
            return -1L;
        }
        return seconds * 1000L;
    }

    private static int defaultSeconds(PlayerRank rank) {
        return switch (rank) {
            case AVENTURIER -> DEFAULT_AVENTURIER;
            case VIP -> DEFAULT_VIP;
            case CHEVRE -> DEFAULT_CHEVRE;
            case MODERATOR -> DEFAULT_MODERATOR;
            case ADMIN, OWNER -> 0;
            default -> DEFAULT_NONE;
        };
    }

    public void checkAfkPlayers() {
        long now = System.currentTimeMillis();
        VanishManager vanish = plugin.getModerationCommand() != null
                ? plugin.getModerationCommand().getVanishManager()
                : null;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (vanish != null && vanish.isVanished(player.getUniqueId())) {
                continue;
            }

            UUID uuid = player.getUniqueId();
            long timeoutMs = getAfkTimeoutMs(player);
            if (timeoutMs <= 0) {
                continue;
            }

            Long lastActive = lastActivity.get(uuid);
            if (lastActive == null) {
                lastActivity.put(uuid, now);
                continue;
            }

            if (now - lastActive > timeoutMs) {
                long minutes = Math.max(1, timeoutMs / (60 * 1000L));
                player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cVous avez été kick pour inactivité (" + minutes + " min)."));
            }
        }
    }
}
