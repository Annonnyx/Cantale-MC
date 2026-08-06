package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gère le salon de sélection de rôles (rôles optionnels auto-attribués).
 */
public class RoleSelectionListener extends ListenerAdapter {

    private record RoleOption(String value, long roleId, String emoji, String label, String description) {}

    private final DiscordConfig config;

    public RoleSelectionListener(DiscordConfig config) {
        this.config = config;
    }

    private List<RoleOption> getAvailableOptions() {
        List<RoleOption> all = new ArrayList<>();
        all.add(new RoleOption("notif_announcements", config.getAnnouncementsPingRoleId(), "🔔", "Annonces", "Recevoir les annonces importantes"));
        all.add(new RoleOption("notif_maintenance", config.getServerStatusPingRoleId(), "🛠️", "Maintenances", "Notifications de maintenance & état du serveur"));
        all.add(new RoleOption("notif_events", config.getEventPingRoleId(), "🎉", "Événements", "Notifications d'événements"));
        all.add(new RoleOption("notif_tournaments", config.getTournamentPingRoleId(), "🏆", "Tournois", "Notifications de tournois"));
        all.add(new RoleOption("notif_updates", config.getUpdatePingRoleId(), "📦", "Mises à jour", "Notifications de mises à jour"));
        all.add(new RoleOption("notif_wanted", config.getWantedPingRoleId(), "🎯", "Wanted", "Notifications système de primes"));
        all.add(new RoleOption("role_partner", config.getPartnerRoleId(), "🤝", "Partenariats", "Se faire notifier pour un partenariat"));

        // Filtrer les ID invalides (<= 0) et éliminer les doublons d'ID de rôle dans la config
        List<RoleOption> unique = new ArrayList<>();
        Set<Long> seenRoleIds = new HashSet<>();

        for (RoleOption opt : all) {
            if (opt.roleId() > 0 && seenRoleIds.add(opt.roleId())) {
                unique.add(opt);
            }
        }
        return unique;
    }

    public void setupRoleMenu(Guild guild) {
        TextChannel channel = guild.getTextChannelById(config.getRoleSelectionChannelId());
        if (channel == null) return;

        channel.getHistoryFromBeginning(50).queue(history -> {
            for (net.dv8tion.jda.api.entities.Message msg : history.getRetrievedHistory()) {
                if (msg.getAuthor().equals(guild.getJDA().getSelfUser())) {
                    msg.delete().queue();
                }
            }

            List<RoleOption> options = getAvailableOptions();
            if (options.isEmpty()) return;

            StringBuilder desc = new StringBuilder();
            desc.append("Choisis tes rôles optionnels ci-dessous. Tu peux en ajouter ou en retirer à tout moment.\n\n");
            desc.append("**Notifications & Rôles disponibles :**\n");

            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("role_select")
                .setPlaceholder("Choisis tes rôles...")
                .setMinValues(0)
                .setMaxValues(options.size());

            for (RoleOption opt : options) {
                desc.append(opt.emoji()).append(" <@&").append(opt.roleId()).append("> — ").append(opt.description()).append("\n");
                menuBuilder.addOption(opt.emoji() + " " + opt.label(), opt.value(), opt.description());
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.MAGENTA)
                .setTitle("🎨 Sélection de rôles")
                .setDescription(desc.toString())
                .setFooter("Cantale Network", null)
                .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build())
                .addActionRow(menuBuilder.build())
                .queue();
        });
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!"role_select".equals(event.getComponentId())) return;
        if (event.getGuild() == null) return;

        Member member = event.getMember();
        if (member == null) return;

        Guild guild = event.getGuild();
        List<String> selectedValues = event.getValues();
        List<RoleOption> options = getAvailableOptions();

        for (RoleOption opt : options) {
            Role role = guild.getRoleById(opt.roleId());
            if (role == null) continue;

            boolean shouldHave = selectedValues.contains(opt.value());
            boolean currentlyHas = member.getRoles().contains(role);

            if (shouldHave && !currentlyHas) {
                guild.addRoleToMember(member, role).queue();
            } else if (!shouldHave && currentlyHas) {
                guild.removeRoleFromMember(member, role).queue();
            }
        }

        event.reply("✅ Vos rôles ont été mis à jour avec succès !").setEphemeral(true).queue();
    }
}
