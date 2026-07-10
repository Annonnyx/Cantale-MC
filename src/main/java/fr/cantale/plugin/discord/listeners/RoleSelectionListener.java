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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gère le salon de sélection de rôles (rôles optionnels auto-attribués).
 */
public class RoleSelectionListener extends ListenerAdapter {

    private final DiscordConfig config;

    public RoleSelectionListener(DiscordConfig config) {
        this.config = config;
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

            StringBuilder desc = new StringBuilder();
            desc.append("Choisis tes rôles optionnels ci-dessous. Tu peux en ajouter ou retirer à tout moment.\n\n");
            desc.append("**Notifications :**\n");
            appendRoleLine(desc, "🔔", config.getAnnouncementsPingRoleId(), "Annonces importantes");
            appendRoleLine(desc, "🛠️", config.getServerStatusPingRoleId(), "Maintenances & état du serveur");
            appendRoleLine(desc, "🎉", config.getEventPingRoleId(), "Événements");
            appendRoleLine(desc, "🏆", config.getTournamentPingRoleId(), "Tournois");
            appendRoleLine(desc, "📦", config.getUpdatePingRoleId(), "Mises à jour");
            appendRoleLine(desc, "🎯", config.getWantedPingRoleId(), "Système de primes (Wanted)");
            appendRoleLine(desc, "🤝", config.getPartnerRoleId(), "Partenariats");
            
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.MAGENTA)
                .setTitle("🎨 Sélection de rôles")
                .setDescription(desc.toString())
                .setFooter("Cantale Network", null)
                .setTimestamp(Instant.now());

            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("role_select")
                .setPlaceholder("Choisis tes rôles...")
                .setMinValues(0);

            Set<Long> usedRoleIds = new LinkedHashSet<>();
            addOption(menuBuilder, usedRoleIds, config.getAnnouncementsPingRoleId(), "🔔 Annonces", "notif_announcements", "Recevoir les annonces importantes");
            addOption(menuBuilder, usedRoleIds, config.getServerStatusPingRoleId(), "🛠️ Maintenances", "notif_maintenance", "Notifications de maintenance");
            addOption(menuBuilder, usedRoleIds, config.getEventPingRoleId(), "🎉 Événements", "notif_events", "Notifications d'événements");
            addOption(menuBuilder, usedRoleIds, config.getTournamentPingRoleId(), "🏆 Tournois", "notif_tournaments", "Notifications de tournois");
            addOption(menuBuilder, usedRoleIds, config.getUpdatePingRoleId(), "📦 Mises à jour", "notif_updates", "Notifications de mises à jour");
            addOption(menuBuilder, usedRoleIds, config.getWantedPingRoleId(), "🎯 Wanted", "notif_wanted", "Notifications système de primes");
            addOption(menuBuilder, usedRoleIds, config.getPartnerRoleId(), "🤝 Partenariats", "role_partner", "Se faire notifier pour un partenariat");

            int count = (int) java.util.stream.Stream.of(
                config.getAnnouncementsPingRoleId(), config.getServerStatusPingRoleId(),
                config.getEventPingRoleId(), config.getTournamentPingRoleId(),
                config.getUpdatePingRoleId(), config.getWantedPingRoleId(),
                config.getPartnerRoleId()
            ).filter(id -> id > 0).count();

            menuBuilder.setMaxValues(count);

            channel.sendMessageEmbeds(embed.build())
                .addActionRow(menuBuilder.build())
                .queue();
        });
    }

    private void appendRoleLine(StringBuilder sb, String emoji, long roleId, String description) {
        if (roleId > 0) {
            sb.append(emoji).append(" <@&").append(roleId).append("> — ").append(description).append("\n");
        }
    }

    private void addOption(StringSelectMenu.Builder builder, Set<Long> usedRoleIds, long roleId, String label, String value, String description) {
        if (roleId > 0 && usedRoleIds.add(roleId)) {
            builder.addOption(label, value, description);
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!"role_select".equals(event.getComponentId())) return;
        if (event.getGuild() == null) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();

        for (String value : event.getValues()) {
            long roleId = switch (value) {
                case "notif_announcements" -> config.getAnnouncementsPingRoleId();
                case "notif_maintenance" -> config.getServerStatusPingRoleId();
                case "notif_events" -> config.getEventPingRoleId();
                case "notif_tournaments" -> config.getTournamentPingRoleId();
                case "notif_updates" -> config.getUpdatePingRoleId();
                case "notif_wanted" -> config.getWantedPingRoleId();
                case "role_partner" -> config.getPartnerRoleId();
                default -> 0L;
            };
            if (roleId == 0L) continue;
            Role role = guild.getRoleById(roleId);
            if (role == null) continue;
            if (member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue();
            } else {
                guild.addRoleToMember(member, role).queue();
            }
        }

        event.reply("✅ Rôles mis à jour !").setEphemeral(true).queue();
    }
}
