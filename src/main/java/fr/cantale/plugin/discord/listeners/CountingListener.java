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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Salon compteur Discord : le chiffre doit être prev+1.
 * Messages sans aucun chiffre = chat libre (ignorés, compteur inchangé).
 */
public class CountingListener extends ListenerAdapter {

    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)");
    private static final Pattern ANY_NUMBER = Pattern.compile("\\d+");
    private static final String STATE_FILE = "discord-counting.yml";
    private static final int HISTORY_SCAN = 100;
    private static final Emoji WRONG_REACT = Emoji.fromUnicode("❌");

    private final CantalePlugin plugin;
    private final DiscordConfig config;
    private final AtomicLong current = new AtomicLong(0L);
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
            Long lastCount = findLastCountInHistory(messages);
            long fileValue = current.get();
            if (fileValue > 0L) {
                plugin.getLogger().info("[Discord Counting] Compteur fichier conservé : " + fileValue
                    + (lastCount != null ? " (historique récent ≈ " + lastCount + ")" : ""));
            } else if (lastCount != null) {
                setAndSave(lastCount);
                plugin.getLogger().info("[Discord Counting] Compteur repris depuis l'historique : " + lastCount);
            } else {
                plugin.getLogger().info("[Discord Counting] Aucun compte trouvé — départ à 0.");
            }
            plugin.getLogger().info("[Discord Counting] Actif sur #" + channel.getName()
                + " — prochain attendu : " + (current.get() + 1));
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
        if (content == null || content.isBlank()) return;

        // Pas de chiffre → chat libre, compteur inchangé
        if (!HAS_DIGIT.matcher(content).find()) return;

        Long parsed = extractCountNumber(content);
        long expected = current.get() + 1L;

        if (parsed == null || parsed != expected) {
            rejectWrong(event, expected);
            return;
        }

        // Correct → avancer + persister
        setAndSave(expected);
        event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue(null, ignored -> {});
    }

    private void rejectWrong(MessageReceivedEvent event, long expected) {
        Message message = event.getMessage();
        TextChannel channel = event.getChannel() instanceof TextChannel tc ? tc : null;

        boolean canManage = channel != null
            && event.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE);

        Runnable warn = () -> {
            if (channel == null) return;
            String text = "❌ Mauvais nombre — le prochain est **" + expected
                + "**. (Sans chiffre = chat libre.)";
            channel.sendMessage(event.getAuthor().getAsMention() + " " + text)
                .queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS), ignored -> {});
        };

        if (canManage) {
            message.delete().reason("Compteur Discord : mauvais nombre").queue(
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
     * Extrait le nombre du compteur : nombre en tête de message, sinon unique entier du message.
     * @return null si format ambigu / invalide
     */
    static Long extractCountNumber(String content) {
        Matcher lead = LEADING_NUMBER.matcher(content);
        if (lead.find()) {
            return parseLongSafe(lead.group(1));
        }

        Matcher any = ANY_NUMBER.matcher(content);
        List<String> found = new ArrayList<>();
        while (any.find()) {
            found.add(any.group());
        }
        if (found.size() == 1) {
            return parseLongSafe(found.get(0));
        }
        return null;
    }

    private static Long parseLongSafe(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long findLastCountInHistory(List<Message> messages) {
        // messages : du plus récent au plus ancien
        for (Message msg : messages) {
            if (msg.getAuthor().isBot() || msg.isWebhookMessage()) continue;
            String content = msg.getContentRaw();
            if (content == null || content.isBlank()) continue;
            if (!HAS_DIGIT.matcher(content).find()) continue;
            Long n = extractCountNumber(content);
            if (n != null && n >= 0L) {
                return n;
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
        plugin.getLogger().info("[Discord Counting] Compteur chargé depuis fichier : " + value);
    }

    private void setAndSave(long value) {
        current.set(value);
        synchronized (saveLock) {
            File file = new File(plugin.getDataFolder(), STATE_FILE);
            FileConfiguration yaml = new YamlConfiguration();
            yaml.set("current", value);
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
}
