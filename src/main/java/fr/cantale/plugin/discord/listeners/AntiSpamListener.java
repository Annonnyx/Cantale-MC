package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Salon piège anti-bot : supprime les messages et bannit tout membre qui écrit dedans.
 */
public class AntiSpamListener extends ListenerAdapter {

    private static final int MESSAGE_DELETE_DAYS = 7;

    private final DiscordConfig config;

    public AntiSpamListener(DiscordConfig config) {
        this.config = config;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getMember() == null) return;
        if (event.getAuthor().isBot()) return;

        long antiBotChannelId = config.getAntiBotChannelId();
        if (antiBotChannelId <= 0L || event.getChannel().getIdLong() != antiBotChannelId) return;

        Member member = event.getMember();
        if (isStaff(member)) return;

        Message message = event.getMessage();
        TextChannel channel = message.getChannel().asTextChannel();
        User user = member.getUser();

        message.delete().reason("Salon anti-bot").queue(
            s -> purgeUserMessages(channel, user.getIdLong()),
            e -> purgeUserMessages(channel, user.getIdLong())
        );

        member.ban(MESSAGE_DELETE_DAYS, TimeUnit.DAYS)
            .reason("Message dans le salon anti-bot")
            .queue(
                s -> logBan(event, member),
                e -> logBanFailure(event, member, e.getMessage())
            );
    }

    private void purgeUserMessages(TextChannel channel, long userId) {
        channel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
            List<Message> userMessages = new ArrayList<>();
            for (Message msg : messages) {
                if (msg.getAuthor().getIdLong() == userId && !msg.isPinned()) {
                    userMessages.add(msg);
                }
            }
            if (userMessages.isEmpty()) return;

            List<Message> bulkDeletable = new ArrayList<>();
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
            for (Message msg : userMessages) {
                if (msg.getTimeCreated().toInstant().toEpochMilli() >= cutoff) {
                    bulkDeletable.add(msg);
                } else {
                    msg.delete().queue(null, err -> {});
                }
            }

            if (bulkDeletable.size() == 1) {
                bulkDeletable.get(0).delete().queue(null, err -> {});
            } else if (bulkDeletable.size() > 1) {
                channel.deleteMessages(bulkDeletable).queue(null, err -> {
                    bulkDeletable.forEach(msg -> msg.delete().queue(null, e -> {}));
                });
            }
        });
    }

    private boolean isStaff(Member member) {
        for (Role role : member.getRoles()) {
            if (config.getStaffRoleIds().contains(role.getIdLong())) return true;
        }
        return false;
    }

    private void logBan(MessageReceivedEvent event, Member member) {
        TextChannel logs = event.getGuild().getTextChannelById(config.getModLogsChannelId());
        if (logs == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLACK)
            .setTitle("🤖 Salon anti-bot")
            .setDescription(member.getAsMention() + " a écrit dans <#" + config.getAntiBotChannelId() + "> et a été banni.")
            .setTimestamp(Instant.now())
            .setFooter("ID: " + member.getId(), member.getEffectiveAvatarUrl());

        logs.sendMessageEmbeds(embed.build()).queue();
    }

    private void logBanFailure(MessageReceivedEvent event, Member member, String error) {
        TextChannel logs = event.getGuild().getTextChannelById(config.getModLogsChannelId());
        if (logs == null) return;

        logs.sendMessage("⚠️ Échec du ban anti-bot pour " + member.getAsMention() + " : " + error).queue();
    }
}
