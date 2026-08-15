package fr.cantale.plugin.discord.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Salon compteur Discord — règles strictes :
 * <ul>
 *   <li>Message = exactement l'entier suivant (espaces optionnels uniquement)</li>
 *   <li>Pas de lettres, préfixes, suffixes (« r », « 12r », « n12 »…)</li>
 *   <li>Doit être {@code previous + 1}</li>
 *   <li>Un même auteur ne peut pas compter deux fois de suite</li>
 * </ul>
 * Tout message invalide est supprimé (Manage Messages) + avertissement FR court.
 */
public class CountingListener extends ListenerAdapter {

    /** Entier positif seul, espaces optionnels autour — rien d'autre. */
    private static final Pattern STRICT_INTEGER = Pattern.compile("^\\s*(\\d+)\\s*$");
    private static final String STATE_FILE = "discord-counting.yml";
    private static final int HISTORY_SCAN = 100;
    private static final Emoji WRONG_REACT = Emoji.fromUnicode("❌");

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private final AtomicLong current = new AtomicLong(0L);
    private final AtomicLong lastAuthorId = new AtomicLong(0L);
    private final Object saveLock = new Object();
    private volatile boolean hydrated;

    public CountingListener(CantalePlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;
        loadFromFile();
    }

    /**
     * Synchronise le compteur avec l'historique du salon (à appeler après JDA ready).
     * Priorité au fichier persisté si &gt; 0 ; sinon dernier nombre valide dans l'historique.
     */
    public void hydrateFromHistory(JDA jda) {
        if (hydrated) return;
        long channelId = config.getCountingChannelId();
        if (channelId <= 0L || jda == null) {
            hydrated = true;
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().info("[Discord Counting] Salon introuvable (id=" + channelId
                + "), compteur fichier=" + current.get());
            hydrated = true;
            return;
        }

        channel.getHistory().retrievePast(HISTORY_SCAN).queue(messages -> {
            HistoryHit hit = findLastValidCountInHistory(messages);
            long fileValue = current.get();
            if (fileValue > 0L) {
                plugin.getLogger().info("[Discord Counting] Compteur fichier conservé : " + fileValue
                    + (hit != null ? " (historique récent ≈ " + hit.number + ")" : ""));
            } else if (hit != null) {
                setAndSave(hit.number, hit.authorId);
                plugin.getLogger().info("[Discord Counting] Compteur repris depuis l'historique : " + hit.number);
            } else {
                plugin.getLogger().info("[Discord Counting] Aucun compte trouvé — départ à 0.");
            }
            plugin.getLogger().info("[Discord Counting] Actif sur #" + channel.getName()
                + " — prochain attendu : " + (current.get() + 1)
                + " (strict, anti double-post)");
            hydrated = true;
        }, err -> {
            plugin.getLogger().warning("[Discord Counting] Historique illisible : " + err.getMessage()
                + " — compteur fichier=" + current.get());
            hydrated = true;
        });
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;

        long channelId = config.getCountingChannelId();
        if (channelId <= 0L || event.getChannel().getIdLong() != channelId) return;

        String content = event.getMessage().getContentRaw();
        long expected = current.get() + 1L;
        long authorId = event.getAuthor().getIdLong();

        Long parsed = extractCountNumber(content);
        if (parsed == null) {
            rejectWrong(event, expected, "message invalide — envoie uniquement le nombre (ex. **"
                + expected + "**), sans lettres ni texte.");
            return;
        }

        if (parsed != expected) {
            rejectWrong(event, expected, "mauvais nombre — le prochain est **" + expected + "**.");
            return;
        }

        long prevAuthor = lastAuthorId.get();
        if (prevAuthor != 0L && prevAuthor == authorId) {
            rejectWrong(event, expected, "tu ne peux pas compter deux fois de suite — laisse quelqu'un d'autre.");
            return;
        }

        setAndSave(expected, authorId);
        event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue(null, ignored -> {});
    }

    private void rejectWrong(MessageReceivedEvent event, long expected, String reason) {
        Message message = event.getMessage();
        TextChannel channel = event.getChannel() instanceof TextChannel tc ? tc : null;

        boolean canManage = channel != null
            && event.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE);

        Runnable warn = () -> {
            if (channel == null) return;
            String text = "❌ " + reason;
            channel.sendMessage(event.getAuthor().getAsMention() + " " + text)
                .queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS), ignored -> {});
        };

        if (canManage) {
            message.delete().reason("Compteur Discord : " + reason).queue(
                ok -> warn.run(),
                err -> {
                    message.addReaction(WRONG_REACT).queue(null, ignored -> {});
                    warn.run();
                }
            );
        } else {
            message.addReaction(WRONG_REACT).queue(null, ignored -> {});
            warn.run();
        }
    }

    /**
     * Accepte uniquement un message dont le contenu entier est un entier (espaces OK).
     * @return null si format invalide
     */
    static Long extractCountNumber(String content) {
        if (content == null) return null;
        Matcher m = STRICT_INTEGER.matcher(content);
        if (!m.matches()) return null;
        return parseLongSafe(m.group(1));
    }

    private static Long parseLongSafe(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private HistoryHit findLastValidCountInHistory(List<Message> messages) {
        // messages : du plus récent au plus ancien
        for (Message msg : messages) {
            if (msg.getAuthor().isBot() || msg.isWebhookMessage()) continue;
            Long n = extractCountNumber(msg.getContentRaw());
            if (n != null && n >= 0L) {
                return new HistoryHit(n, msg.getAuthor().getIdLong());
            }
        }
        return null;
    }

    private void loadFromFile() {
        File file = new File(plugin.getDataFolder(), STATE_FILE);
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long value = yaml.getLong("current", 0L);
        if (value < 0L) value = 0L;
        current.set(value);
        lastAuthorId.set(yaml.getLong("last-author-id", 0L));
        plugin.getLogger().info("[Discord Counting] Compteur chargé depuis fichier : " + value);
    }

    private void setAndSave(long value, long authorId) {
        current.set(value);
        lastAuthorId.set(authorId);
        synchronized (saveLock) {
            File file = new File(plugin.getDataFolder(), STATE_FILE);
            FileConfiguration yaml = new YamlConfiguration();
            yaml.set("current", value);
            yaml.set("last-author-id", authorId);
            yaml.set("updated-at", System.currentTimeMillis());
            try {
                if (!plugin.getDataFolder().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    plugin.getDataFolder().mkdirs();
                }
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("[Discord Counting] Échec sauvegarde : " + e.getMessage());
            }
        }
    }

    public long getCurrent() {
        return current.get();
    }

    private record HistoryHit(long number, long authorId) {}
}
