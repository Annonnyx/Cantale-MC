package fr.cantale.plugin.lang;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageService implements Listener {

    private final CantalePlugin plugin;
    private final MessageCatalog catalog;
    private final ConcurrentHashMap<UUID, String> languages = new ConcurrentHashMap<>();

    public MessageService(CantalePlugin plugin, MessageCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public static MessageService load(CantalePlugin plugin) throws IOException {
        try (InputStream french = plugin.getResource("messages_fr.yml");
             InputStream english = plugin.getResource("messages_en.yml")) {
            if (french == null || english == null) {
                throw new IOException("messages_fr.yml ou messages_en.yml absent du jar");
            }
            return new MessageService(plugin, MessageCatalog.load(french, english));
        }
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    public void remember(UUID uuid, String lang) {
        if (uuid == null) {
            return;
        }
        languages.put(uuid, LanguageCodes.orFrench(lang));
    }

    public String languageOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return languages.getOrDefault(player.getUniqueId(), LanguageCodes.FR);
        }
        return LanguageCodes.FR;
    }

    public String raw(CommandSender sender, String key, Object... args) {
        return catalog.format(languageOf(sender), key, args);
    }

    public String raw(String lang, String key, Object... args) {
        return catalog.format(lang, key, args);
    }

    public void send(CommandSender sender, String key, Object... args) {
        if (sender == null) {
            return;
        }
        String lang = languageOf(sender);
        String text = catalog.format(lang, "prefix") + catalog.format(lang, key, args);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }

    public void sendEncoded(CommandSender sender, String encoded) {
        if (sender == null || encoded == null || encoded.isBlank()) {
            return;
        }
        String[] parts = encoded.split("\u001f", -1);
        Object[] args = new Object[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        send(sender, parts[0], args);
    }

    public Component component(CommandSender sender, String key, Object... args) {
        String colored = ChatColor.translateAlternateColorCodes('&', raw(sender, key, args));
        return LegacyComponentSerializer.legacySection().deserialize(colored);
    }

    public void broadcast(String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, key, args);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String lang = LanguageCodes.FR;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                lang = PlayerLanguageRepository.read(conn, uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("[i18n] Langue illisible pour " + uuid + " : " + e.getMessage());
            }
            String resolved = lang;
            Bukkit.getScheduler().runTask(plugin, () -> remember(uuid, resolved));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        languages.remove(event.getPlayer().getUniqueId());
    }
}
