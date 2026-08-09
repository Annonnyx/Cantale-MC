package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Système de tickets Discord pour le support Cantale.
 *
 * Convention de nommage des salons ticket :
 *  - Ouvert : "[OPEN] <raison> <demandeur>"
 *  - Fermé  : "[CLOSED] <raison> <demandeur>"
 * Le topic du salon stocke "ticket:<ownerId>:<raison>" afin de retrouver le
 * demandeur et le motif même après renommage ou déplacement du salon.
 * Discord normalise les noms de salons en minuscules : les vérifications de
 * préfixe sont donc insensibles à la casse.
 */
public class TicketListener extends ListenerAdapter {

    private static final long SUPPORT_CATEGORY_ID = 1504899461401546954L;
    private static final long CLOSED_CATEGORY_ID = 1513940161682211076L;
    private static final String OPEN_PREFIX = "[OPEN]";
    private static final String CLOSED_PREFIX = "[CLOSED]";
    private static final String TOPIC_PREFIX = "ticket:";
    private static final int MAX_NAME_LENGTH = 100; // limite Discord
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
                String topic = ch.getTopic();
                boolean isTicket = topic != null && topic.startsWith(TOPIC_PREFIX);

                if (!isTicket) {
                    event.reply("❌ Ce n'est pas un salon ticket.").setEphemeral(true).queue();
                } else if (isClosedTicketName(ch.getName())) {
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

        // Un membre ne peut avoir qu'un seul ticket OUVERT : on ignore les tickets
        // fermés/archivés, sinon un membre dont le ticket a été fermé ne pourrait
        // plus jamais en ouvrir un nouveau.
        String memberTag = TOPIC_PREFIX + member.getId();
        TextChannel existing = guild.getTextChannels().stream()
            .filter(c -> c.getTopic() != null && c.getTopic().contains(memberTag))
            .filter(c -> isOpenTicketName(c.getName()))
            .findFirst().orElse(null);
        if (existing != null) {
            event.reply("❌ Tu as déjà un ticket ouvert : " + existing.getAsMention()).setEphemeral(true).queue();
            return;
        }

        String ticketName = buildTicketName(OPEN_PREFIX, reason, member.getEffectiveName());
        String topic = memberTag + ":" + reason;

        Category supportCat = guild.getCategoryById(SUPPORT_CATEGORY_ID);

        var action = guild.createTextChannel(ticketName)
            .setTopic(topic)
            .addPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL))
            .addPermissionOverride(member, OPEN_PERMS, EnumSet.noneOf(Permission.class));
        if (supportCat != null) {
            action.setParent(supportCat);
        }

        action.queue(ch -> {
            // Donner accès aux rôles staff
            for (Long roleId : config.getStaffRoleIds()) {
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    ch.getManager().putPermissionOverride(role, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue(null, ignored -> {});
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

            // DM de confirmation au demandeur
            sendDm(member.getUser(), "🎫 Ton ticket a été créé : " + ch.getAsMention());

            event.reply("✅ Ticket créé : " + ch.getAsMention()).setEphemeral(true).queue();
        }, failure -> event.reply("❌ Impossible de créer le ticket.").setEphemeral(true).queue());
    }

    private void handleCloseButton(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;
        TextChannel ch = event.getChannel().asTextChannel();
        Guild guild = event.getGuild();

        if (isClosedTicketName(ch.getName())) {
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
        String currentName = ch.getName();

        if (!isClosedTicketName(currentName) && ownerId == null) {
            event.reply("❌ Ce salon n'est pas un ticket archivé.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        String reason = extractReasonFromTopic(topic);
        if (reason == null || reason.isBlank()) reason = "autre";
        String ownerName = resolveOwnerName(guild, ownerId, currentName, reason);
        String openName = buildTicketName(OPEN_PREFIX, reason, ownerName);

        // Restaurer l'accès du demandeur et le prévenir en DM
        if (ownerId != null) {
            resolveOwnerMember(guild, ownerId, owner -> {
                if (owner == null) return;
                try {
                    ch.getManager().putPermissionOverride(owner, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue(null, ignored -> {});
                } catch (Exception ignored) {}
            });
            dmOwner(guild, ownerId, "🔓 Ton ticket a été rouvert par le staff.");
        }

        Category supportCat = guild.getCategoryById(SUPPORT_CATEGORY_ID);
        var manager = ch.getManager().setName(openName);
        if (supportCat != null) {
            manager.setParent(supportCat);
        }

        manager.queue(success -> {
            for (Long roleId : config.getStaffRoleIds()) {
                try {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) ch.getManager().putPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), EnumSet.noneOf(Permission.class)).queue(null, ignored -> {});
                } catch (Exception ignored) {}
            }

            ch.sendMessage("🔓 Ticket rouvert. Le demandeur a été prévenu.")
                .setActionRow(Button.danger("ticket_close", "🔒 Fermer le ticket"))
                .queue(null, ignored -> {});
            event.getHook().editOriginal("✅ Ticket rouvert.").queue(null, ignored -> {});
        }, failure -> event.getHook().editOriginal("❌ Impossible de déplacer/rouvrir le salon.").queue(null, ignored -> {}));
    }

    private void closeTicketChannel(TextChannel ch, Guild guild) {
        if (ch == null || guild == null) return;
        String topic = ch.getTopic();
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) return;
        String currentName = ch.getName();
        if (isClosedTicketName(currentName)) return;

        String ownerId = extractOwnerIdFromTopic(topic);
        String reason = extractReasonFromTopic(topic);
        if (reason == null || reason.isBlank()) reason = "autre";
        String ownerName = resolveOwnerName(guild, ownerId, currentName, reason);
        String closedName = buildTicketName(CLOSED_PREFIX, reason, ownerName);

        Runnable archive = () -> {
            ch.getManager().setName(closedName).queue(null, ignored -> {});

            try {
                ch.getManager().putPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL)).queue(null, ignored -> {});
            } catch (Exception ignored) {}

            // Retirer l'accès du demandeur et le prévenir en DM
            if (ownerId != null) {
                resolveOwnerMember(guild, ownerId, owner -> {
                    if (owner == null) return;
                    try {
                        ch.getManager().putPermissionOverride(owner, EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL)).queue(null, ignored -> {});
                    } catch (Exception ignored) {}
                });
                dmOwner(guild, ownerId, "🔒 Ton ticket a été fermé par le staff.");
            }

            for (Long roleId : config.getStaffRoleIds()) {
                try {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) ch.getManager().putPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), EnumSet.noneOf(Permission.class)).queue(null, ignored -> {});
                } catch (Exception ignored) {}
            }

            // Bouton de réouverture — sans lui, "ticket_reopen" n'était jamais atteignable
            ch.sendMessage("🔒 Ticket fermé. Salon archivé et masqué pour le demandeur.")
                .setActionRow(Button.success("ticket_reopen", "🔓 Rouvrir le ticket"))
                .queue(null, ignored -> {});
        };

        Category closedCat = guild.getCategoryById(CLOSED_CATEGORY_ID);
        if (closedCat != null) {
            ch.getManager().setParent(closedCat).queue(success -> archive.run(), failure -> archive.run());
        } else {
            archive.run();
        }
    }

    /* ------------------------------ Helpers ------------------------------ */

    /** Vrai si le salon est un ticket actuellement ouvert. */
    private static boolean isOpenTicketName(String name) {
        return name != null && name.regionMatches(true, 0, OPEN_PREFIX, 0, OPEN_PREFIX.length());
    }

    /** Vrai si le salon est un ticket fermé/archivé. */
    private static boolean isClosedTicketName(String name) {
        return name != null && name.regionMatches(true, 0, CLOSED_PREFIX, 0, CLOSED_PREFIX.length());
    }

    /** Extrait l'identifiant du propriétaire depuis le topic ("ticket:<ownerId>[:<raison>]"). */
    private String extractOwnerIdFromTopic(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) return null;
        String rest = topic.substring(TOPIC_PREFIX.length());
        int sep = rest.indexOf(':');
        String ownerId = sep >= 0 ? rest.substring(0, sep) : rest;
        return ownerId.isEmpty() ? null : ownerId;
    }

    /** Extrait la raison depuis le topic ("ticket:<ownerId>:<raison>"), null si absente. */
    private String extractReasonFromTopic(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) return null;
        String rest = topic.substring(TOPIC_PREFIX.length());
        int sep = rest.indexOf(':');
        if (sep < 0 || sep + 1 >= rest.length()) return null;
        return rest.substring(sep + 1);
    }

    /** Construit un nom de salon ticket valide : "<préfixe> <raison> <demandeur>" (max 100 car.). */
    private static String buildTicketName(String prefix, String reason, String ownerName) {
        String name = prefix + " " + sanitizeNamePart(reason) + " " + sanitizeNamePart(ownerName);
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /** Discord impose minuscules/chiffres/tirets dans les noms de salons. */
    private static String sanitizeNamePart(String value) {
        if (value == null) return "inconnu";
        String out = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+|-+$", "");
        return out.isEmpty() ? "inconnu" : out;
    }

    /** Résout le pseudo du demandeur : membre du serveur, sinon nom actuel du salon, sinon "membre". */
    private String resolveOwnerName(Guild guild, String ownerId, String currentName, String reason) {
        if (ownerId != null) {
            try {
                Member owner = guild.getMemberById(ownerId);
                if (owner != null) return owner.getEffectiveName();
            } catch (Exception ignored) {}
        }
        String fromName = extractOwnerNameFromChannelName(currentName, reason);
        return fromName != null ? fromName : "membre";
    }

    /** Extrait la partie "demandeur" d'un nom de salon ticket ("[OPEN] raison demandeur"). */
    private String extractOwnerNameFromChannelName(String name, String reason) {
        if (name == null) return null;
        String n = name.trim();
        for (String prefix : new String[]{OPEN_PREFIX, CLOSED_PREFIX}) {
            if (n.regionMatches(true, 0, prefix, 0, prefix.length())) {
                n = n.substring(prefix.length());
                break;
            }
        }
        n = stripLeadingSeparators(n);
        String sanitizedReason = sanitizeNamePart(reason);
        if (!"inconnu".equals(sanitizedReason) && n.regionMatches(true, 0, sanitizedReason, 0, sanitizedReason.length())) {
            n = stripLeadingSeparators(n.substring(sanitizedReason.length()));
        }
        return n.isEmpty() ? null : n;
    }

    private static String stripLeadingSeparators(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == ' ')) i++;
        return s.substring(i);
    }

    /** Résout le membre demandeur (cache puis API REST — le cache est limité sans GUILD_MEMBERS). */
    private void resolveOwnerMember(Guild guild, String ownerId, Consumer<Member> callback) {
        if (guild == null || ownerId == null || callback == null) return;
        try {
            Member cached = guild.getMemberById(ownerId);
            if (cached != null) {
                callback.accept(cached);
                return;
            }
            guild.retrieveMemberById(ownerId).queue(callback, err -> callback.accept(null));
        } catch (Exception e) {
            callback.accept(null);
        }
    }

    /** Envoie un DM au demandeur, même s'il a quitté le serveur (fallback utilisateur global). */
    private void dmOwner(Guild guild, String ownerId, String message) {
        resolveOwnerMember(guild, ownerId, owner -> {
            if (owner != null) {
                sendDm(owner.getUser(), message);
                return;
            }
            try {
                guild.getJDA().retrieveUserById(ownerId).queue(user -> sendDm(user, message), err -> {});
            } catch (Exception ignored) {}
        });
    }

    private void sendDm(User user, String message) {
        if (user == null || message == null) return;
        try {
            user.openPrivateChannel()
                .flatMap(pc -> pc.sendMessage(message))
                .queue(null, ignored -> {});
        } catch (Exception ignored) {}
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        for (Long id : config.getStaffRoleIds()) {
            if (member.getRoles().stream().anyMatch(r -> r.getIdLong() == id)) return true;
        }
        return false;
    }
}
