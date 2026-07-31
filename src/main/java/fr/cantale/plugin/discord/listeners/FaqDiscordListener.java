package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.chatbot.faq.FaqCore;
import fr.cantale.plugin.chatbot.faq.FaqEntry;
import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Adaptateur Discord du bot FAQ.
 * Écoute les messages textuels et répond par embed.
 */
public class FaqDiscordListener extends ListenerAdapter {

    private final CantalePlugin plugin;
    private final FaqCore faqCore;
    private final DiscordConfig discordConfig;
    private final Set<Long> allowedChannelIds;

    public FaqDiscordListener(CantalePlugin plugin, FaqCore faqCore, DiscordConfig discordConfig) {
        this.plugin = plugin;
        this.faqCore = faqCore;
        this.discordConfig = discordConfig;
        this.allowedChannelIds = new HashSet<>();
        refreshAllowedChannels();
    }

    public void refreshAllowedChannels() {
        allowedChannelIds.clear();
        allowedChannelIds.addAll(faqCore.getConfig().getDiscordChannels());
        if (discordConfig.getFaqChannelId() > 0L) {
            allowedChannelIds.add(discordConfig.getFaqChannelId());
        }
        // Fallback sur le salon support si aucun canal FAQ n'est configuré
        if (allowedChannelIds.isEmpty()) {
            allowedChannelIds.add(discordConfig.getSupportChannelId());
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getMessage().isWebhookMessage()) return;

        long channelId = event.getChannel().getIdLong();
        if (!allowedChannelIds.contains(channelId)) return;

        String message = event.getMessage().getContentRaw();
        if (message.startsWith("/")) return;

        FaqEntry entry = faqCore.match(message, event.getAuthor().getId(), "discord");
        if (entry == null) return;

        if (event.getChannel() instanceof TextChannel textChannel) {
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x1A1A1A))
                .setTitle("Cantale — FAQ")
                .setDescription(entry.getResponse().replace("\\n", "\n"))
                .setFooter("Posez vos questions ici", null)
                .setTimestamp(Instant.now());

            textChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {},
                error -> plugin.getLogger().warning("[Discord FAQ] Échec envoi embed: " + error.getMessage())
            );
        }
    }
}
