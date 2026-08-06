package fr.cantale.plugin.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatReactionListener implements Listener {

    private final ChatReactionManager manager;

    public ChatReactionListener(ChatReactionManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // Vérification sync côté main thread pour dépôt économie / broadcast
        String message = event.getMessage();
        org.bukkit.Bukkit.getScheduler().runTask(
                fr.cantale.plugin.CantalePlugin.getInstance(),
                () -> manager.tryWinScramble(event.getPlayer(), message)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.tryWinBlockBreak(event.getPlayer(), event.getBlock().getType());
    }
}
