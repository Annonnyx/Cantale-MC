package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfkListener implements Listener {

    private final CantalePlugin plugin;
    private final Map<UUID, Long> lastActivity;
    private static final long AFK_TIMEOUT_MS = 3600000; // 1 heure en millisecondes

    public AfkListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.lastActivity = new HashMap<>();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Même petit mouvement compte comme activité
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            updateActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    public void updateActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void checkAfkPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (lastActivity.containsKey(uuid)) {
                long lastActive = lastActivity.get(uuid);
                if (now - lastActive > AFK_TIMEOUT_MS) {
                    // Kick le joueur AFK
                    player.kickPlayer(ChatColor.translateAlternateColorCodes('&', 
                        "&cVous avez été kick pour inactivité (1h)."));
                }
            }
        }
    }
}
