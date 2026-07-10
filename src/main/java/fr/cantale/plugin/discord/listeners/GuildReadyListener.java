package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Actions à effectuer quand le bot rejoint/reconnait un serveur Discord.
 */
public class GuildReadyListener extends ListenerAdapter {

    private final DiscordConfig config;
    private final VerificationListener verificationListener;

    public GuildReadyListener(DiscordConfig config, VerificationListener verificationListener) {
        this.config = config;
        this.verificationListener = verificationListener;
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        Guild guild = event.getGuild();
        if (guild.getIdLong() != config.getGuildId()) return;

        // Supprimer l'ancien panel de vérification s'il existe, puis en envoyer un nouveau
        TextChannel verification = guild.getTextChannelById(config.getVerificationChannelId());
        if (verification != null) {
            long botId = event.getJDA().getSelfUser().getIdLong();
            verification.getHistory().retrievePast(20).queue(messages -> {
                for (Message msg : messages) {
                    if (msg.getAuthor().getIdLong() == botId && !msg.getEmbeds().isEmpty()) {
                        String title = msg.getEmbeds().get(0).getTitle();
                        if (title != null && title.contains("Vérification")) {
                            msg.delete().queue();
                            break;
                        }
                    }
                }
                if (verificationListener != null) {
                    verificationListener.sendVerificationPanel(guild);
                }
            });
        } else if (verificationListener != null) {
            verificationListener.sendVerificationPanel(guild);
        }

        // Setup du menu de sélection de rôles
        RoleSelectionListener roleListener = new RoleSelectionListener(config);
        roleListener.setupRoleMenu(guild);
    }
}
