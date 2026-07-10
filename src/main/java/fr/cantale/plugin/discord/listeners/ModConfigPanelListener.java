package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Panel interactif de configuration de la modération Discord.
 * Accessible via /modconfig — affiche un embed avec select menu et boutons.
 */
public class ModConfigPanelListener extends ListenerAdapter {

    private static final List<Long> AUTHORIZED_ROLES = Arrays.asList(
        1504907451789475991L,
        1504907619851047003L,
        1504907542554349618L
    );

    private static final Color COLOR_PRIMARY = new Color(88, 101, 242);
    private static final Color COLOR_OK = new Color(87, 242, 135);
    private static final Color COLOR_WARN = new Color(254, 231, 92);

    private final DiscordConfig config;

    public ModConfigPanelListener(DiscordConfig config) {
        this.config = config;
    }

    // ═══════════════════════════════════════════════════════
    //  Entrée : slash command /modconfig
    // ═══════════════════════════════════════════════════════

    public void openPanel(SlashCommandInteractionEvent event) {
        if (!isAuthorized(event.getMember())) {
            event.reply("❌ Cette commande est réservée aux rôles staff Cantale.").setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(buildHomeEmbed().build())
            .setComponents(buildHomeComponents())
            .setEphemeral(true)
            .queue();
    }

    // ═══════════════════════════════════════════════════════
    //  Boutons
    // ═══════════════════════════════════════════════════════

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("mcfg:")) return;
        if (!isAuthorized(event.getMember())) {
            event.reply("❌ Action réservée au staff Cantale.").setEphemeral(true).queue();
            return;
        }

        String[] parts = id.split(":");
        String action = parts[1];

        switch (action) {
            case "home" -> editToHome(event);
            case "refresh" -> editToHome(event);
            case "back" -> editToHome(event);
            case "cat" -> editToCategory(event, parts[2]);
            case "tog" -> handleToggle(event, parts[2], parts.length > 3 ? parts[3] : "home");
            case "set" -> openSetModal(event, parts[2], parts.length > 3 ? parts[3] : "home");
            case "word" -> openWordModal(event, parts[2]);
            case "link" -> openLinkModal(event, parts[2]);
            default -> event.reply("❌ Action inconnue.").setEphemeral(true).queue();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Select menu
    // ═══════════════════════════════════════════════════════

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!"mcfg:select".equals(event.getComponentId())) return;
        if (!isAuthorized(event.getMember())) {
            event.reply("❌ Action réservée au staff Cantale.").setEphemeral(true).queue();
            return;
        }
        String cat = event.getValues().get(0);
        editToCategory(event, cat);
    }

    // ═══════════════════════════════════════════════════════
    //  Modals
    // ═══════════════════════════════════════════════════════

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("mcfg-m:")) return;
        if (!isAuthorized(event.getMember())) {
            event.reply("❌ Action réservée au staff Cantale.").setEphemeral(true).queue();
            return;
        }

        String[] parts = id.split(":");
        String kind = parts[1];

        switch (kind) {
            case "set" -> handleSetModal(event, parts[2], parts.length > 3 ? parts[3] : "home");
            case "word" -> handleWordModal(event, parts[2]);
            case "link" -> handleLinkModal(event, parts[2]);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Home embed
    // ═══════════════════════════════════════════════════════

    private EmbedBuilder buildHomeEmbed() {
        return new EmbedBuilder()
            .setColor(COLOR_PRIMARY)
            .setTitle("⚙️ Panel Modération Cantale")
            .setDescription("Choisis une catégorie dans le menu déroulant pour configurer la modération Discord.\n\n" +
                "**État actuel des protections** :")
            .addField("️ Anti-raid", statusLine(config.isAntiRaid()), true)
            .addField("🔔 Anti-ping", statusLine(config.isAntiPing()), true)
            .addField("🔠 Anti-caps", statusLine(config.isAntiCaps()), true)
            .addField("🔗 Anti-liens", statusLine(config.isAntiLinks()), true)
            .addField("📨 Anti-invites", statusLine(config.isAntiInvites()), true)
            .addField("📈 Escalade auto", statusLine(config.isEscalationEnabled()), true)
            .addField("📚 Mots interdits", String.valueOf(config.getBannedWords().size()), true)
            .addField("✅ Liens autorisés", String.valueOf(config.getAllowedLinks().size()), true)
            .setFooter("Cantale Network — Modération")
            .setTimestamp(Instant.now());
    }

    private List<ActionRow> buildHomeComponents() {
        StringSelectMenu menu = StringSelectMenu.create("mcfg:select")
            .setPlaceholder("📂 Choisir une catégorie à configurer…")
            .addOption("Anti-raid", "raid", "Protection contre les vagues de joins", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🛡️"))
            .addOption("Anti-ping", "ping", "Limite des mentions massives", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔔"))
            .addOption("Anti-caps", "caps", "Détection des majuscules excessives", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔠"))
            .addOption("Anti-liens / Invites", "links", "Filtrage des liens et invitations", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔗"))
            .addOption("Escalade automatique", "escalation", "Sanctions progressives", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📈"))
            .addOption("Mots interdits", "words", "Liste des mots bannis", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📚"))
            .addOption("Liens autorisés", "linklist", "Whitelist des URLs", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅"))
            .addOption("Cache & avancé", "cache", "Taille du cache de messages", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("💾"))
            .build();

        Button refresh = Button.secondary("mcfg:refresh", "🔄 Rafraîchir");
        return List.of(ActionRow.of(menu), ActionRow.of(refresh));
    }

    private void editToHome(IReplyCallback event) {
        MessageEditData data = new MessageEditBuilder()
            .setEmbeds(buildHomeEmbed().build())
            .setComponents(buildHomeComponents())
            .build();
        if (event instanceof ButtonInteractionEvent b) {
            b.editMessage(data).queue();
        } else if (event instanceof StringSelectInteractionEvent s) {
            s.editMessage(data).queue();
        } else if (event instanceof ModalInteractionEvent m) {
            m.editMessage(data).queue();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Category embeds
    // ═══════════════════════════════════════════════════════

    private void editToCategory(GenericInteractionCreateEvent event, String cat) {
        EmbedBuilder embed = buildCategoryEmbed(cat);
        List<ActionRow> rows = buildCategoryComponents(cat);
        MessageEditData data = new MessageEditBuilder()
            .setEmbeds(embed.build())
            .setComponents(rows)
            .build();
        if (event instanceof ButtonInteractionEvent b) b.editMessage(data).queue();
        else if (event instanceof StringSelectInteractionEvent s) s.editMessage(data).queue();
        else if (event instanceof ModalInteractionEvent m) m.editMessage(data).queue();
    }

    private EmbedBuilder buildCategoryEmbed(String cat) {
        EmbedBuilder e = new EmbedBuilder().setColor(COLOR_PRIMARY).setTimestamp(Instant.now());
        switch (cat) {
            case "raid" -> e.setTitle("🛡️ Anti-raid")
                .setDescription("Détecte les vagues de joins suspectes et active le lockdown.")
                .addField("Anti-raid", statusLine(config.isAntiRaid()), true)
                .addField("Lockdown auto", statusLine(config.isRaidLockdown()), true)
                .addField("\u200B", "\u200B", true)
                .addField("Seuil de joins", String.valueOf(config.getRaidJoinThreshold()), true)
                .addField("Fenêtre (s)", String.valueOf(config.getRaidJoinWindowSeconds()), true)
                .addField("Durée lockdown (s)", String.valueOf(config.getRaidLockdownDurationSeconds()), true);
            case "ping" -> e.setTitle("🔔 Anti-ping")
                .setDescription("Limite les mentions massives. Peut mute automatiquement.")
                .addField("Anti-ping", statusLine(config.isAntiPing()), true)
                .addField("Mute auto", statusLine(config.isMuteOnPing()), true)
                .addField("\u200B", "\u200B", true)
                .addField("Mentions max", String.valueOf(config.getMaxMentions()), true)
                .addField("Durée mute (s)", String.valueOf(config.getMuteOnPingDurationSeconds()), true);
            case "caps" -> e.setTitle("🔠 Anti-caps")
                .setDescription("Détecte les messages écrits trop en majuscules.")
                .addField("État", statusLine(config.isAntiCaps()), true)
                .addField("Seuil (%)", config.getCapsPercent() + "%", true);
            case "links" -> e.setTitle("🔗 Anti-liens / Anti-invites")
                .setDescription("Filtre les URLs non whitelistées et les invitations Discord.")
                .addField("Anti-liens", statusLine(config.isAntiLinks()), true)
                .addField("Anti-invites", statusLine(config.isAntiInvites()), true);
            case "escalation" -> e.setTitle("📈 Escalade automatique")
                .setDescription("Sanctions progressives : warn → mute → kick selon le nombre d'infractions.")
                .addField("État", statusLine(config.isEscalationEnabled()), true)
                .addField("\u200B", "\u200B", true)
                .addField("\u200B", "\u200B", true)
                .addField("Warn (infractions)", String.valueOf(config.getWarnThreshold()), true)
                .addField("Mute (infractions)", String.valueOf(config.getMuteThreshold()), true)
                .addField("Kick (infractions)", String.valueOf(config.getKickThreshold()), true);
            case "words" -> {
                String list = config.getBannedWords().isEmpty() ? "*Aucun mot interdit.*" : "`" + String.join("`, `", config.getBannedWords()) + "`";
                e.setTitle("📚 Mots interdits")
                    .setDescription("Mots automatiquement modérés.\n\n" + list)
                    .setColor(COLOR_WARN);
            }
            case "linklist" -> {
                String list = config.getAllowedLinks().isEmpty() ? "*Aucune URL whitelistée.*" : "`" + String.join("`, `", config.getAllowedLinks()) + "`";
                e.setTitle("✅ Liens autorisés")
                    .setDescription("URLs qui passent l'anti-liens.\n\n" + list)
                    .setColor(COLOR_OK);
            }
            case "cache" -> e.setTitle("💾 Cache & avancé")
                .setDescription("Paramètres internes du système de modération.")
                .addField("Taille du cache de messages", String.valueOf(config.getMessageCacheSize()), true);
            default -> e.setTitle("❓ Catégorie inconnue");
        }
        return e;
    }

    private List<ActionRow> buildCategoryComponents(String cat) {
        Button back = Button.secondary("mcfg:back", "⬅️ Retour");
        switch (cat) {
            case "raid" -> {
                return List.of(
                    ActionRow.of(
                        toggleBtn("antiraid", config.isAntiRaid(), "Anti-raid", cat),
                        toggleBtn("lockdown", config.isRaidLockdown(), "Lockdown", cat)
                    ),
                    ActionRow.of(
                        setBtn("raid-join", "Seuil joins", cat),
                        setBtn("raid-window", "Fenêtre (s)", cat),
                        setBtn("raid-lockdown", "Durée lockdown", cat)
                    ),
                    ActionRow.of(back)
                );
            }
            case "ping" -> {
                return List.of(
                    ActionRow.of(
                        toggleBtn("antiping", config.isAntiPing(), "Anti-ping", cat),
                        toggleBtn("muteping", config.isMuteOnPing(), "Mute auto", cat)
                    ),
                    ActionRow.of(
                        setBtn("max-mentions", "Mentions max", cat),
                        setBtn("mute-ping", "Durée mute (s)", cat)
                    ),
                    ActionRow.of(back)
                );
            }
            case "caps" -> {
                return List.of(
                    ActionRow.of(toggleBtn("anticaps", config.isAntiCaps(), "Anti-caps", cat)),
                    ActionRow.of(setBtn("caps", "Seuil %", cat)),
                    ActionRow.of(back)
                );
            }
            case "links" -> {
                return List.of(
                    ActionRow.of(
                        toggleBtn("antilinks", config.isAntiLinks(), "Anti-liens", cat),
                        toggleBtn("antiinvites", config.isAntiInvites(), "Anti-invites", cat)
                    ),
                    ActionRow.of(back)
                );
            }
            case "escalation" -> {
                return List.of(
                    ActionRow.of(toggleBtn("escalation", config.isEscalationEnabled(), "Escalade", cat)),
                    ActionRow.of(
                        setBtn("warn", "Seuil warn", cat),
                        setBtn("mute", "Seuil mute", cat),
                        setBtn("kick", "Seuil kick", cat)
                    ),
                    ActionRow.of(back)
                );
            }
            case "words" -> {
                return List.of(
                    ActionRow.of(
                        Button.success("mcfg:word:add", "➕ Ajouter un mot"),
                        Button.danger("mcfg:word:rm", "➖ Retirer un mot")
                    ),
                    ActionRow.of(back)
                );
            }
            case "linklist" -> {
                return List.of(
                    ActionRow.of(
                        Button.success("mcfg:link:add", "➕ Ajouter une URL"),
                        Button.danger("mcfg:link:rm", "➖ Retirer une URL")
                    ),
                    ActionRow.of(back)
                );
            }
            case "cache" -> {
                return List.of(
                    ActionRow.of(setBtn("cache-size", "Taille du cache", cat)),
                    ActionRow.of(back)
                );
            }
        }
        return List.of(ActionRow.of(back));
    }

    private Button toggleBtn(String feature, boolean current, String label, String cat) {
        String id = "mcfg:tog:" + feature + ":" + cat;
        return current
            ? Button.success(id, "✅ " + label + " : ON")
            : Button.secondary(id, "⭕ " + label + " : OFF");
    }

    private Button setBtn(String param, String label, String cat) {
        return Button.primary("mcfg:set:" + param + ":" + cat, "✏️ " + label);
    }

    // ═══════════════════════════════════════════════════════
    //  Handlers : toggle, modals
    // ═══════════════════════════════════════════════════════

    private void handleToggle(ButtonInteractionEvent event, String feature, String cat) {
        switch (feature) {
            case "antiraid" -> config.setAntiRaid(!config.isAntiRaid());
            case "lockdown" -> config.setRaidLockdown(!config.isRaidLockdown());
            case "antiping" -> config.setAntiPing(!config.isAntiPing());
            case "muteping" -> config.setMuteOnPing(!config.isMuteOnPing());
            case "anticaps" -> config.setAntiCaps(!config.isAntiCaps());
            case "antilinks" -> config.setAntiLinks(!config.isAntiLinks());
            case "antiinvites" -> config.setAntiInvites(!config.isAntiInvites());
            case "escalation" -> config.setEscalationEnabled(!config.isEscalationEnabled());
            default -> {
                event.reply("❌ Toggle inconnu.").setEphemeral(true).queue();
                return;
            }
        }
        config.save();
        editToCategory(event, cat);
    }

    private void openSetModal(ButtonInteractionEvent event, String param, String cat) {
        int current = currentIntValue(param);
        TextInput input = TextInput.create("value", "Nouvelle valeur (entier)", TextInputStyle.SHORT)
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .setValue(String.valueOf(current))
            .setPlaceholder("Entrez un nombre entier")
            .build();
        Modal modal = Modal.create("mcfg-m:set:" + param + ":" + cat, "Modifier : " + labelForParam(param))
            .addComponents(ActionRow.of(input))
            .build();
        event.replyModal(modal).queue();
    }

    private void handleSetModal(ModalInteractionEvent event, String param, String cat) {
        String raw = event.getValue("value").getAsString().trim();
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            event.reply("❌ Valeur invalide : `" + raw + "` n'est pas un entier.").setEphemeral(true).queue();
            return;
        }
        if (value < 0) {
            event.reply("❌ La valeur doit être positive.").setEphemeral(true).queue();
            return;
        }

        switch (param) {
            case "raid-join" -> config.setRaidJoinThreshold(value);
            case "raid-window" -> config.setRaidJoinWindowSeconds(value);
            case "raid-lockdown" -> config.setRaidLockdownDurationSeconds(value);
            case "max-mentions" -> config.setMaxMentions(value);
            case "mute-ping" -> config.setMuteOnPingDurationSeconds(value);
            case "caps" -> config.setCapsPercent(value);
            case "warn" -> config.setWarnThreshold(value);
            case "mute" -> config.setMuteThreshold(value);
            case "kick" -> config.setKickThreshold(value);
            case "cache-size" -> config.setMessageCacheSize(value);
            default -> {
                event.reply("❌ Paramètre inconnu.").setEphemeral(true).queue();
                return;
            }
        }
        config.save();
        editToCategory(event, cat);
    }

    private void openWordModal(ButtonInteractionEvent event, String action) {
        TextInput input = TextInput.create("word", "Mot", TextInputStyle.SHORT)
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(64)
            .setPlaceholder("Tape le mot (sans casse)")
            .build();
        String title = "add".equals(action) ? "Ajouter un mot interdit" : "Retirer un mot interdit";
        Modal modal = Modal.create("mcfg-m:word:" + action, title)
            .addComponents(ActionRow.of(input))
            .build();
        event.replyModal(modal).queue();
    }

    private void handleWordModal(ModalInteractionEvent event, String action) {
        String word = event.getValue("word").getAsString().toLowerCase().trim();
        List<String> words = config.getBannedWords();
        if ("add".equals(action)) {
            if (words.contains(word)) {
                event.reply("⚠️ `" + word + "` est déjà dans la liste.").setEphemeral(true).queue();
                return;
            }
            words.add(word);
        } else {
            if (!words.remove(word)) {
                event.reply("⚠️ `" + word + "` n'est pas dans la liste.").setEphemeral(true).queue();
                return;
            }
        }
        config.save();
        editToCategory(event, "words");
    }

    private void openLinkModal(ButtonInteractionEvent event, String action) {
        TextInput input = TextInput.create("url", "URL / domaine", TextInputStyle.SHORT)
            .setRequired(true)
            .setMinLength(3)
            .setMaxLength(128)
            .setPlaceholder("ex: youtube.com")
            .build();
        String title = "add".equals(action) ? "Ajouter une URL autorisée" : "Retirer une URL autorisée";
        Modal modal = Modal.create("mcfg-m:link:" + action, title)
            .addComponents(ActionRow.of(input))
            .build();
        event.replyModal(modal).queue();
    }

    private void handleLinkModal(ModalInteractionEvent event, String action) {
        String url = event.getValue("url").getAsString().toLowerCase().trim();
        List<String> links = config.getAllowedLinks();
        if ("add".equals(action)) {
            if (links.contains(url)) {
                event.reply("⚠️ `" + url + "` est déjà dans la liste.").setEphemeral(true).queue();
                return;
            }
            links.add(url);
        } else {
            if (!links.remove(url)) {
                event.reply("⚠️ `" + url + "` n'est pas dans la liste.").setEphemeral(true).queue();
                return;
            }
        }
        config.save();
        editToCategory(event, "linklist");
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    private int currentIntValue(String param) {
        return switch (param) {
            case "raid-join" -> config.getRaidJoinThreshold();
            case "raid-window" -> config.getRaidJoinWindowSeconds();
            case "raid-lockdown" -> config.getRaidLockdownDurationSeconds();
            case "max-mentions" -> config.getMaxMentions();
            case "mute-ping" -> config.getMuteOnPingDurationSeconds();
            case "caps" -> config.getCapsPercent();
            case "warn" -> config.getWarnThreshold();
            case "mute" -> config.getMuteThreshold();
            case "kick" -> config.getKickThreshold();
            case "cache-size" -> config.getMessageCacheSize();
            default -> 0;
        };
    }

    private String labelForParam(String param) {
        return switch (param) {
            case "raid-join" -> "Seuil joins (raid)";
            case "raid-window" -> "Fenêtre joins (s)";
            case "raid-lockdown" -> "Durée lockdown (s)";
            case "max-mentions" -> "Mentions max par message";
            case "mute-ping" -> "Durée mute ping (s)";
            case "caps" -> "Seuil caps (%)";
            case "warn" -> "Seuil warn";
            case "mute" -> "Seuil mute";
            case "kick" -> "Seuil kick";
            case "cache-size" -> "Taille du cache de messages";
            default -> param;
        };
    }

    private String statusLine(boolean on) {
        return on ? "✅ **Activé**" : "❌ Désactivé";
    }

    private boolean isAuthorized(Member member) {
        if (member == null) return false;
        return member.getRoles().stream().anyMatch(r -> AUTHORIZED_ROLES.contains(r.getIdLong()));
    }
}
