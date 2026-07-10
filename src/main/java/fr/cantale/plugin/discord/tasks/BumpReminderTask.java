package fr.cantale.plugin.discord.tasks;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.time.Instant;

/**
 * Rappel automatique pour bump le serveur sur Disboard, toutes les 2h01.
 *
 * IMPORTANT : ce bot ne peut pas exécuter lui-même la commande /bump à la place
 * d'un membre. Discord considère qu'un bot qui déclenche automatiquement des
 * interactions/slash commands à la place d'un humain relève de l'automatisation
 * de compte (proche du "self-bot"), ce qui est interdit par les CGU développeurs
 * de Discord et peut entraîner un bannissement du bot, voire des sanctions sur
 * Disboard. La commande /bump doit donc toujours être cliquée par un vrai membre.
 *
 * Ce que fait cette tâche : elle poste un rappel dans le salon toutes les
 * 2h01min (le cooldown Disboard est de 2h, +1min de marge de sécurité) pour
 * qu'un membre clique lui-même sur /bump.
 */
public class BumpReminderTask implements Runnable {

    private static final long BUMP_CHANNEL_ID = 1271524199231656112L;

    // 2h01min en ticks Minecraft (20 ticks = 1 seconde)
    public static final long PERIOD_TICKS = (2 * 60 * 60 + 60) * 20L; // 7260s * 20

    private final JavaPlugin plugin;
    private final JDA jda;

    public BumpReminderTask(JavaPlugin plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
    }

    /** À appeler une fois au démarrage du plugin pour lancer le cycle de rappels. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this, PERIOD_TICKS, PERIOD_TICKS);
    }

    @Override
    public void run() {
        TextChannel channel = jda.getTextChannelById(BUMP_CHANNEL_ID);
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0x1A1A1A))
            .setTitle("⬡ C'est l'heure du bump !")
            .setDescription("Le cooldown Disboard est terminé.\n" +
                "Tape **/bump** ici pour faire remonter Cantale dans le classement du serveur.")
            .setFooter("Rappel automatique — prochain dans environ 2h01", null)
            .setTimestamp(Instant.now());

        channel.sendMessage("@here")
            .setEmbeds(embed.build())
            .queue();
    }
}
