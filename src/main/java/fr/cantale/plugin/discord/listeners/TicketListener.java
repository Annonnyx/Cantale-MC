package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Système de tickets Discord pour le support Cantale.
 *
 * Corrections apportées :
 *  - Le bouton "Fermer" n'accusait jamais réception de l'interaction
 *    (Discord affichait "Cette interaction a échoué" au staff même si ça fonctionnait derrière).
 *  - Le bouton "Rouvrir" n'était jamais envoyé après une fermeture : le handler
 *    existait mais personne ne pouvait jamais cliquer dessus.
 *  - Un membre dont le ticket avait été fermé ne pouvait plus jamais en ouvrir un
 *    nouveau : la détection de "ticket déjà ouvert" matchait aussi les salons archivés.
 */
public class TicketListener extends ListenerAdapter {

    private static final long SUPPORT_CATEGORY_ID = 1504899461401546954L;
    private static final long CLOSED_CATEGORY_ID = 1513940161682211076L;
    private static final EnumSet<Permission> OPEN_PERMS =
        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES);

    private final DiscordConfig config;

    public TicketListener(DiscordConfig config) {
        this.config = config;
    }

    public void register(Guild guild) {
        guild.upsertCommand(Commands.slash("ticket", "Gestion des tickets support")
            .addSubcommands(
                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("setup", "Envoyer le panel de création de ticket dans le salon support"),
                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("close", "Fermer le ticket actuel")
            )
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
        ).queue();
    }

    /** Envoie le message de création de ticket dans le salon support. */
    public void sendTicketPanel(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.MAGENTA)
            .setTitle("🎫 Support Cantale")
            .setDescription("Besoin d'aide ? Clique sur le bouton ci-dessous pour ouvrir un ticket privé avec le staff.")
            .setFooter("Cantale Network", null)
            .setTimestamp(Instant.now());

        StringSelectMenu menu = StringSelectMenu.create("ticket_open")
            .setPlaceholder("Choisis le motif du ticket")
            .addOption("Problème", "probleme")
            .addOption("Partenariat", "partenariat")
            .addOption("Plainte", "plainte")
            .addOption("Demande de P.A.S.D.I.C.", "pasdic")
            .build();

        channel.sendMessageEmbeds(embed.build()).setActionRow(menu).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != config.getGuildId()) return;
        if (!event.getName().equals("ticket")) return;

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Usage : /ticket setup | /ticket close").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "setup" -> {
                if (!isStaff(event.getMember())) {
                    event.reply("❌ Réservé au staff.").setEphemeral(true).queue();
                    return;
                }
                TextChannel ch = event.getGuild().getTextChannelById(config.getSupportChannelId());
                if (ch == null) {
                    event.reply("❌ Salon support non configuré.").setEphemeral(true).queue();
                    return;
                }
                sendTicketPanel(ch);
                event.reply("✅ Panel envoyé dans " + ch.getAsMention()).setEphemeral(true).queue();
            }
            case "close" -> {
                TextChannel ch = event.getChannel().asTextChannel();
                String name = ch.getName();
                String topic = ch.getTopic();
                boolean isTicket = topic != null && topic.startsWith("ticket:");

                if (!isTicket) {
                    event.reply("❌ Ce n'est pas un salon ticket.").setEphemeral(true).queue();
                } else if (name.startsWith("closed-")) {
                    event.reply("❌ Ce ticket est déjà fermé.").setEphemeral(true).queue();
                } else {
                    event.reply("🔒 Fermeture du ticket... Le salon sera archivé.").queue();
                    closeTicketChannel(ch, event.getGuild());
                }
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String btnId = event.getComponentId();
        if ("ticket_close".equals(btnId)) {
            handleCloseButton(event);
            return;
        }
        if ("ticket_reopen".equals(btnId)) {
            handleReopenButton(event);
            return;
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!"ticket_open".equals(event.getComponentId())) return;
        if (event.getGuild() == null) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (member == null) return;

        String reason = event.getValues().isEmpty() ? "autre" : event.getValues().get(0);

        // Vérifier si le membre a déjà un ticket OUVERT (on ignore les tickets archivés/fermés,
        // sinon un membre dont le ticket a été fermé ne peut plus jamais en réouvrir un nouveau)
        String memberTag = "ticket:" + member.getId();
        TextChannel existing = guild.getTextChannels().stream()
            .filter(c -> c.getTopic() != null && c.getTopic().contains(memberTag))
            .filter(c -> !c.getName().startsWith("closed-"))
            .findFirst().orElse(null);
        if (existing != null) {
            event.reply("❌ Tu as déjà un ticket ouvert : " + existing.getAsMention()).setEphemeral(true).queue();
            return;
        }

        // Créer le salon ticket nommé "utilisateur-motif" dans la catégorie Support
        String base = member.getEffectiveName();
        String sanitizedUser = base.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String motif = reason.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String ticketName = "[" + sanitizedUser + "]-[" + motif + "]";

        Category supportCat = guild.getCategoryById(SUPPORT_CATEGORY_ID);

        guild.createTextChannel(ticketName)
            .setParent(supportCat)
            .addPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL))
            .addPermissionOverride(member, OPEN_PERMS, EnumSet.noneOf(Permission.class))
            .queue(ch -> {
                // stocker l'auteur dans le topic pour retrouver facilement son ticket
                try { ch.getManager().setTopic(memberTag).queue(); } catch (Exception ignored) {}
                // Donner accès aux rôles staff
                for (Long roleId : config.getStaffRoleIds()) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        ch.getManager().putPermissionOverride(role, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue();
                    }
                }

                long modPingRoleId = config.getModeratorPingRoleId();
                String modPing = modPingRoleId > 0 ? "<@&" + modPingRoleId + "> " : "";

                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.MAGENTA)
                    .setTitle("🎫 Ticket — " + member.getEffectiveName())
                    .setDescription("Motif : " + reason + "\n\nDécris ton problème ici. Le staff te répondra dès que possible.")
                    .setTimestamp(Instant.now());

                Button closeBtn = Button.danger("ticket_close", "🔒 Fermer le ticket");
                ch.sendMessage(modPing + member.getAsMention())
                    .setEmbeds(embed.build()).setActionRow(closeBtn).queue();

                event.reply("✅ Ticket créé : " + ch.getAsMention()).setEphemeral(true).queue();
            });
    }

    private void handleCloseButton(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;
        TextChannel ch = event.getChannel().asTextChannel();
        Guild guild = event.getGuild();

        if (ch.getName().startsWith("closed-")) {
            event.reply("❌ Ce ticket est déjà fermé.").setEphemeral(true).queue();
            return;
        }

        // On accuse réception TOUT DE SUITE : sans ça Discord affiche "Cette interaction
        // a échoué" au staff même quand la fermeture se déroule bien derrière.
        event.deferReply(true).queue();
        closeTicketChannel(ch, guild);
        event.getHook().sendMessage("🔒 Fermeture du ticket en cours...").queue();
    }

    private void handleReopenButton(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;

        TextChannel ch = event.getChannel().asTextChannel();
        Guild guild = event.getGuild();

        String topic = ch.getTopic();
        String ownerId = extractOwnerIdFromTopic(topic);

        String name = ch.getName();
        if (!name.startsWith("closed-") && ownerId == null) {
            event.reply("❌ Ce salon n'est pas un ticket archivé.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        if (ownerId != null) {
            final String finalOwnerId = ownerId;
            try {
                var owner = guild.getMemberById(finalOwnerId);
                if (owner != null) {
                    ch.getManager().putPermissionOverride(owner, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue();
                    owner.getUser().openPrivateChannel()
                        .flatMap(pc -> pc.sendMessage("🔓 Ton ticket a été rouvert par le staff : " + ch.getAsMention()))
                        .queue(null, ignored -> {});
                }
            } catch (Exception ignored) {}
        }

        Category supportCat = guild.getCategoryById(SUPPORT_CATEGORY_ID);
        String newName = name.startsWith("closed-") ? name.substring("closed-".length()) : name;

        var manager = ch.getManager().setName(newName);
        if (supportCat != null) {
            manager.setParent(supportCat);
        }

        manager.queue(success -> {
            for (Long roleId : config.getStaffRoleIds()) {
                try {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) ch.getManager().putPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), EnumSet.noneOf(Permission.class)).queue();
                } catch (Exception ignored) {}
            }

            ch.sendMessage("🔓 Ticket rouvert. Le demandeur a été prévenu.")
                .setActionRow(Button.danger("ticket_close", "🔒 Fermer le ticket"))
                .queue();
            event.getHook().editOriginal("✅ Ticket rouvert.").queue();
        }, failure -> {
            event.getHook().editOriginal("❌ Impossible de déplacer/rouvrir le salon.").queue();
        });
    }

    private void closeTicketChannel(TextChannel ch, Guild guild) {
        String name = ch.getName();
        String topic = ch.getTopic();
        boolean isTicket = topic != null && topic.startsWith("ticket:");
        if (!isTicket || name.startsWith("closed-")) return;

        Runnable archive = () -> {
            String closedName = "closed-" + name;
            ch.getManager().setName(closedName).queue();

            try {
                ch.getManager().putPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            } catch (Exception ignored) {}

            try {
                String ownerId = extractOwnerIdFromTopic(topic);
                if (ownerId != null) {
                    var owner = guild.getMemberById(ownerId);
                    if (owner != null) {
                        ch.getManager().putPermissionOverride(owner, EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL)).queue();
                    }
                }
            } catch (Exception ignored) {}

            for (Long roleId : config.getStaffRoleIds()) {
                try {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) ch.getManager().putPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), EnumSet.noneOf(Permission.class)).queue();
                } catch (Exception ignored) {}
            }

            // Bouton de réouverture — sans lui, "ticket_reopen" n'était jamais atteignable
            ch.sendMessage("🔒 Ticket fermé. Salon archivé et masqué pour le demandeur.")
                .setActionRow(Button.success("ticket_reopen", "🔓 Rouvrir le ticket"))
                .queue();
        };

        var closedCat = guild.getCategoryById(CLOSED_CATEGORY_ID);
        if (closedCat != null) {
            ch.getManager().setParent(closedCat).queue(success -> archive.run(), failure -> archive.run());
        } else {
            archive.run();
        }
    }

    /** Extrait l'identifiant du propriétaire depuis le topic d'un ticket. */
    private String extractOwnerIdFromTopic(String topic) {
        if (topic == null) return null;
        if (!topic.startsWith("ticket:")) return null;
        return topic.substring("ticket:".length());
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        for (Long id : config.getStaffRoleIds()) {
            if (member.getRoles().stream().anyMatch(r -> r.getIdLong() == id)) return true;
        }
        return false;
    }
}
