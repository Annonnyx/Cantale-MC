package fr.cantale.plugin.discord.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.chatbot.faq.FaqCore;
import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Enregistre et gère les slash commands de modération.
 */
public class SlashCommandManager extends ListenerAdapter {

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private final FaqCore faqCore;
    private final fr.cantale.plugin.discord.commands.GiveawayManager giveawayManager;
    private fr.cantale.plugin.discord.listeners.ModConfigPanelListener modConfigPanel;

    public SlashCommandManager(CantalePlugin plugin, DiscordConfig config, FaqCore faqCore) {
        this.plugin = plugin;
        this.config = config;
        this.faqCore = faqCore;
        this.giveawayManager = new fr.cantale.plugin.discord.commands.GiveawayManager(plugin);
    }

    public void setModConfigPanel(fr.cantale.plugin.discord.listeners.ModConfigPanelListener panel) {
        this.modConfigPanel = panel;
    }

    public void register(Guild guild) {
        guild.updateCommands().addCommands(
            Commands.slash("warn", "Avertir un membre")
                .addOption(OptionType.USER, "utilisateur", "Membre à avertir", true)
                .addOption(OptionType.STRING, "raison", "Raison de l'avertissement", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

            Commands.slash("mute", "Timeout un membre")
                .addOption(OptionType.USER, "utilisateur", "Membre à timeout", true)
                .addOption(OptionType.INTEGER, "durée", "Durée en minutes", true)
                .addOption(OptionType.STRING, "raison", "Raison", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

            Commands.slash("kick", "Expulser un membre")
                .addOption(OptionType.USER, "utilisateur", "Membre à expulser", true)
                .addOption(OptionType.STRING, "raison", "Raison", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS)),

            Commands.slash("ban", "Bannir un membre")
                .addOption(OptionType.USER, "utilisateur", "Membre à bannir", true)
                .addOption(OptionType.STRING, "raison", "Raison", true)
                .addOption(OptionType.INTEGER, "jours", "Jours de messages à supprimer (0-7)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

            Commands.slash("clear", "Supprimer des messages")
                .addOption(OptionType.INTEGER, "nombre", "Nombre de messages (1-100)", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)),

            Commands.slash("infractions", "Voir les sanctions d'un membre")
                .addOption(OptionType.USER, "utilisateur", "Membre à inspecter", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

            Commands.slash("stats", "Stats du serveur Minecraft")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("status", "Statut en temps réel du serveur Minecraft")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("profile", "Stats d'un joueur Minecraft")
                .addOption(OptionType.STRING, "pseudo", "Pseudo du joueur", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("balance", "Afficher son solde Cantox (compte lié requis)")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("bal", "Alias de /balance")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("leaderboard", "Classement des joueurs")
                .addOptions(new OptionData(OptionType.STRING, "categorie", "Catégorie du classement", true)
                    .addChoice("Argent", "argent")
                    .addChoice("Kills", "kills")
                    .addChoice("Temps de jeu", "playtime"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("classement", "Alias de /leaderboard")
                .addOptions(new OptionData(OptionType.STRING, "categorie", "Catégorie du classement", true)
                    .addChoice("Argent", "argent")
                    .addChoice("Kills", "kills")
                    .addChoice("Temps de jeu", "playtime"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("fleaderboard", "Classement des factions")
                .addOptions(new OptionData(OptionType.STRING, "categorie", "Catégorie du classement", true)
                    .addChoice("Argent", "argent")
                    .addChoice("Power", "power"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("fclassement", "Alias de /fleaderboard")
                .addOptions(new OptionData(OptionType.STRING, "categorie", "Catégorie du classement", true)
                    .addChoice("Argent", "argent")
                    .addChoice("Power", "power"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("giveaway", "Lancer un giveaway Discord → récompenses Minecraft")
                .addOption(OptionType.STRING, "temps", "Durée (ex: 10m, 1h, 2h30m)", true)
                .addOption(OptionType.INTEGER, "gagnants", "Nombre de gagnants", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

            Commands.slash("link", "Lier son compte Minecraft")
                .addOption(OptionType.STRING, "code", "Code à 6 caractères généré en jeu avec /link", true)
                .addOption(OptionType.STRING, "pseudo", "Ton pseudo Minecraft exact (avec les majuscules)", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("pay", "Payer un joueur Minecraft en Cantox")
                .addOption(OptionType.STRING, "joueur", "Pseudo du joueur", true)
                .addOption(OptionType.NUMBER, "montant", "Montant en Cantox", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("givevie", "Donner une vie à un joueur Minecraft")
                .addOption(OptionType.STRING, "joueur", "Pseudo du joueur", true)
                .addOption(OptionType.INTEGER, "nombre", "Nombre de vies (1-5)", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("daily", "Récupérer sa récompense quotidienne Cantox (lien MC requis)")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("journalier", "Récupérer sa récompense quotidienne Cantox (lien MC requis)")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("annonce", "Poster une annonce générale sur le serveur")
                .addOption(OptionType.STRING, "message", "Contenu de l'annonce", true)
                .addOption(OptionType.CHANNEL, "salon", "Salon cible (défaut: annonces)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)),

            Commands.slash("partenariat", "Gérer un partenariat (annonce + forum + rôle)")
                .addOption(OptionType.STRING, "message", "Contenu du partenariat", true)
                .addOption(OptionType.STRING, "titre", "Titre du post forum", true)
                .addOption(OptionType.USER, "membre", "Membre à promouvoir (optionnel)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)),

            Commands.slash("miseajour", "Poster une mise à jour du serveur")
                .addOption(OptionType.STRING, "message", "Contenu de la mise à jour", true)
                .addOption(OptionType.CHANNEL, "salon", "Salon cible (défaut: annonces)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)),

            Commands.slash("tournoi", "Annoncer un tournoi sur le serveur")
                .addOption(OptionType.STRING, "message", "Description du tournoi", true)
                .addOption(OptionType.CHANNEL, "salon", "Salon cible (défaut: annonces)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)),

            Commands.slash("event", "Annoncer un événement sur le serveur")
                .addOption(OptionType.STRING, "message", "Description de l'événement", true)
                .addOption(OptionType.CHANNEL, "salon", "Salon cible (défaut: annonces)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)),

            Commands.slash("modconfig", "Ouvrir le panel de configuration de la modération Discord")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

            Commands.slash("msg", "Envoyer un message en jeu à un joueur Minecraft")
                .addOption(OptionType.STRING, "joueur", "Pseudo du joueur Minecraft", true)
                .addOption(OptionType.STRING, "message", "Message à envoyer", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)),

            Commands.slash("say", "[Owner] Faire parler le bot")
                .addOption(OptionType.STRING, "message", "Message à envoyer", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

            Commands.slash("off", "[Owner] Annonce le shutdown et arrête le serveur")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

            Commands.slash("faq-admin", "Gérer la FAQ du bot")
                .addOptions(new OptionData(OptionType.STRING, "action", "Action à effectuer", true)
                    .addChoice("Recharger", "reload")
                    .addChoice("Ajouter", "add")
                    .addChoice("Supprimer", "remove"))
                .addOption(OptionType.STRING, "id", "Identifiant de l'entrée FAQ", true)
                .addOption(OptionType.STRING, "triggers", "Triggers séparés par des virgules (pour add)", false)
                .addOption(OptionType.STRING, "response", "Réponse (pour add)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
        ).queue(
            success -> plugin.getLogger().info("[Discord] Slash commands enregistrés avec succès (" + success.size() + " commandes)"),
            error -> plugin.getLogger().severe("[Discord] Erreur lors de l'enregistrement des slash commands: " + error.getMessage())
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != config.getGuildId()) return;

        Member member = event.getMember();
        if (member == null) return;

        switch (event.getName()) {
            case "warn" -> handleWarn(event, member);
            case "mute" -> handleMute(event, member);
            case "kick" -> handleKick(event, member);
            case "ban" -> handleBan(event, member);
            case "clear" -> handleClear(event, member);
            case "infractions" -> handleInfractions(event, member);
            case "stats", "status" -> handleStats(event);
            case "profile" -> handleProfile(event);
            case "balance", "bal" -> handleBalance(event, member);
            case "leaderboard", "classement" -> handleLeaderboard(event);
            case "fleaderboard", "fclassement" -> handleFactionLeaderboard(event);
            case "giveaway" -> giveawayManager.handleGiveaway(event, member);
            case "link" -> handleLink(event, member);
            case "pay" -> handlePay(event, member);
            case "givevie" -> handleGiveVie(event, member);
            case "annonce" -> handleAnnonce(event, member);
            case "miseajour" -> handleMiseAJour(event, member);
            case "tournoi" -> handleTournoi(event, member);
            case "event" -> handleEvent(event, member);
            case "modconfig" -> {
                if (modConfigPanel != null) modConfigPanel.openPanel(event);
                else event.reply("❌ Panel non initialisé.").setEphemeral(true).queue();
            }
            case "partenariat" -> handlePartenariat(event, member);
            case "msg" -> handleMsg(event, member);
            case "daily", "journalier" -> handleDaily(event, member);
            case "say" -> handleSay(event, member);
            case "off" -> handleOff(event, member);
            case "faq-admin" -> handleFaqAdmin(event, member);
        }
    }

    private static final long OWNER_DISCORD_ID = 1122092101459517481L;

    private boolean isOwner(Member member) {
        return member != null && member.getIdLong() == OWNER_DISCORD_ID;
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        if (isOwner(member)) return true;
        return member.getRoles().stream().anyMatch(r -> config.getStaffRoleIds().contains(r.getIdLong()));
    }

    private void handleFaqAdmin(SlashCommandInteractionEvent event, Member member) {
        if (!isStaff(member)) {
            event.reply("Cette commande est réservée au staff.").setEphemeral(true).queue();
            return;
        }
        String action = event.getOption("action").getAsString();
        String id = event.getOption("id").getAsString().toLowerCase();
        switch (action) {
            case "reload" -> {
                faqCore.reload();
                event.reply("FAQ rechargée (" + faqCore.getEntries().size() + " entrées).").setEphemeral(true).queue();
            }
            case "add" -> {
                if (event.getOption("triggers") == null || event.getOption("response") == null) {
                    event.reply("Triggers et response sont requis pour add.").setEphemeral(true).queue();
                    return;
                }
                String triggersRaw = event.getOption("triggers").getAsString();
                String response = event.getOption("response").getAsString();
                List<String> triggers = Arrays.stream(triggersRaw.split("[,;]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
                if (triggers.isEmpty()) {
                    event.reply("Au moins un trigger est requis.").setEphemeral(true).queue();
                    return;
                }
                File file = new File(plugin.getDataFolder(), "faq.yml");
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                List<Map<String, Object>> entries = loadFaqEntriesMap(yaml);
                entries.removeIf(e -> id.equalsIgnoreCase(String.valueOf(e.get("id"))));
                Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("id", id);
                newEntry.put("triggers", new ArrayList<>(triggers));
                newEntry.put("response", response);
                newEntry.put("enabled", true);
                entries.add(newEntry);
                yaml.set("entries", entries);
                try {
                    yaml.save(file);
                } catch (IOException e) {
                    event.reply("Erreur sauvegarde faq.yml : " + e.getMessage()).setEphemeral(true).queue();
                    return;
                }
                faqCore.reload();
                event.reply("Entrée `" + id + "` ajoutée/rechargée.").setEphemeral(true).queue();
            }
            case "remove" -> {
                File file = new File(plugin.getDataFolder(), "faq.yml");
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                List<Map<String, Object>> entries = loadFaqEntriesMap(yaml);
                boolean removed = entries.removeIf(e -> id.equalsIgnoreCase(String.valueOf(e.get("id"))));
                if (!removed) {
                    event.reply("Aucune entrée trouvée avec l'id `" + id + "`.").setEphemeral(true).queue();
                    return;
                }
                yaml.set("entries", entries);
                try {
                    yaml.save(file);
                } catch (IOException e) {
                    event.reply("Erreur sauvegarde faq.yml : " + e.getMessage()).setEphemeral(true).queue();
                    return;
                }
                faqCore.reload();
                event.reply("Entrée `" + id + "` supprimée.").setEphemeral(true).queue();
            }
            default -> event.reply("Action inconnue : " + action).setEphemeral(true).queue();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadFaqEntriesMap(YamlConfiguration yaml) {
        Object raw = yaml.get("entries");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> map) {
                    Map<String, Object> casted = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        casted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(casted);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private void handleSay(SlashCommandInteractionEvent event, Member member) {
        if (!isOwner(member)) {
            event.reply("❌ Cette commande est réservée au propriétaire.").setEphemeral(true).queue();
            return;
        }
        String message = event.getOption("message").getAsString();
        event.getChannel().sendMessage(message).queue();
        event.reply("✅ Message envoyé.").setEphemeral(true).queue();
    }

    private void handleOff(SlashCommandInteractionEvent event, Member member) {
        if (!isOwner(member)) {
            event.reply("❌ Cette commande est réservée au propriétaire.").setEphemeral(true).queue();
            return;
        }
        event.reply("🔴 Arrêt du serveur Minecraft en cours...").setEphemeral(true).queue();

        Guild guild = event.getGuild();
        if (guild == null) {
            scheduleShutdown();
            return;
        }

        long statusChannelId = config.getMinecraftStatusChannelId();
        long maintenanceChannelId = config.getHostingAnnouncementsChannelId();
        long pingRoleId = config.getServerStatusPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        // Message dans le salon status (joueurs)
        if (statusChannelId > 0) {
            GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, statusChannelId);
            if (channel != null) {
                try {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("🛑 Serveur Minecraft hors-ligne")
                        .setDescription("Le serveur Cantale vient d'être arrêté par <@" + OWNER_DISCORD_ID + ">.")
                        .setTimestamp(Instant.now());
                    channel.sendMessage(ping + "**Le serveur Minecraft est OFF !**")
                        .setEmbeds(embed.build()).complete();
                    plugin.getLogger().info("[Discord] /off : message status envoyé dans " + statusChannelId);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Discord] /off : échec envoi message status : " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("[Discord] /off : salon status " + statusChannelId + " introuvable.");
            }
        }

        // Message dans le salon maintenance (hosting-announcements) si configuré et différent du status
        if (maintenanceChannelId > 0 && maintenanceChannelId != statusChannelId) {
            GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, maintenanceChannelId);
            if (channel != null) {
                try {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.ORANGE)
                        .setTitle("🛠️ Maintenance manuelle — serveur OFF")
                        .setDescription("Le serveur a été arrêté manuellement par <@" + OWNER_DISCORD_ID + ">.")
                        .setTimestamp(Instant.now());
                    channel.sendMessage(ping + "**Le serveur Minecraft est hors-ligne pour maintenance.**")
                        .setEmbeds(embed.build()).complete();
                    plugin.getLogger().info("[Discord] /off : message maintenance envoyé dans " + maintenanceChannelId);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Discord] /off : échec envoi message maintenance : " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("[Discord] /off : salon maintenance " + maintenanceChannelId + " introuvable.");
            }
        }

        scheduleShutdown();
    }

    private void scheduleShutdown() {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> org.bukkit.Bukkit.shutdown());
    }

    @Override
    public void onStringSelectInteraction(@NotNull net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent event) {
        giveawayManager.handleSelect(event);
    }

    private void handlePartenariat(SlashCommandInteractionEvent event, Member moderator) {
        String message = event.getOption("message").getAsString();
        String titre = event.getOption("titre").getAsString();
        Member target = event.getOption("membre") != null ? event.getOption("membre").getAsMember() : null;

        Guild guild = event.getGuild();
        if (guild == null) return;

        long announceChannelId = config.getPartnershipsChannelId();
        long pingRoleId = config.getPartnerRoleId();
        long forumId = config.getPartnershipsChannelId();
        long partnerRoleId = config.getPartnerRoleId();

        // Send announcement with role mention
        TextChannel announce = guild.getTextChannelById(announceChannelId);
        if (announce != null) {
            EmbedBuilder announceEmbed = cantaleAnnounceEmbed(guild, "Partenariat", message, moderator, "Publié par");
            announce.sendMessage("<@&" + pingRoleId + "> **Nouveau partenariat Cantale !**")
                .setEmbeds(announceEmbed.build()).queue();
        }

        // Create forum post (if forum channel exists)
        try {
            var forum = guild.getForumChannelById(forumId);
            if (forum != null) {
                EmbedBuilder forumEmbed = cantaleAnnounceEmbed(guild, "Partenariat", message, moderator, "Publié par");
                var msgData = new net.dv8tion.jda.api.utils.messages.MessageCreateBuilder().setEmbeds(forumEmbed.build()).build();
                forum.createForumPost(titre, msgData).queue();
            }
        } catch (Exception ignored) {}

        // Give partner role if member provided
        if (target != null) {
            Role partnerRole = guild.getRoleById(partnerRoleId);
            if (partnerRole != null) {
                guild.addRoleToMember(target, partnerRole).queue();
            }
        }

        event.reply("✅ Partenariat annoncé.").setEphemeral(true).queue();
    }

    private void handleWarn(SlashCommandInteractionEvent event, Member moderator) {
        User target = event.getOption("utilisateur").getAsUser();
        String reason = event.getOption("raison").getAsString();

        logModAction(event.getGuild(), "⚠️ Avertissement", Color.YELLOW,
            "**Utilisateur:** " + target.getAsMention() + "\n**Raison:** " + reason + "\n**Modérateur:** " + moderator.getAsMention(),
            target, moderator);

        event.reply("⚠️ " + target.getAsMention() + " a été averti : " + reason).setEphemeral(true).queue();

        target.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage("⚠️ **Avertissement** sur Cantale Discord\n**Raison:** " + reason + "\n**Modérateur:** " + moderator.getEffectiveName()))
            .queue(null, failure -> {});
    }

    private void handleMute(SlashCommandInteractionEvent event, Member moderator) {
        Member target = event.getOption("utilisateur").getAsMember();
        int minutes = event.getOption("durée").getAsInt();
        String reason = event.getOption("raison").getAsString();

        if (target != null) {
            target.timeoutFor(minutes, TimeUnit.MINUTES).reason(reason).queue();

            logModAction(event.getGuild(), "⏱️ Timeout", Color.ORANGE,
                "**Utilisateur:** " + target.getAsMention() + "\n**Durée:** " + minutes + " min\n**Raison:** " + reason + "\n**Modérateur:** " + moderator.getAsMention(),
                target.getUser(), moderator);
        }

        event.reply("⏱️ " + target.getAsMention() + " timeout pendant " + minutes + " min.").setEphemeral(true).queue();
    }

    private void handleKick(SlashCommandInteractionEvent event, Member moderator) {
        Member target = event.getOption("utilisateur").getAsMember();
        String reason = event.getOption("raison").getAsString();

        if (target != null) {
            target.kick().reason(reason).queue();

            logModAction(event.getGuild(), "👢 Expulsion", Color.RED,
                "**Utilisateur:** " + target.getAsMention() + "\n**Raison:** " + reason + "\n**Modérateur:** " + moderator.getAsMention(),
                target.getUser(), moderator);
        }

        event.reply("👢 " + target.getAsMention() + " a été expulsé.").setEphemeral(true).queue();
    }

    private void handleBan(SlashCommandInteractionEvent event, Member moderator) {
        User target = event.getOption("utilisateur").getAsUser();
        String reason = event.getOption("raison").getAsString();
        int days = event.getOption("jours") != null ? event.getOption("jours").getAsInt() : 0;
        days = Math.min(7, Math.max(0, days));

        event.getGuild().ban(target, days, TimeUnit.DAYS).reason(reason).queue();

        logModAction(event.getGuild(), "🔨 Bannissement", Color.RED,
            "**Utilisateur:** " + target.getAsMention() + "\n**Raison:** " + reason + "\n**Modérateur:** " + moderator.getAsMention() + (days > 0 ? "\n**Purge:** " + days + " jours" : ""),
            target, moderator);

        event.reply("🔨 " + target.getAsMention() + " a été banni.").setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event, Member moderator) {
        int amount = event.getOption("nombre").getAsInt();
        amount = Math.min(100, Math.max(1, amount));

        TextChannel channel = event.getChannel().asTextChannel();
        channel.getIterableHistory()
            .takeAsync(amount)
            .thenAccept(messages -> {
                channel.deleteMessages(messages).queue(
                    success -> {
                        event.reply("🗑️ " + messages.size() + " messages supprimés.").setEphemeral(true).queue();
                        logModAction(event.getGuild(), "🗑️ Clear messages", Color.GRAY,
                            "**Salon:** " + channel.getAsMention() + "\n**Nombre:** " + messages.size() + "\n**Modérateur:** " + moderator.getAsMention(),
                            null, moderator);
                    },
                    error -> event.reply("❌ Erreur lors de la suppression.").setEphemeral(true).queue()
                );
            });
    }

    private void handleInfractions(SlashCommandInteractionEvent event, Member moderator) {
        User target = event.getOption("utilisateur").getAsUser();
        // Pour l'instant, simple embed (sans vraie DB d'infractions)
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("📋 Infractions — " + target.getName())
            .setDescription("Pas d'historique d'infractions enregistré (feature à venir).")
            .setFooter("Demandé par " + moderator.getEffectiveName(), null);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        // Récupérer les stats depuis le thread principal (Bukkit)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            java.util.Map<String, Object> stats = plugin.getServerOptimizer().getStats();

            double tps = ((Number) stats.getOrDefault("tps", 20.0)).doubleValue();
            int online = ((Number) stats.getOrDefault("online_players", 0)).intValue();
            int max = ((Number) stats.getOrDefault("max_players", 0)).intValue();
            long usedMb = ((Number) stats.getOrDefault("used_memory_mb", 0)).longValue();
            long maxMb = ((Number) stats.getOrDefault("max_memory_mb", 0)).longValue();
            int chunks = ((Number) stats.getOrDefault("loaded_chunks", 0)).intValue();
            int entities = ((Number) stats.getOrDefault("total_entities", 0)).intValue();

            // Uptime serveur
            long uptimeMs = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
            long upDays = uptimeMs / 86_400_000L;
            long upHours = (uptimeMs % 86_400_000L) / 3_600_000L;
            long upMin = (uptimeMs % 3_600_000L) / 60_000L;
            String uptime = (upDays > 0 ? upDays + "j " : "") + upHours + "h " + upMin + "min";

            String version = org.bukkit.Bukkit.getBukkitVersion().split("-")[0];

            // Couleur et icône selon TPS
            Color color;
            String tpsIcon;
            String tpsStatus;
            if (tps >= 19.0) { color = new Color(0x2ECC71); tpsIcon = "🟢"; tpsStatus = "Excellent"; }
            else if (tps >= 17.0) { color = new Color(0xF1C40F); tpsIcon = "🟡"; tpsStatus = "Stable"; }
            else if (tps >= 14.0) { color = new Color(0xE67E22); tpsIcon = "🟠"; tpsStatus = "Lag léger"; }
            else { color = new Color(0xE74C3C); tpsIcon = "🔴"; tpsStatus = "Surchargé"; }

            String onlineIcon = online > 0 ? "🟢" : "⚫";

            EmbedBuilder embed = new EmbedBuilder()
                .setColor(color)
                .setTitle("📊 Statut Cantale — Live")
                    .setDescription("**" + onlineIcon + " " + (online > 0 ? "En ligne" : "Personne connecté") + "**\n"
                        + "Connecte-toi sur `play.cantale.world` pour jouer !\n\n*Actualisé toutes les 10s pendant 30min.*")
                .addField("👥 Joueurs", "`" + online + " / " + max + "`", true)
                .addField(tpsIcon + " TPS", "`" + String.format("%.2f", Math.min(tps, 20.0)) + "` " + tpsStatus, true)
                .addField("⏱ Uptime", uptime, true)
                .addField("🌐 IP publique", "`play.cantale.world`", true)
                .addField("🎮 Version", version, true)
                .addField("💾 RAM", usedMb + " / " + maxMb + " MB", true)
                .addField("📦 Chunks chargés", String.valueOf(chunks), true)
                .addField("🐾 Entités", String.valueOf(entities), true)
                .addField("🛡 Optimisation", plugin.getServerOptimizer().isEnabled() ? "✅ Active" : "❌ Désactivée", true)
                .setFooter("Cantale — Trois vies. C'est tout.", null)
                .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(embed.build()).queue(message -> {
                if (plugin.getDiscordBot() != null) {
                    plugin.getDiscordBot().registerLiveStatsMessage(message.getChannel().getIdLong(), message.getIdLong());
                }
            });
        });
    }

    private void handleProfile(SlashCommandInteractionEvent event) {
        String name = event.getOption("pseudo").getAsString();
        event.deferReply().queue();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "SELECT lives, balance, kills, deaths FROM players WHERE LOWER(username) = LOWER(?)")) {
                stmt.setString(1, name);
                java.sql.ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int lives = rs.getInt("lives");
                    double balance = rs.getDouble("balance");
                    int kills = rs.getInt("kills");
                    int deaths = rs.getInt("deaths");
                    double kd = deaths > 0 ? (double) kills / deaths : kills;

                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.BLUE)
                        .setTitle("🎮 Profil — " + name)
                        .addField("❤️ Vies", String.valueOf(lives), true)
                        .addField("💰 Cantox", String.format("%.0f", balance), true)
                        .addField("⚔️ Kills", String.valueOf(kills), true)
                        .addField("💀 Deaths", String.valueOf(deaths), true)
                        .addField("📊 K/D", String.format("%.2f", kd), true)
                        .setTimestamp(Instant.now());
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginalEmbeds(embed.build()).queue());
                } else {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Joueur non trouvé.").queue());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /profile error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Erreur lors de la récupération du profil.").queue());
            }
        });
    }

    private void handleBalance(SlashCommandInteractionEvent event, Member member) {
        if (!hasJoueurRole(member)) {
            event.reply("❌ Tu dois lier ton compte Minecraft avec `/link` pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }
        String discordId = member.getId();
        event.deferReply(true).queue();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                String uuid = getUuidFromDiscordId(conn, discordId);
                if (uuid == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Ton compte n'est pas lié. Fais `/link` d'abord.").queue());
                    return;
                }

                String username = getPlayerNameFromUuid(conn, uuid);
                double balance;
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
                    stmt.setString(1, uuid);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Joueur introuvable.").queue());
                        return;
                    }
                    balance = rs.getDouble("balance");
                }

                double finalBalance = balance;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("💰 Solde Cantox")
                        .setDescription("**" + (username != null ? username : "Inconnu") + "** : **" + String.format("%.0f", finalBalance) + "** Cantox")
                        .setTimestamp(Instant.now());
                    event.getHook().editOriginalEmbeds(embed.build()).queue();
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /balance error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Erreur lors de la récupération du solde.").queue());
            }
        });
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        String category = event.getOption("categorie").getAsString().toLowerCase();
        event.deferReply().queue();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String column = switch (category) {
                case "kills" -> "kills";
                case "playtime" -> "playtime";
                default -> "balance";
            };
            String title = switch (category) {
                case "kills" -> "⚔️ Classement — Kills";
                case "playtime" -> "⏱️ Classement — Temps de jeu";
                default -> "💰 Classement — Argent";
            };
            java.awt.Color color = switch (category) {
                case "kills" -> Color.RED;
                case "playtime" -> Color.CYAN;
                default -> Color.GREEN;
            };

            String sql = "SELECT username, " + column + " FROM players ORDER BY " + column + " DESC LIMIT 10";
            java.util.List<String> lines = new java.util.ArrayList<>();
            String[] medals = {"🥇", "🥈", "🥉", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};

            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    String name = rs.getString("username");
                    double value = rs.getDouble(column);
                    String display;
                    if (category.equals("playtime")) {
                        long seconds = (long) value;
                        long hours = seconds / 3600;
                        long minutes = (seconds % 3600) / 60;
                        display = hours + "h " + minutes + "min";
                    } else {
                        display = String.format("%.0f", value);
                    }
                    lines.add(medals[rank] + " **" + name + "** — " + display);
                    rank++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /leaderboard error: " + e.getMessage());
            }

            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(color)
                    .setTitle(title)
                    .setDescription(lines.isEmpty() ? "Aucune donnée disponible." : String.join("\n", lines))
                    .setFooter("Top 10 — Cantale", null)
                    .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            });
        });
    }

    private void handleFactionLeaderboard(SlashCommandInteractionEvent event) {
        String category = event.getOption("categorie").getAsString().toLowerCase();
        event.deferReply().queue();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String column = switch (category) {
                case "power" -> "power";
                default -> "balance";
            };
            String title = switch (category) {
                case "power" -> "⚡ Classement Factions — Power";
                default -> "💰 Classement Factions — Argent";
            };
            java.awt.Color color = switch (category) {
                case "power" -> Color.ORANGE;
                default -> Color.GREEN;
            };

            String sql = "SELECT name, tag, " + column + " FROM factions ORDER BY " + column + " DESC LIMIT 10";
            java.util.List<String> lines = new java.util.ArrayList<>();
            String[] medals = {"🥇", "🥈", "🥉", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};

            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    String name = rs.getString("name");
                    String tag = rs.getString("tag");
                    double value = rs.getDouble(column);
                    lines.add(medals[rank] + " **" + name + "** [" + tag + "] — " + String.format("%.0f", value));
                    rank++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /fleaderboard error: " + e.getMessage());
            }

            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(color)
                    .setTitle(title)
                    .setDescription(lines.isEmpty() ? "Aucune faction disponible." : String.join("\n", lines))
                    .setFooter("Top 10 — Cantale", null)
                    .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            });
        });
    }

    private void handleLink(SlashCommandInteractionEvent event, Member member) {
        String code = event.getOption("code").getAsString().toUpperCase();
        String pseudo = event.getOption("pseudo").getAsString();
        String discordId = member.getId();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                // Vérifier code + pseudo
                String uuid = null;
                String storedName = null;
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT uuid, player_name FROM link_codes WHERE code = ? AND used = 0 AND expires_at > ?")) {
                    stmt.setString(1, code);
                    stmt.setLong(2, System.currentTimeMillis() / 1000);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        uuid = rs.getString("uuid");
                        storedName = rs.getString("player_name");
                    }
                }

                if (uuid == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Code invalide ou expiré. Fais `/link` en jeu pour générer un nouveau code.").setEphemeral(true).queue());
                    return;
                }

                final String finalStoredName = storedName;
                if (finalStoredName != null && !finalStoredName.equalsIgnoreCase(pseudo)) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply(
                        "❌ Le pseudo **" + pseudo + "** ne correspond pas au compte lié au code.\n" +
                        "Pseudo attendu : **" + finalStoredName + "**").setEphemeral(true).queue());
                    return;
                }

                // Marquer code comme utilisé
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE link_codes SET used = 1 WHERE code = ?")) {
                    stmt.setString(1, code);
                    stmt.executeUpdate();
                }

                // Supprimer ancienne liaison si existe
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM discord_links WHERE uuid = ? OR discord_id = ?")) {
                    stmt.setString(1, uuid);
                    stmt.setString(2, discordId);
                    stmt.executeUpdate();
                }

                // Créer liaison
                String discordUsername = member.getUser().getName();
                String discordGlobalName = member.getUser().getGlobalName();
                String discordDisplayName = discordGlobalName != null ? discordGlobalName : member.getEffectiveName();
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO discord_links (discord_id, uuid, linked_at, discord_username, discord_display_name) VALUES (?, ?, ?, ?, ?)")) {
                    stmt.setString(1, discordId);
                    stmt.setString(2, uuid);
                    stmt.setLong(3, System.currentTimeMillis() / 1000);
                    stmt.setString(4, discordUsername);
                    stmt.setString(5, discordDisplayName);
                    stmt.executeUpdate();
                }

                // Attribuer rôle Joueur
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    Guild guild = event.getGuild();
                    if (guild != null) {
                        Role joueurRole = guild.getRoleById(1504909677786300577L);
                        if (joueurRole != null) {
                            guild.addRoleToMember(member, joueurRole).queue(
                                success -> {},
                                error -> plugin.getLogger().warning("[Discord] Impossible d'attribuer le rôle Joueur: " + error.getMessage())
                            );
                        }
                    }
                    event.reply("✅ Compte lié avec succès ! Le rôle <@&1504909677786300577> t'a été attribué.").setEphemeral(true).queue();
                });

            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /link error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Erreur lors de la liaison.").setEphemeral(true).queue());
            }
        });
    }

    private void handlePay(SlashCommandInteractionEvent event, Member member) {
        if (!hasJoueurRole(member)) {
            event.reply("❌ Tu dois lier ton compte Minecraft avec `/link` pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }
        String targetName = event.getOption("joueur").getAsString();
        double amount = event.getOption("montant").getAsDouble();
        if (amount <= 0) {
            event.reply("❌ Montant invalide.").setEphemeral(true).queue();
            return;
        }
        String discordId = member.getId();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                // Récupérer UUID du sender
                String senderUuid = getUuidFromDiscordId(conn, discordId);
                if (senderUuid == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Ton compte n'est pas lié. Fais `/link` d'abord.").setEphemeral(true).queue());
                    return;
                }

                // Vérifier solde
                double balance;
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
                    stmt.setString(1, senderUuid);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) { org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Joueur introuvable.").setEphemeral(true).queue()); return; }
                    balance = rs.getDouble("balance");
                }
                if (balance < amount) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Tu n'as pas assez de Cantox. Solde: " + String.format("%.0f", balance)).setEphemeral(true).queue());
                    return;
                }

                // Récupérer UUID du target
                final String[] targetUuidHolder = new String[1];
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM players WHERE username = ? COLLATE utf8mb4_general_ci")) {
                    stmt.setString(1, targetName);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (rs.next()) targetUuidHolder[0] = rs.getString("uuid");
                }
                if (targetUuidHolder[0] == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Joueur cible non trouvé.").setEphemeral(true).queue());
                    return;
                }
                if (targetUuidHolder[0].equals(senderUuid)) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Tu ne peux pas te payer toi-même.").setEphemeral(true).queue());
                    return;
                }

                // Transfert
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("UPDATE players SET balance = balance - ? WHERE uuid = ?")) {
                    stmt.setDouble(1, amount); stmt.setString(2, senderUuid); stmt.executeUpdate();
                }
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("UPDATE players SET balance = balance + ? WHERE uuid = ?")) {
                    stmt.setDouble(1, amount); stmt.setString(2, targetUuidHolder[0]); stmt.executeUpdate();
                }

                // Notifier target si en ligne
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(targetUuidHolder[0]));
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().sendMessage("§8[§6Cantale§8] §e" + member.getEffectiveName() + " §7(Discord) t'a payé §6" + String.format("%.0f", amount) + " §7Cantox !");
                    }
                    event.reply("✅ Tu as payé **" + targetName + "** : **" + String.format("%.0f", amount) + "** Cantox !").setEphemeral(true).queue();
                });

            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /pay error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Erreur lors du transfert.").setEphemeral(true).queue());
            }
        });
    }

    private void handleGiveVie(SlashCommandInteractionEvent event, Member member) {
        if (!hasJoueurRole(member)) {
            event.reply("❌ Tu dois lier ton compte Minecraft avec `/link` pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }
        String targetName = event.getOption("joueur").getAsString();
        int amount = event.getOption("nombre").getAsInt();
        if (amount < 1 || amount > 5) {
            event.reply("❌ Nombre de vies invalide (1-5).").setEphemeral(true).queue();
            return;
        }
        String discordId = member.getId();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                String senderUuid = getUuidFromDiscordId(conn, discordId);
                if (senderUuid == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Ton compte n'est pas lié. Fais `/link` d'abord.").setEphemeral(true).queue());
                    return;
                }

                // Vérifier vies du sender
                int senderLives;
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT lives FROM players WHERE uuid = ?")) {
                    stmt.setString(1, senderUuid);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) { org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Joueur introuvable.").setEphemeral(true).queue()); return; }
                    senderLives = rs.getInt("lives");
                }
                if (senderLives <= amount) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Tu n'as pas assez de vies. Vies actuelles: " + senderLives).setEphemeral(true).queue());
                    return;
                }

                final String[] targetUuidHolder = new String[1];
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM players WHERE username = ? COLLATE utf8mb4_general_ci")) {
                    stmt.setString(1, targetName);
                    java.sql.ResultSet rs = stmt.executeQuery();
                    if (rs.next()) targetUuidHolder[0] = rs.getString("uuid");
                }
                if (targetUuidHolder[0] == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Joueur cible non trouvé.").setEphemeral(true).queue());
                    return;
                }
                if (targetUuidHolder[0].equals(senderUuid)) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Tu ne peux pas te donner des vies à toi-même.").setEphemeral(true).queue());
                    return;
                }

                // Transfert vies
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("UPDATE players SET lives = lives - ? WHERE uuid = ?")) {
                    stmt.setInt(1, amount); stmt.setString(2, senderUuid); stmt.executeUpdate();
                }
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("UPDATE players SET lives = lives + ? WHERE uuid = ?")) {
                    stmt.setInt(1, amount); stmt.setString(2, targetUuidHolder[0]); stmt.executeUpdate();
                }

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(targetUuidHolder[0]));
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().sendMessage("§8[§6Cantale§8] §e" + member.getEffectiveName() + " §7(Discord) t'a donné §6" + amount + " §7vie(s) !");
                    }
                    event.reply("✅ Tu as donné **" + amount + "** vie(s) à **" + targetName + "** !").setEphemeral(true).queue();
                });

            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /givevie error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.reply("❌ Erreur lors du transfert.").setEphemeral(true).queue());
            }
        });
    }

    private String getUuidFromDiscordId(java.sql.Connection conn, String discordId) throws java.sql.SQLException {
        try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM discord_links WHERE discord_id = ?")) {
            stmt.setString(1, discordId);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("uuid");
        }
        return null;
    }

    private String getPlayerNameFromUuid(java.sql.Connection conn, String uuid) throws java.sql.SQLException {
        try (java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT username FROM players WHERE uuid = ?")) {
            stmt.setString(1, uuid);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("username");
        }
        return null;
    }

    private void handleDaily(SlashCommandInteractionEvent event, Member member) {
        if (!hasJoueurRole(member)) {
            event.reply("❌ Tu dois lier ton compte Minecraft avec `/link` pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }

        String discordId = member.getId();
        event.deferReply(true).queue();

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                String uuid = getUuidFromDiscordId(conn, discordId);
                if (uuid == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Ton compte n'est pas lié. Fais `/link` d'abord.").queue());
                    return;
                }

                int granted = plugin.getRankManager().grantDailyCantox(java.util.UUID.fromString(uuid), 1000);
                if (granted <= 0) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Tu as déjà récupéré ta récompense quotidienne aujourd'hui. Reviens demain !").queue());
                    return;
                }

                String username = getPlayerNameFromUuid(conn, uuid);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("💰 Récompense quotidienne")
                        .setDescription("Tu as reçu **" + granted + "** Cantox !")
                        .setFooter("Compte lié: " + (username != null ? username : "inconnu"), null)
                        .setTimestamp(Instant.now());
                    event.getHook().editOriginalEmbeds(embed.build()).queue();

                    // Notifier le joueur en jeu s'il est en ligne
                    org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        offlinePlayer.getPlayer().sendMessage("§8[§6Cantale§8] §aTa récompense quotidienne a été récupérée depuis Discord : §e" + granted + " Cantox§a.");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] /daily error: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> event.getHook().editOriginal("❌ Erreur lors de la récupération de la récompense.").queue());
            }
        });
    }

    private boolean hasJoueurRole(Member member) {
        if (member == null) return false;
        return member.getRoles().stream().anyMatch(r -> r.getIdLong() == 1504909677786300577L);
    }


    private void logModAction(Guild guild, String title, Color color, String description, User target, Member moderator) {
        TextChannel logs = guild.getTextChannelById(config.getModLogsChannelId());
        if (logs == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now());

        if (target != null) {
            embed.setFooter("ID cible: " + target.getId(), target.getEffectiveAvatarUrl());
        }

        logs.sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Embed unifié pour toutes les commandes d'annonce (annonce, mise à jour, tournoi,
     * événement, partenariat) — même couleur, même style de titre, même footer,
     * pour que tous les messages publics du bot aient un aspect cohérent.
     */
    private EmbedBuilder cantaleAnnounceEmbed(Guild guild, String typeLabel, String message, Member author, String footerVerb) {
        // Le champ STRING d'une slash command est un champ mono-ligne : Entrée envoie la
        // commande au lieu d'insérer un saut de ligne. Le staff tape donc "\n" à la main
        // pour aérer son texte — sans cette conversion, ça s'affichait littéralement comme
        // "\n" dans l'embed au lieu d'un vrai retour à la ligne.
        String formatted = message.replace("\\n", "\n");

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0x1A1A1A))
            .setTitle("⬡ " + typeLabel.toUpperCase() + " ⬡")
            .setDescription(formatted)
            .setFooter("Cantale Network · " + footerVerb + " " + author.getEffectiveName(), author.getEffectiveAvatarUrl())
            .setTimestamp(Instant.now());
        if (guild != null && guild.getIconUrl() != null) {
            embed.setThumbnail(guild.getIconUrl());
        }
        return embed;
    }

    private void handleAnnonce(SlashCommandInteractionEvent event, Member member) {
        String message = event.getOption("message").getAsString();

        net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel;
        if (event.getOption("salon") != null) {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getOption("salon").getAsChannel();
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Le salon sélectionné n'est pas un salon texte.").setEphemeral(true).queue();
                return;
            }
        } else {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getGuild().getGuildChannelById(config.getAnnouncementsChannelId());
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Salon d'annonces non configuré ou introuvable.").setEphemeral(true).queue();
                return;
            }
        }

        long pingRoleId = config.getAnnouncementsPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        EmbedBuilder embed = cantaleAnnounceEmbed(event.getGuild(), "Annonce", message, member, "Publié par");

        channel.sendMessage(ping + "**Nouvelle annonce du serveur Cantale !**")
            .setEmbeds(embed.build()).queue();

        event.reply("✅ Annonce publiée dans " + channel.getAsMention()).setEphemeral(true).queue();
    }

    private void handleMiseAJour(SlashCommandInteractionEvent event, Member member) {
        String message = event.getOption("message").getAsString();

        net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel;
        if (event.getOption("salon") != null) {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getOption("salon").getAsChannel();
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Le salon sélectionné n'est pas un salon texte.").setEphemeral(true).queue();
                return;
            }
        } else {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getGuild().getGuildChannelById(config.getAnnouncementsChannelId());
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Salon d'annonces non configuré ou introuvable.").setEphemeral(true).queue();
                return;
            }
        }

        long pingRoleId = config.getUpdatePingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        EmbedBuilder embed = cantaleAnnounceEmbed(event.getGuild(), "Mise à jour", message, member, "Publié par");

        channel.sendMessage(ping + "**Nouvelle mise à jour du serveur !**")
            .setEmbeds(embed.build()).queue();

        event.reply("✅ Mise à jour publiée dans " + channel.getAsMention()).setEphemeral(true).queue();
    }

    private void handleTournoi(SlashCommandInteractionEvent event, Member member) {
        String message = event.getOption("message").getAsString();

        net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel;
        if (event.getOption("salon") != null) {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getOption("salon").getAsChannel();
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Le salon sélectionné n'est pas un salon texte.").setEphemeral(true).queue();
                return;
            }
        } else {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getGuild().getGuildChannelById(config.getAnnouncementsChannelId());
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Salon d'annonces non configuré ou introuvable.").setEphemeral(true).queue();
                return;
            }
        }

        long pingRoleId = config.getTournamentPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        EmbedBuilder embed = cantaleAnnounceEmbed(event.getGuild(), "Tournoi", message, member, "Organisé par");

        channel.sendMessage(ping + "**Un tournoi est organisé sur Cantale !**")
            .setEmbeds(embed.build()).queue();

        event.reply("✅ Tournoi annoncé dans " + channel.getAsMention()).setEphemeral(true).queue();
    }

    private void handleEvent(SlashCommandInteractionEvent event, Member member) {
        String message = event.getOption("message").getAsString();

        net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel;
        if (event.getOption("salon") != null) {
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getOption("salon").getAsChannel();
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Le salon sélectionné n'est pas un salon texte.").setEphemeral(true).queue();
                return;
            }
        } else {
            long eventsChannelId = config.getEventsChannelId();
            net.dv8tion.jda.api.entities.channel.Channel ch = event.getGuild().getGuildChannelById(eventsChannelId);
            if (ch instanceof net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) {
                channel = (net.dv8tion.jda.api.entities.channel.middleman.MessageChannel) ch;
            } else {
                event.reply("❌ Salon événements non configuré ou introuvable.").setEphemeral(true).queue();
                return;
            }
        }

        long pingRoleId = config.getEventPingRoleId();
        String ping = pingRoleId > 0 ? "<@&" + pingRoleId + "> " : "";

        EmbedBuilder embed = cantaleAnnounceEmbed(event.getGuild(), "Événement", message, member, "Organisé par");

        channel.sendMessage(ping + "**Un événement a lieu sur Cantale !**")
            .setEmbeds(embed.build()).queue();

        event.reply("✅ Événement annoncé dans " + channel.getAsMention()).setEphemeral(true).queue();
    }

    // ─── Discord /msg → Minecraft ───

    private void handleMsg(SlashCommandInteractionEvent event, Member member) {
        String targetName = event.getOption("joueur").getAsString();
        String message = event.getOption("message").getAsString();

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                event.reply("❌ Le joueur **" + targetName + "** n'est pas en ligne.").setEphemeral(true).queue();
                return;
            }

            String senderName = member.getEffectiveName();
            target.sendMessage("§8[§9Discord§8] §e" + senderName + " §7→ §fVous : §r" + message);

            // Ajouter un bouton pour répondre en jeu
            target.sendMessage("§8[§9Discord§8] §7Répondez avec §e/discord " + senderName + " <message>");

            event.reply("✅ Message envoyé à **" + target.getName() + "** en jeu !").setEphemeral(true).queue();
        });
    }

    // ─── Bouton "Répondre" depuis les MP Discord ───

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (giveawayManager.handlesButton(event)) {
            giveawayManager.handleButton(event);
            return;
        }

        String componentId = event.getComponentId();
        if (!componentId.startsWith("mc_reply:")) return;

        String targetPlayerName = componentId.substring("mc_reply:".length());

        // Ouvrir un Modal pour saisir le message
        TextInput messageInput = TextInput.create("reply_message", "Message", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Votre réponse...")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(500)
            .build();

        Modal modal = Modal.create("mc_reply_modal:" + targetPlayerName, "Répondre à " + targetPlayerName)
            .addActionRow(messageInput)
            .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (giveawayManager.handlesModal(event)) {
            giveawayManager.handleModal(event);
            return;
        }

        String modalId = event.getModalId();
        if (!modalId.startsWith("mc_reply_modal:")) return;

        String targetPlayerName = modalId.substring("mc_reply_modal:".length());
        String message = event.getValue("reply_message").getAsString();
        String senderName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(targetPlayerName);
            if (target == null || !target.isOnline()) {
                event.reply("❌ Le joueur **" + targetPlayerName + "** n'est plus en ligne.").setEphemeral(true).queue();
                return;
            }

            target.sendMessage("§8[§9Discord§8] §e" + senderName + " §7→ §fVous : §r" + message);
            event.reply("✅ Réponse envoyée à **" + target.getName() + "** !").setEphemeral(true).queue();
        });
    }
}
