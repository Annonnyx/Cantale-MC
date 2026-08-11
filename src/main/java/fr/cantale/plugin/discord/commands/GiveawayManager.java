package fr.cantale.plugin.discord.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.rank.PlayerRank;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gestion des giveaways Discord / Minecraft.
 * Permet de configurer un giveaway via menus déroulants et modals, puis de publier
 * un message public avec participation via bouton. Les récompenses sont stockées
 * dans la table pending_rewards pour distribution à la connexion du gagnant.
 */
public class GiveawayManager {

    private final CantalePlugin plugin;
    private final Map<String, GiveawayBuilder> builders = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> participants = new ConcurrentHashMap<>();
    /** Giveaways publiés en cours, indexés par ID du message Discord. */
    private final Map<String, ActiveGiveaway> activeGiveaways = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public GiveawayManager(CantalePlugin plugin) {
        this.plugin = plugin;
        // L'état des giveaways est en mémoire : un redémarrage perd les clôtures
        // planifiées sans récupération possible. On le signale au démarrage si besoin.
        if (!activeGiveaways.isEmpty()) {
            plugin.getLogger().warning("[Discord] " + activeGiveaways.size()
                + " giveaway(s) actif(s) en mémoire au démarrage : leurs clôtures planifiées"
                + " ont été perdues et devront être effectuées manuellement.");
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Slash command /giveaway
    // ───────────────────────────────────────────────────────────────────────────

    public void handleGiveaway(SlashCommandInteractionEvent event, Member member) {
        String durationStr = event.getOption("temps").getAsString();
        int winners = event.getOption("gagnants").getAsInt();
        long durationSeconds = parseDuration(durationStr);

        if (durationSeconds <= 0) {
            event.reply("❌ Durée invalide. Utilise un format comme `10m`, `1h` ou `2h30m`.").setEphemeral(true).queue();
            return;
        }
        if (winners < 1) {
            event.reply("❌ Le nombre de gagnants doit être d'au moins 1.").setEphemeral(true).queue();
            return;
        }

        String memberId = member.getId();
        GiveawayBuilder builder = new GiveawayBuilder(memberId, durationSeconds, winners);
        builders.put(memberId, builder);

        event.replyEmbeds(configEmbed(builder))
            .addComponents(
                ActionRow.of(rewardSelectMenu()),
                ActionRow.of(Button.success("giveaway:launch:" + memberId, "🚀 Lancer"), Button.danger("giveaway:cancel:" + memberId, "❌ Annuler"))
            )
            .setEphemeral(true)
            .queue();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Menus déroulants et boutons
    // ───────────────────────────────────────────────────────────────────────────

    public boolean handlesButton(ButtonInteractionEvent event) {
        return event.getComponentId().startsWith("giveaway:");
    }

    public boolean handlesModal(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        return modalId.startsWith("giveaway:cantox:") || modalId.startsWith("giveaway:other:");
    }

    public void handleSelect(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("giveaway:reward:")) {
            handleRewardTypeSelect(event);
        } else if (componentId.startsWith("giveaway:rank:")) {
            handleRankSelect(event);
        } else if (componentId.startsWith("giveaway:itemcat:")) {
            handleItemCategorySelect(event);
        } else if (componentId.startsWith("giveaway:item:")) {
            handleItemSelect(event);
        }
    }

    private void handleRewardTypeSelect(StringSelectInteractionEvent event) {
        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.get(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway en cours de configuration.").setEphemeral(true).queue();
            return;
        }

        String selected = event.getValues().get(0);
        if ("cantox".equals(selected)) {
            TextInput input = TextInput.create("giveaway:cantox_amount", "Montant en Cantox", TextInputStyle.SHORT)
                .setPlaceholder("5000")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(12)
                .build();
            Modal modal = Modal.create("giveaway:cantox:" + memberId, "Récompense : Cantox")
                .addActionRow(input)
                .build();
            event.replyModal(modal).queue();
        } else if ("rank".equals(selected)) {
            StringSelectMenu rankMenu = StringSelectMenu.create("giveaway:rank:" + memberId)
                .setPlaceholder("Choisir un grade")
                .addOptions(Arrays.stream(PlayerRank.values())
                    .filter(r -> r != PlayerRank.NONE)
                    .map(r -> SelectOption.of(r.getDisplayName(), r.name()))
                    .toList())
                .build();
            event.reply("Sélectionne le grade à offrir :").addComponents(ActionRow.of(rankMenu)).setEphemeral(true).queue();
        } else if ("item".equals(selected)) {
            // Discord limite les StringSelectMenu à 25 options — on passe par des catégories
            // (28 CustomItemType > 25 → crash silencieux sinon, items custom impossibles).
            try {
                StringSelectMenu categoryMenu = StringSelectMenu.create("giveaway:itemcat:" + memberId)
                    .setPlaceholder("Catégorie d'item custom")
                    .addOptions(
                        SelectOption.of("Pickantaxe", "pickantaxe"),
                        SelectOption.of("Cantaxe", "cantaxe"),
                        SelectOption.of("Multi-Cantool", "multicantool"),
                        SelectOption.of("Autres (Cantalame, Vie, Garde…)", "autres")
                    )
                    .build();
                event.reply("Choisis d'abord une catégorie :")
                    .addComponents(ActionRow.of(categoryMenu))
                    .setEphemeral(true)
                    .queue();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Giveaway] Impossible d'ouvrir le menu item", e);
                event.reply("❌ Impossible d'ouvrir le menu des items custom.").setEphemeral(true).queue();
            }
        } else if ("other".equals(selected)) {
            TextInput input = TextInput.create("giveaway:other_text", "Description de la récompense", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Ex: 1 kit de démarrage, 64 diamants...")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(200)
                .build();
            Modal modal = Modal.create("giveaway:other:" + memberId, "Récompense : Autre")
                .addActionRow(input)
                .build();
            event.replyModal(modal).queue();
        }
    }

    private void handleRankSelect(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("giveaway:rank:")) return;

        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.get(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway en cours de configuration.").setEphemeral(true).queue();
            return;
        }

        String rankName = event.getValues().get(0);
        PlayerRank rank = PlayerRank.fromString(rankName);
        if (rank != PlayerRank.NONE) {
            builder.addReward(new Reward("RANK", rank.name(), 1));
        }
        refreshConfigMessage(event, builder);
    }

    private void handleItemCategorySelect(StringSelectInteractionEvent event) {
        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.get(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway en cours de configuration.").setEphemeral(true).queue();
            return;
        }

        String category = event.getValues().get(0);
        List<CustomItemType> types = switch (category) {
            case "pickantaxe" -> Arrays.stream(CustomItemType.values()).filter(CustomItemType::isPickantise).toList();
            case "cantaxe" -> Arrays.stream(CustomItemType.values()).filter(CustomItemType::isCantaxe).toList();
            case "multicantool" -> Arrays.stream(CustomItemType.values()).filter(CustomItemType::isMultiCantool).toList();
            case "autres" -> Arrays.stream(CustomItemType.values())
                .filter(t -> t.isCantalame() || t == CustomItemType.VIE || t.isRuneFortification() || t.isGardeArmor())
                .toList();
            default -> List.of();
        };

        if (types.isEmpty()) {
            event.reply("❌ Catégorie invalide.").setEphemeral(true).queue();
            return;
        }

        try {
            StringSelectMenu itemMenu = StringSelectMenu.create("giveaway:item:" + memberId)
                .setPlaceholder("Choisir un item custom")
                .addOptions(types.stream()
                    .map(t -> SelectOption.of(t.getDisplayName(), t.name()))
                    .toList())
                .build();
            event.reply("Sélectionne l'item custom à offrir :")
                .addComponents(ActionRow.of(itemMenu))
                .setEphemeral(true)
                .queue();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Giveaway] Menu item catégorie=" + category, e);
            event.reply("❌ Impossible d'ouvrir la liste d'items.").setEphemeral(true).queue();
        }
    }

    private void handleItemSelect(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("giveaway:item:")) return;

        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.get(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway en cours de configuration.").setEphemeral(true).queue();
            return;
        }

        String itemName = event.getValues().get(0);
        try {
            CustomItemType itemType = CustomItemType.valueOf(itemName);
            builder.addReward(new Reward("ITEM", itemType.name(), 1));
            refreshConfigMessage(event, builder);
        } catch (IllegalArgumentException e) {
            event.reply("❌ Item invalide.").setEphemeral(true).queue();
        }
    }

    public void handleButton(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();

        if (componentId.startsWith("giveaway:launch:")) {
            launchGiveaway(event);
        } else if (componentId.startsWith("giveaway:cancel:")) {
            builders.remove(memberId);
            event.reply("❌ Giveaway annulé.").setEphemeral(true).queue();
        } else if (componentId.equals("giveaway:join")) {
            String messageId = event.getMessageId();
            String userId = event.getUser().getId();

            // Un giveaway terminé (ou perdu au redémarrage) ne doit plus accepter
            // de participation — sinon le clic crée une liste fantôme jamais tirée.
            if (!activeGiveaways.containsKey(messageId)) {
                event.reply("❌ Ce giveaway est terminé, tu ne peux plus participer.")
                        .setEphemeral(true).queue();
                return;
            }

            Set<String> entries = participants.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet());
            if (!entries.add(userId)) {
                event.reply("⚠️ Tu participes déjà ! (**" + entries.size() + "** participant(s) au total).")
                        .setEphemeral(true).queue();
                return;
            }
            int count = entries.size();
            event.reply("✅ Tu participes au giveaway ! (**" + count + "** participant(s) au total).")
                    .setEphemeral(true).queue();
            refreshPublicGiveawayEmbed(event, messageId, count);
        } else if (componentId.startsWith("giveaway:rank:")) {
            // Handled by StringSelectInteraction, should not reach here
            event.reply("❌ Action non prise en charge.").setEphemeral(true).queue();
        }
    }

    public void handleModal(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.get(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway en cours de configuration.").setEphemeral(true).queue();
            return;
        }

        if (modalId.startsWith("giveaway:cantox:")) {
            String value = event.getValue("giveaway:cantox_amount").getAsString();
            try {
                double amount = Double.parseDouble(value);
                if (amount <= 0) throw new NumberFormatException();
                builder.addReward(new Reward("CANTOX", String.valueOf(amount), 1));
                event.replyEmbeds(configEmbed(builder)).setEphemeral(true).queue();
            } catch (NumberFormatException e) {
                event.reply("❌ Montant invalide.").setEphemeral(true).queue();
            }
        } else if (modalId.startsWith("giveaway:other:")) {
            String value = event.getValue("giveaway:other_text").getAsString();
            builder.addReward(new Reward("OTHER", value, 1));
            event.replyEmbeds(configEmbed(builder)).setEphemeral(true).queue();
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Logique interne
    // ───────────────────────────────────────────────────────────────────────────

    private void refreshConfigMessage(StringSelectInteractionEvent event, GiveawayBuilder builder) {
        event.editMessageEmbeds(configEmbed(builder)).queue();
    }

    private void launchGiveaway(ButtonInteractionEvent event) {
        String memberId = event.getMember() != null ? event.getMember().getId() : event.getUser().getId();
        GiveawayBuilder builder = builders.remove(memberId);
        if (builder == null) {
            event.reply("❌ Aucun giveaway à lancer.").setEphemeral(true).queue();
            return;
        }
        if (builder.rewards.isEmpty()) {
            event.reply("❌ Ajoute au moins une récompense avant de lancer.").setEphemeral(true).queue();
            builders.put(memberId, builder);
            return;
        }

        // GuildMessageChannel couvre TextChannel ET NewsChannel (salons annonce).
        // asTextChannel() rejetait les salons annonce → IllegalStateException.
        if (!(event.getChannel() instanceof GuildMessageChannel channel)) {
            event.reply("❌ Ce salon ne permet pas de lancer un giveaway.").setEphemeral(true).queue();
            builders.put(memberId, builder);
            return;
        }
        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : "Anonyme";

        // Discord exige un accusé de réception sous 3s : on acquitte tout de suite
        // (deferReply) avant l'envoi du message public, qui est un second appel
        // réseau pouvant dépasser ce délai (c'était la cause du "n'a pas répondu à temps").
        event.deferReply(true).queue();

        MessageCreateData message = new MessageCreateBuilder()
            .setContent("<@&1504910718338597089>")
            .setEmbeds(publicGiveawayEmbed(builder, authorName, 0))
            .setActionRow(Button.primary("giveaway:join", "🎉 Participer"))
            .build();

        channel.sendMessage(message).queue(sent -> {
            String messageId = sent.getId();
            activeGiveaways.put(messageId, new ActiveGiveaway(builder, authorName));
            event.getHook().sendMessage("✅ Giveaway lancé dans " + channel.getAsMention() + " !").setEphemeral(true).queue();

            long ticks = builder.durationSeconds * 20L;
            // JDA est thread-safe pour les appels REST : la clôture reste sur un thread
            // async Bukkit (les accès DB passent déjà par le scheduler). Le catch garantit
            // la journalisation complète de toute erreur au lieu d'un échec silencieux.
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    endGiveaway(messageId, builder, channel);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "[Discord] La tâche de clôture du giveaway " + messageId + " a échoué", t);
                }
            }, ticks);
        }, error -> {
            plugin.getLogger().severe("[Discord] Erreur lors de l'envoi du giveaway : " + error.getMessage());
            event.getHook().sendMessage("❌ Erreur lors du lancement du giveaway : " + error.getMessage()).setEphemeral(true).queue();
        });
    }

    private void endGiveaway(String messageId, GiveawayBuilder builder, GuildMessageChannel channel) {
        try {
            ActiveGiveaway active = activeGiveaways.remove(messageId);
            String authorName = active != null ? active.authorName() : "?";
            Set<String> entries = participants.remove(messageId);
            int participantCount = entries != null ? entries.size() : 0;

            // Figer le message public : embed "terminé" + bouton désactivé
            closePublicMessage(channel, messageId, builder, authorName, participantCount);

            if (entries == null || entries.isEmpty()) {
                channel.sendMessage("❌ Le giveaway s'est terminé sans participant.")
                    .queue(null, err -> plugin.getLogger().log(Level.WARNING,
                        "[Discord] Impossible d'annoncer la fin du giveaway sans participant " + messageId, err));
                return;
            }

            List<String> entryList = new ArrayList<>(entries);
            Collections.shuffle(entryList, random);
            int winnerCount = Math.min(builder.winners, entryList.size());
            List<String> winnerIds = entryList.subList(0, winnerCount);

            List<String> winnerMentions = new ArrayList<>();
            List<String> unresolvedWinners = new ArrayList<>();
            for (String discordId : winnerIds) {
                // Le gagnant est annoncé même sans compte Minecraft lié : la récompense
                // n'est alors pas stockée et devra être attribuée manuellement.
                UUID uuid = resolveMinecraftUuid(discordId);
                if (uuid != null) {
                    storeRewards(uuid, builder.rewards);
                } else {
                    unresolvedWinners.add(discordId);
                }
                User user = channel.getJDA().getUserById(discordId);
                winnerMentions.add(user != null ? user.getAsMention() : "<@" + discordId + ">");
            }

            if (!unresolvedWinners.isEmpty()) {
                plugin.getLogger().warning("[Discord] Giveaway " + messageId + " : " + unresolvedWinners.size()
                    + " gagnant(s) sans compte Minecraft lié (récompenses non stockées) : "
                    + String.join(", ", unresolvedWinners));
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xFFD700))
                .setTitle("🎉 Giveaway terminé !")
                .setDescription("Gagnant(s) : " + String.join(", ", winnerMentions))
                .addField("Participants", String.valueOf(participantCount), true)
                .addField("Récompenses", formatRewards(builder.rewards), false)
                .setTimestamp(Instant.now());

            // Les mentions dans un embed ne pingent pas : elles doivent être dans le contenu texte.
            channel.sendMessage("🎉 Félicitations " + String.join(", ", winnerMentions) + " !")
                .setEmbeds(embed.build())
                .queue(null, err -> plugin.getLogger().log(Level.SEVERE,
                    "[Discord] Impossible d'envoyer les résultats du giveaway " + messageId, err));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Discord] Erreur critique lors de la clôture du giveaway " + messageId, e);
        }
    }

    private UUID resolveMinecraftUuid(String discordId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM discord_links WHERE discord_id = ?")) {
            stmt.setString(1, discordId);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Impossible de résoudre l'UUID pour le giveaway : " + e.getMessage());
        }
        return null;
    }

    private void storeRewards(UUID uuid, List<Reward> rewards) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO pending_rewards (player_uuid, reward_type, reward_value, quantity, claimed, created_at) VALUES (?, ?, ?, ?, 0, ?)")) {
                long now = System.currentTimeMillis() / 1000;
                for (Reward reward : rewards) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, reward.type());
                    stmt.setString(3, reward.value());
                    stmt.setInt(4, reward.quantity());
                    stmt.setLong(5, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (Exception e) {
                plugin.getLogger().severe("[Discord] Erreur stockage récompenses giveaway : " + e.getMessage());
            }
        });
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ───────────────────────────────────────────────────────────────────────────

    private long parseDuration(String input) {
        input = input.trim().toLowerCase();
        long totalSeconds = 0;
        int current = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) {
                current = current * 10 + (c - '0');
            } else if (c == 'h') {
                totalSeconds += current * 3600L;
                current = 0;
            } else if (c == 'm') {
                totalSeconds += current * 60L;
                current = 0;
            } else if (c == 's') {
                totalSeconds += current;
                current = 0;
            }
        }
        return totalSeconds;
    }

    private StringSelectMenu rewardSelectMenu() {
        return StringSelectMenu.create("giveaway:reward:menu")
            .setPlaceholder("Ajouter une récompense")
            .addOptions(
                SelectOption.of("💰 Cantox", "cantox"),
                SelectOption.of("🏆 Grade", "rank"),
                SelectOption.of("🎁 Item custom", "item"),
                SelectOption.of("📦 Autre", "other")
            )
            .build();
    }

    private MessageEmbed configEmbed(GiveawayBuilder builder) {
        return new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("🎁 Configuration du giveaway")
            .setDescription("Durée : **" + formatDuration(builder.durationSeconds) + "**\nGagnants : **" + builder.winners + "**\n\nRécompenses :")
            .addField("Récompenses actuelles", formatRewards(builder.rewards), false)
            .setFooter("Sélectionne une récompense ci-dessous puis clique sur Lancer", null)
            .setTimestamp(Instant.now())
            .build();
    }

    private MessageEmbed publicGiveawayEmbed(GiveawayBuilder builder, String author, int participantCount) {
        return new EmbedBuilder()
            .setColor(Color.MAGENTA)
            .setTitle("🎉 Giveaway !")
            .setDescription("Lancé par **" + author + "**\nDurée : **" + formatDuration(builder.durationSeconds) + "**\nGagnants : **" + builder.winners + "**")
            .addField("Participants", String.valueOf(participantCount), true)
            .addField("Récompenses", formatRewards(builder.rewards), false)
            .setFooter("Clique sur 🎉 pour participer", null)
            .setTimestamp(Instant.now())
            .build();
    }

    /** Remplace l'embed public par une version "terminé" et désactive le bouton Participer. */
    private void closePublicMessage(GuildMessageChannel channel, String messageId, GiveawayBuilder builder,
                                    String authorName, int participantCount) {
        MessageEmbed ended = new EmbedBuilder()
            .setColor(Color.DARK_GRAY)
            .setTitle("🎉 Giveaway terminé !")
            .setDescription("Lancé par **" + authorName + "**\nGagnants : **" + builder.winners + "**")
            .addField("Participants", String.valueOf(participantCount), true)
            .addField("Récompenses", formatRewards(builder.rewards), false)
            .setFooter("Ce giveaway est terminé", null)
            .setTimestamp(Instant.now())
            .build();

        channel.retrieveMessageById(messageId).queue(msg ->
            msg.editMessageEmbeds(ended)
                .setComponents(ActionRow.of(Button.secondary("giveaway:join", "🎉 Participer").asDisabled()))
                .queue(null, err -> plugin.getLogger().log(Level.WARNING,
                    "[Discord] Impossible de désactiver le bouton du giveaway " + messageId, err)),
            err -> { /* message supprimé entre-temps : rien à figer */ });
    }

    private void refreshPublicGiveawayEmbed(ButtonInteractionEvent event, String messageId, int participantCount) {
        try {
            ActiveGiveaway active = activeGiveaways.get(messageId);
            Message message = event.getMessage();
            if (active == null || message == null) return;
            message.editMessageEmbeds(publicGiveawayEmbed(active.builder, active.authorName, participantCount))
                    .queue(null, err -> {
                        // Le message public a pu être supprimé entre-temps : on arrête
                        // alors le suivi du giveaway au lieu d'accumuler les erreurs.
                        if (err instanceof ErrorResponseException ere
                                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                            activeGiveaways.remove(messageId);
                            plugin.getLogger().warning("[Discord] Message du giveaway " + messageId + " supprimé, suivi arrêté.");
                        } else {
                            plugin.getLogger().log(Level.WARNING, "[Discord] Mise à jour participants giveaway " + messageId, err);
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Erreur lors du rafraîchissement du giveaway " + messageId, e);
        }
    }

    private String formatRewards(List<Reward> rewards) {
        if (rewards.isEmpty()) return "Aucune";
        StringBuilder sb = new StringBuilder();
        for (Reward reward : rewards) {
            sb.append("• ");
            switch (reward.type()) {
                case "CANTOX" -> sb.append("💰 ").append(String.format("%.0f", Double.parseDouble(reward.value()))).append(" Cantox");
                case "RANK" -> sb.append("🏆 Grade ").append(PlayerRank.fromString(reward.value()).getDisplayName());
                case "ITEM" -> sb.append("🎁 Item ").append(CustomItemType.valueOf(reward.value()).getDisplayName());
                default -> sb.append("📦 ").append(reward.value());
            }
            if (reward.quantity() > 1) {
                sb.append(" x").append(reward.quantity());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Modèles internes
    // ───────────────────────────────────────────────────────────────────────────

    private static class GiveawayBuilder {
        final String memberId;
        final long durationSeconds;
        final int winners;
        final List<Reward> rewards = new ArrayList<>();

        GiveawayBuilder(String memberId, long durationSeconds, int winners) {
            this.memberId = memberId;
            this.durationSeconds = durationSeconds;
            this.winners = winners;
        }

        void addReward(Reward reward) {
            rewards.add(reward);
        }
    }

    private record Reward(String type, String value, int quantity) {}

    /** Métadonnées d'un giveaway publié (pour rafraîchir l'embed participants). */
    private record ActiveGiveaway(GiveawayBuilder builder, String authorName) {}
}
