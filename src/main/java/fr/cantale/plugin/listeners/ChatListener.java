package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.tag.TagManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final CantalePlugin plugin;
    private final TagManager tagManager;

    public ChatListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.tagManager = plugin.getTagManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Chat de Faction
        var factionMgr = plugin.getFactionManager();
        if (factionMgr != null && factionMgr.isFactionChatToggled(player.getUniqueId())) {
            var faction = factionMgr.getPlayerFaction(player.getUniqueId());
            if (faction != null) {
                event.setCancelled(true);
                factionMgr.sendFactionMessage(player, faction, event.getMessage());
                return;
            } else {
                factionMgr.setFactionChatToggled(player.getUniqueId(), false);
            }
        }
        
        // Logger le message de chat
        plugin.getActionLogger().logChatMessage(player.getName(), event.getMessage());
        
        // Construire le préfixe complet via TagManager
        // Inclut: [Faction] [Grade] [CustomTag] [Staff]
        String prefix = tagManager.getPlayerPrefix(player);

        String format = prefix + event.getFormat();
        event.setFormat(format);

        // Bridge vers Discord
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendMcMessage(player.getName(), event.getMessage());
        }
    }
}
