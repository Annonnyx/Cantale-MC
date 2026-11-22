package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cache.RedisChannels;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Relaye le chat site → jeu depuis Redis {@code cantale:chat:web2mc}.
 * La table {@code web_chat_outbox} reste l'archive (UPDATE de statut), sans SELECT périodique.
 */
public class WebChatBridge {

    private static final int RESULT_MAX = 255;

    private final CantalePlugin plugin;

    public WebChatBridge(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getLogger().info("[Web] WebChatBridge branché sur Redis "
                + RedisChannels.CHAT_WEB_TO_MC + " (polling SQL désactivé, archivage conservé).");
    }

    public void stop() {
        // L'abonnement est fermé par RedisCache.shutdown().
    }

    /**
     * Appelé sur le thread Pub/Sub. Le broadcast Bukkit est renvoyé sur le thread principal.
     */
    public void onRedisMessage(String body) {
        RedisBridgeMessages.Chat chat = RedisBridgeMessages.parseChat(body);
        if (chat == null) {
            plugin.getLogger().warning("[Web] Chat Redis ignoré (JSON invalide).");
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> handleOnMain(chat));
        } catch (Exception e) {
            plugin.getLogger().warning("[Web] Chat Redis non planifié : " + e.getMessage());
        }
    }

    /** Chat publié par un autre nœud : affiché ici, jamais réarchivé ni republié. */
    public void onCrossServerChat(String body) {
        RedisBridgeMessages.Chat chat = RedisBridgeMessages.parseChat(body);
        if (chat == null) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> deliver(chat));
        } catch (Exception e) {
            plugin.getLogger().warning("[Web] Chat cross-serveur non planifié : " + e.getMessage());
        }
    }

    private void handleOnMain(RedisBridgeMessages.Chat chat) {
        String error = deliver(chat);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> finish(chat, error));
    }

    /**
     * @return message d'échec, ou {@code null} si le message a été diffusé
     */
    private String deliver(RedisBridgeMessages.Chat chat) {
        UUID uuid;
        try {
            uuid = UUID.fromString(chat.playerUuid());
        } catch (IllegalArgumentException e) {
            return "uuid invalide";
        }

        if (plugin.getModerationCommand() != null
                && plugin.getModerationCommand().getMuteManager() != null
                && plugin.getModerationCommand().getMuteManager().isMuted(uuid)) {
            return "joueur mute";
        }

        String name = chat.playerName() == null || chat.playerName().isBlank()
                ? "Joueur" : chat.playerName().trim();
        String message = chat.message() == null ? "" : chat.message().trim();
        if (message.isEmpty()) {
            return "message vide";
        }

        Integer factionId = chat.factionId();
        if (factionId != null && factionId > 0) {
            var fm = plugin.getFactionManager();
            if (fm == null) {
                return "faction manager indisponible";
            }
            Faction faction = fm.getFaction(factionId);
            if (faction == null) {
                return "faction introuvable";
            }
            if (!faction.isMember(uuid)) {
                return "pas membre de la faction";
            }
            fm.sendFactionMessage(uuid, name, faction, message, false);
            return null;
        }

        Bukkit.broadcastMessage("§8[§bWeb§8] §f" + name + "§7: §f" + message);
        return null;
    }

    private void finish(RedisBridgeMessages.Chat chat, String error) {
        if (error != null) {
            if (chat.id() > 0) {
                mark(chat.id(), "failed", error);
            }
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(chat.playerUuid());
        } catch (IllegalArgumentException e) {
            uuid = null;
        }
        String name = chat.playerName() == null || chat.playerName().isBlank()
                ? "Joueur" : chat.playerName().trim();
        String message = chat.message() == null ? "" : chat.message().trim();
        Integer factionId = chat.factionId();
        if (!archiveOnce(chat)) {
            return;
        }
        WebChatLog.append(plugin, "web", uuid, name, message, factionId);

        if ((factionId == null || factionId <= 0)
                && plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendMcMessage(name + " (web)", message);
        }

        if (chat.id() > 0) {
            mark(chat.id(), "done", null);
        }
    }

    private boolean archiveOnce(RedisBridgeMessages.Chat chat) {
        if (chat.id() <= 0) {
            return !plugin.getConfigManager().isVelocityEnabled();
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            return AdminActionClaim.claimChatArchive(conn, chat.id());
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] Archive chat non réservée : " + e.getMessage());
            return false;
        }
    }

    private void mark(long id, String status, String result) {
        String sql = "UPDATE web_chat_outbox SET status = ?, result = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                if (result == null) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, truncate(result));
                }
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Web] WebChatBridge mark #" + id + " impossible : " + e.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= RESULT_MAX ? value : value.substring(0, RESULT_MAX);
    }
}
