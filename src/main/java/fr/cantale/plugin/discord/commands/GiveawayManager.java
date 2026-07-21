package fr.cantale.plugin.discord.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.rank.PlayerRank;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
    private final Random random = new Random();

    public GiveawayManager(CantalePlugin plugin) {
        this.plugin = plugin;
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
            StringSelectMenu itemMenu = StringSelectMenu.create("giveaway:item:" + memberId)
                .setPlaceholder("Choisir un item custom")
                .addOptions(Arrays.stream(CustomItemType.values())
                    .map(t -> SelectOption.of(t.getDisplayName(), t.name()))
                    .toList())
                .build();
            event.reply("Sélectionne l'item custom à offrir :").addComponents(ActionRow.of(itemMenu)).setEphemeral(true).queue();
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
            participants.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            event.reply("✅ Tu participes au giveaway !").setEphemeral(true).queue();
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
                event.getHook().editOriginalEmbeds(configEmbed(builder)).queue();
            } catch (NumberFormatException e) {
                event.reply("❌ Montant invalide.").setEphemeral(true).queue();
            }
        } else if (modalId.startsWith("giveaway:other:")) {
            String value = event.getValue("giveaway:other_text").getAsString();
            builder.addReward(new Reward("OTHER", value, 1));
            event.getHook().editOriginalEmbeds(configEmbed(builder)).queue();
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

        TextChannel channel = event.getChannel().asTextChannel();
        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : "Anonyme";

        MessageCreateData message = new MessageCreateBuilder()
            .setEmbeds(publicGiveawayEmbed(builder, authorName))
            .setActionRow(Button.primary("giveaway:join", "🎉 Participer"))
            .build();

        channel.sendMessage(message).queue(sent -> {
            String messageId = sent.getId();
            event.reply("✅ Giveaway lancé dans " + channel.getAsMention() + " !").setEphemeral(true).queue();

            long ticks = builder.durationSeconds * 20L;
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> endGiveaway(messageId, builder, channel), ticks);
        });
    }

    private void endGiveaway(String messageId, GiveawayBuilder builder, TextChannel channel) {
        Set<String> entries = participants.remove(messageId);
        if (entries == null || entries.isEmpty()) {
            channel.sendMessage("❌ Le giveaway s'est terminé sans participant.").queue();
            return;
        }

        List<String> entryList = new ArrayList<>(entries);
        Collections.shuffle(entryList, random);
        int winnerCount = Math.min(builder.winners, entryList.size());
        List<String> winnerIds = entryList.subList(0, winnerCount);

        List<String> winnerMentions = new ArrayList<>();
        for (String discordId : winnerIds) {
            UUID uuid = resolveMinecraftUuid(discordId);
            if (uuid != null) {
                storeRewards(uuid, builder.rewards);
                User user = channel.getJDA().getUserById(discordId);
                winnerMentions.add(user != null ? user.getAsMention() : "<@" + discordId + ">");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0xFFD700))
            .setTitle("🎉 Giveaway terminé !")
            .setDescription("Gagnant(s) : " + (winnerMentions.isEmpty() ? "Aucun compte lié" : String.join(", ", winnerMentions)))
            .addField("Récompenses", formatRewards(builder.rewards), false)
            .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
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

    private MessageEmbed publicGiveawayEmbed(GiveawayBuilder builder, String author) {
        return new EmbedBuilder()
            .setColor(Color.MAGENTA)
            .setTitle("🎉 Giveaway !")
            .setDescription("Lancé par **" + author + "**\nDurée : **" + formatDuration(builder.durationSeconds) + "**\nGagnants : **" + builder.winners + "**")
            .addField("Récompenses", formatRewards(builder.rewards), false)
            .setFooter("Clique sur 🎉 pour participer", null)
            .setTimestamp(Instant.now())
            .build();
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
}
