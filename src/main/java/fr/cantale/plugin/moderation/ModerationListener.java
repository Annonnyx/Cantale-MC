package fr.cantale.plugin.moderation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class ModerationListener implements Listener {

    private final MuteManager muteManager;
    private final VanishManager vanishManager;

    public ModerationListener(MuteManager muteManager, VanishManager vanishManager) {
        this.muteManager = muteManager;
        this.vanishManager = vanishManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (muteManager.isMuted(event.getPlayer().getUniqueId())) {
            Mute mute = muteManager.getMute(event.getPlayer().getUniqueId());
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cVous êtes mute : §f" + mute.getReason());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Cacher les joueurs vanished
        for (Player entry : event.getPlayer().getServer().getOnlinePlayers()) {
            if (vanishManager.isVanished(entry.getUniqueId())) {
                if (!event.getPlayer().hasPermission("cantale.admin") && !event.getPlayer().hasPermission("cantale.moderator")) {
                    event.getPlayer().hidePlayer(entry);
                }
            }
        }

        // Cacher le joueur s'il est vanish
        if (vanishManager.isVanished(event.getPlayer().getUniqueId())) {
            for (Player other : event.getPlayer().getServer().getOnlinePlayers()) {
                if (!other.hasPermission("cantale.admin") && !other.hasPermission("cantale.moderator")) {
                    other.hidePlayer(event.getPlayer());
                }
            }
        }
    }
}
