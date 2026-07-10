package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * ChatPrefixListener — désactivé.
 * La logique des préfixes (Faction, Grade, Tag Custom, Staff) est maintenant
 * gérée entièrement par TagManager via ChatListener.
 */
public class ChatPrefixListener implements Listener {

    public ChatPrefixListener(CantalePlugin plugin) {
        // Conservé pour compatibilité (enregistré dans CantalePlugin#registerListeners)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // No-op : les préfixes sont maintenant gérés par ChatListener + TagManager
    }
}
