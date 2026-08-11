package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Écriture du fil {@code web_chat_messages} pour le site.
 * {@code factionId == null} → chat global ; sinon chat de faction.
 */
public final class WebChatLog {

    private static final int MESSAGE_MAX = 512;
    private static final int NAME_MAX = 64;

    private WebChatLog() {}

    public static void append(
            CantalePlugin plugin,
            String source,
            UUID playerUuid,
            String playerName,
            String message
    ) {
        append(plugin, source, playerUuid, playerName, message, null);
    }

    public static void append(
            CantalePlugin plugin,
            String source,
            UUID playerUuid,
            String playerName,
            String message,
            Integer factionId
    ) {
        if (plugin == null || message == null || message.isBlank()) return;
        String cleanSource = source == null || source.isBlank() ? "mc" : source.toLowerCase();
        String name = truncate(stripColors(playerName == null ? "Inconnu" : playerName), NAME_MAX);
        String text = truncate(stripColors(message).trim(), MESSAGE_MAX);
        if (text.isEmpty()) return;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO web_chat_messages (source, player_uuid, player_name, message, faction_id) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                if (conn == null) return;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, cleanSource);
                    if (playerUuid != null) {
                        ps.setString(2, playerUuid.toString());
                    } else {
                        ps.setNull(2, java.sql.Types.VARCHAR);
                    }
                    ps.setString(3, name);
                    ps.setString(4, text);
                    if (factionId != null && factionId > 0) {
                        ps.setInt(5, factionId);
                    } else {
                        ps.setNull(5, java.sql.Types.INTEGER);
                    }
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[Web] web_chat_messages insert impossible : " + e.getMessage());
            }
        });
    }

    private static String stripColors(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
