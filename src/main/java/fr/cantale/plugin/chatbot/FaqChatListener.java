package fr.cantale.plugin.chatbot;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.chatbot.faq.FaqCore;
import fr.cantale.plugin.chatbot.faq.FaqEntry;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Adaptateur Minecraft du bot FAQ.
 * Écoute le chat, détecte les questions et répond en privé.
 */
public class FaqChatListener implements Listener {

    private final CantalePlugin plugin;
    private final FaqCore faqCore;

    public FaqChatListener(CantalePlugin plugin, FaqCore faqCore) {
        this.plugin = plugin;
        this.faqCore = faqCore;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        FaqEntry entry = faqCore.match(message, player.getUniqueId().toString(), "minecraft");
        if (entry == null) return;

        String prefix = plugin.getConfigManager().getMessagePrefix();
        String response = ChatColor.translateAlternateColorCodes('&', prefix + entry.getResponse());

        // Réponse publique ou privée selon la config
        if (faqCore.getConfig().isReplyInPublic()) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                org.bukkit.Bukkit.broadcastMessage(response)
            );
        } else {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
        }
    }
}
