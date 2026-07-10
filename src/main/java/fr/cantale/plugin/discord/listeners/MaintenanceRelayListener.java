package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;

/**
 * Relaie les messages de maintenance de l'hébergeur vers le salon
 * minecraft-status avec un ping du rôle de notifications maintenances.
 */
public class MaintenanceRelayListener extends ListenerAdapter {

    private final DiscordConfig config;

    public MaintenanceRelayListener(DiscordConfig config) {
        this.config = config;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getChannel().getIdLong() != config.getHostingAnnouncementsChannelId()) return;
        if (config.getHostingAnnouncementsChannelId() == 0L) return;
        if (config.getMinecraftStatusChannelId() == 0L) return;
        if (config.getServerStatusPingRoleId() == 0L) return;

        TextChannel targetChannel = event.getGuild().getTextChannelById(config.getMinecraftStatusChannelId());
        if (targetChannel == null) return;

        long pingRoleId = config.getServerStatusPingRoleId();
        String ping = "<@&" + pingRoleId + ">";

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("🛠️ Maintenance hébergeur")
            .setDescription(event.getMessage().getContentRaw().isEmpty() ? "*Message sans texte*" : event.getMessage().getContentRaw())
            .setFooter("Reçu de " + event.getAuthor().getName(), event.getAuthor().getEffectiveAvatarUrl())
            .setTimestamp(Instant.now());

        // Relaie le contenu textuel avec le ping, et les embeds s'il y en a
        if (!event.getMessage().getEmbeds().isEmpty()) {
            targetChannel.sendMessage(ping)
                .addEmbeds(event.getMessage().getEmbeds())
                .queue();
        } else {
            targetChannel.sendMessage(ping)
                .setEmbeds(embed.build())
                .queue();
        }
    }
}
