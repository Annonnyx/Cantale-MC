package fr.cantale.plugin.discord.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envoie le message de bienvenue stylisé dans le salon général.
 * Déclenché UNIQUEMENT après vérification réussie (appelé par VerificationListener),
 * plus jamais au simple join.
 */
public class WelcomeListener extends ListenerAdapter {

    // Salon général (chat public)
    private static final long WELCOME_CHANNEL_ID = 1504899974775967894L;

    // Phrases d'intro qui tournent aléatoirement pour éviter la répétition
    private static final String[] INTROS = {
        "Un nouveau nom vient de s'inscrire sur les terres de Cantale.",
        "Les hexagones tournent, un survivant de plus entre dans l'arène.",
        "La porte de Cantale s'ouvre. Une vie commence — les deux autres attendront.",
        "Un nouveau venu foule le sol de Cantale. Combien de temps tiendra-t-il ?",
        "Cantale accueille un nouveau visage. Le monde ne pardonne pas deux fois."
    };

    /**
     * À appeler après qu'un membre ait validé la vérification.
     */
    public void sendWelcome(Guild guild, Member member) {
        TextChannel channel = guild.getTextChannelById(WELCOME_CHANNEL_ID);
        if (channel == null) return;

        String intro = INTROS[ThreadLocalRandom.current().nextInt(INTROS.length)];
        int memberCount = guild.getMemberCount();

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0x1A1A1A))
            .setAuthor("CANTALE — NOUVELLE ARRIVÉE", "https://www.cantale.world")
            .setTitle("⬡ ⬡ ⬡  " + member.getEffectiveName() + " a rejoint Cantale")
            .setDescription(
                intro + "\n\n" +
                member.getAsMention() + ", tu disposes de **3 vies**. " +
                "Perds-les toutes, et Cantale ne se souviendra plus de ton nom.\n\n" +
                "⬡ **Vie 1** — ⬡ **Vie 2** — ~~⬡~~ **Vie 3** *(exemple : dernière vie perdue)*"
            )
            .addField("🌍 Explorer", "[cantale.world](https://www.cantale.world) · [cantale.store](https://www.cantale.store)", true)
            .addField("👥 Population", "Tu es le membre n°" + memberCount, true)
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setFooter("Cantale — Le monde appartient à ceux qui savent le conserver", null)
            .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build())
            .addActionRow(
                Button.link("https://www.cantale.world", "🌐 Le site"),
                Button.link("https://www.cantale.store", "🛒 La boutique")
            )
            .queue();
    }
}
