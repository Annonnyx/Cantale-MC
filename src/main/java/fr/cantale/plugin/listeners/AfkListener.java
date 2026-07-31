package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkListener implements Listener {

    private final CantalePlugin plugin;
    private final Map<UUID, Long> lastActivity;

    public AfkListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.lastActivity = new ConcurrentHashMap<>();
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

    public long getAfkTimeoutMs(Player player) {
        PlayerRank rank = plugin.getRankManager().getRank(player);
        return switch (rank) {
            case CHEVRE, MODERATOR, ADMIN, OWNER -> -1L; // Pas de limite
            case VIP -> 60 * 60 * 1000L;                // 1 heure (60 min)
            case AVENTURIER -> 30 * 60 * 1000L;         // 30 minutes
            default -> 10 * 60 * 1000L;                 // 10 minutes par défaut
        };
    }

    public void checkAfkPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long timeoutMs = getAfkTimeoutMs(player);

            // Exempté d'anti-AFK
            if (timeoutMs <= 0) continue;

            long lastActive = lastActivity.getOrDefault(uuid, now);
            if (now - lastActive > timeoutMs) {
                long minutes = timeoutMs / (60 * 1000L);
                player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cVous avez été kick pour inactivité (" + minutes + " min)."));
            }
        }
    }
}
