package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import fr.cantale.plugin.CantalePlugin;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Pont chat Minecraft ↔ Discord.
 * Envoi MC → Discord (via events Bukkit).
 * Réception Discord → MC (via events JDA).
 */
public class ChatBridgeListener extends ListenerAdapter {

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private final JDA jda;
    private long lastDiscordMessageTime = 0;
    private boolean processingMcMessage = false;

    public ChatBridgeListener(CantalePlugin plugin, DiscordConfig config, JDA jda) {
        this.plugin = plugin;
        this.config = config;
        this.jda = jda;
    }

    /**
     * Appelé par ChatListener quand un joueur parle dans MC.
     */
    public void sendToDiscord(String playerName, String message) {
        if (!config.isChatBridgeEnabled() || jda == null) return;

        TextChannel channel = jda.getTextChannelById(config.getMcChatChannelId());
        if (channel == null) return;

        // Anti-boucle : ne pas renvoyer un message qui vient de Discord
        if (System.currentTimeMillis() - lastDiscordMessageTime < 2000) return;

        processingMcMessage = true;
        try {
            String format = config.getMcToDiscordFormat()
                .replace("%player%", stripMinecraftColors(playerName))
                .replace("%message%", stripMinecraftColors(message))
                .replace("%server%", "Cantale");

            channel.sendMessage(format).queue(
                success -> {},
                error -> plugin.getLogger().warning("[Discord] Chat bridge send failed: " + error.getMessage())
            );
        } finally {
            // Reset après un délai pour éviter les boucles
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> processingMcMessage = false, 5L);
        }
    }

    /**
     * Messages système (join, quit, death) → Discord.
     */
    public void sendSystemToDiscord(String formattedMessage) {
        if (!config.isChatBridgeEnabled() || jda == null) return;

        TextChannel channel = jda.getTextChannelById(config.getMcChatChannelId());
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GRAY)
            .setDescription("*" + stripMinecraftColors(formattedMessage) + "*")
            .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private String stripMinecraftColors(String input) {
        if (input == null) return null;
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Envoi serveur status → Discord (player count, TPS).
     */
    public void sendServerStatus(int online, int max, double tps) {
        if (jda == null) return;

        TextChannel channel = jda.getTextChannelById(config.getMinecraftStatusChannelId());
        if (channel == null) return;

        String status = tps > 18 ? "🟢" : tps > 15 ? "🟡" : "🔴";
        String msg = status + " **Cantale** — " + online + "/" + max + " joueurs — TPS: " + String.format("%.1f", tps);

        // On envoie un message simple (le salon peut avoir un message pin édité)
        // Pour un vrai statut persistant il faudrait éditer un message existant
        channel.sendMessage(msg).queue(
            sent -> sent.delete().queueAfter(30, TimeUnit.MINUTES)
        );
    }

    // ─── Discord → MC ───

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!config.isChatBridgeEnabled()) return;
        if (event.getAuthor().isBot() && config.isIgnoreBots()) return;
        if (event.getMessage().isWebhookMessage() && config.isIgnoreBots()) return;
        if (event.getChannel().getIdLong() != config.getMcChatChannelId()) return;
        if (processingMcMessage) return;

        lastDiscordMessageTime = System.currentTimeMillis();

        String name = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        String message = event.getMessage().getContentRaw();

        // Filtres
        if (message.contains("@everyone") || message.contains("@here")) return;

        // Format
        String format = config.getDiscordToMcFormat()
            .replace("%name%", name)
            .replace("%message%", message);

        // Envoyer sur le serveur Minecraft
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(format);
        });
    }
}
