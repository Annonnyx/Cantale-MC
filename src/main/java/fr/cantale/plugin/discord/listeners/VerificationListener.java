package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Gère la vérification des nouveaux membres et l'attribution des rôles.
 * Le message de bienvenue public n'est envoyé qu'APRÈS vérification réussie
 * (voir onButtonInteraction), plus jamais au simple join.
 */
public class VerificationListener extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final DiscordConfig config;
    private final WelcomeListener welcomeListener;
    private final Map<Long, Long> cooldowns = new HashMap<>();

    public VerificationListener(JavaPlugin plugin, DiscordConfig config, WelcomeListener welcomeListener) {
        this.plugin = plugin;
        this.config = config;
        this.welcomeListener = welcomeListener;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        // Attribuer rôle non-vérifié — aucun message public tant que le membre n'est pas vérifié
        Role unverified = guild.getRoleById(config.getUnverifiedRoleId());
        if (unverified != null) {
            guild.addRoleToMember(member, unverified).queue();
        }
    }

    /** Envoie le panel de vérification permanent dans #verification (appelé au startup). */
    public void sendVerificationPanel(Guild guild) {
        TextChannel verification = guild.getTextChannelById(config.getVerificationChannelId());
        if (verification == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("🔐 Vérification obligatoire")
            .setDescription("Pour accéder au serveur Cantale, clique sur le bouton **Se vérifier** ci-dessous.\n\n" +
                "📜 Pense à lire les règles dans <#" + config.getRulesChannelId() + ">.")
            .setFooter("Cantale Network", null)
            .setTimestamp(Instant.now());

        verification.sendMessageEmbeds(embed.build())
            .addActionRow(Button.success("verify_button", "✅ Se vérifier"))
            .queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!"verify_button".equals(event.getComponentId())) return;
        if (event.getGuild() == null) return;

        Member member = event.getMember();
        if (member == null) return;

        Guild guild = event.getGuild();

        // Vérifier si déjà vérifié
        Role verified = guild.getRoleById(config.getVerifiedRoleId());
        if (verified != null && member.getRoles().contains(verified)) {
            event.reply("✅ Tu es déjà vérifié !").setEphemeral(true).queue();
            return;
        }

        // Anti-spam cooldown
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(member.getIdLong());
        if (last != null && now - last < 5000) {
            event.reply("⏳ Attends un peu avant de recliquer.").setEphemeral(true).queue();
            return;
        }
        cooldowns.put(member.getIdLong(), now);

        // Retirer rôle non-vérifié
        Role unverified = guild.getRoleById(config.getUnverifiedRoleId());
        if (unverified != null && member.getRoles().contains(unverified)) {
            guild.removeRoleFromMember(member, unverified).queue();
        }

        // Attribuer rôle vérifié
        verified = guild.getRoleById(config.getVerifiedRoleId());
        if (verified != null && !member.getRoles().contains(verified)) {
            guild.addRoleToMember(member, verified).queue();
        }

        // Message privé de confirmation
        String welcomeMsg = config.getWelcomeMessage();
        member.getUser().openPrivateChannel()
            .flatMap(ch -> ch.sendMessage("✅ **Vérification réussie !**\n" + welcomeMsg))
            .queue(null, failure -> {
                // Si DM impossible, on ignore silencieusement
            });

        // Réponse éphémère
        event.reply("✅ Tu es maintenant vérifié ! Bienvenue sur Cantale.").setEphemeral(true).queue();

        // Message d'accueil public dans le chat général — uniquement maintenant
        welcomeListener.sendWelcome(guild, member);

        // Log
        logVerification(guild, member, true);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getMember() == null) return;

        long channelId = event.getChannel().getIdLong();

        // Vérification via réaction (si configuré)
        if (config.getVerificationMethod().equals("reaction") && channelId == config.getVerificationChannelId()) {
            // Méthode non implémentée ici, le bouton est plus fiable
        }

        // Rappel auto dans salon vérification
        if (channelId == config.getVerificationChannelId()) {
            Member member = event.getMember();
            if (member != null) {
                Role verified = event.getGuild().getRoleById(config.getVerifiedRoleId());
                if (verified != null && !member.getRoles().contains(verified)) {
                    event.getMessage().reply("Pour accéder au serveur, clique sur le bouton **Se vérifier** dans <#" + config.getVerificationChannelId() + ">.").queue(
                        msg -> msg.delete().queueAfter(30, TimeUnit.SECONDS)
                    );
                }
            }
        }
    }

    private void logVerification(Guild guild, Member member, boolean success) {
        TextChannel logs = guild.getTextChannelById(config.getModLogsChannelId());
        if (logs == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(success ? Color.GREEN : Color.RED)
            .setTitle(success ? "✅ Vérification réussie" : "❌ Vérification échouée")
            .addField("Utilisateur", member.getAsMention() + " (`" + member.getId() + "`)", false)
            .setTimestamp(Instant.now());

        logs.sendMessageEmbeds(embed.build()).queue();
    }
}
