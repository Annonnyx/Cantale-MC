package fr.cantale.plugin.moderation;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        vanishManager.handleJoin(player);
        if (vanishManager.isVanished(player.getUniqueId())) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (vanishManager.isVanished(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }

    /**
     * Equipment / hotbar updates can re-send entity packets; re-hide so non-staff
     * never see floating held items or armor from a vanished player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        refreshIfVanished(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        refreshIfVanished(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        refreshIfVanished(event.getPlayer());
    }

    private void refreshIfVanished(Player player) {
        if (vanishManager.isVanished(player.getUniqueId())) {
            vanishManager.refreshHide(player);
        }
    }
}
