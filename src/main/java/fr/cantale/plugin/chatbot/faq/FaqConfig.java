package fr.cantale.plugin.chatbot.faq;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration du bot FAQ, chargée depuis faq.yml.
 */
public class FaqConfig {

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 70.0;
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;
    private static final String DEFAULT_UNMATCHED_LOG = "unmatched-questions.log";

    private final double confidenceThreshold;
    private final int cooldownSeconds;
    private final boolean requireQuestionMarker;
    private final boolean requireExplicitQuestion;
    private final boolean replyInPublic;
    private final List<Long> discordChannels;
    private final String unmatchedLog;

    public FaqConfig(FileConfiguration config) {
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            this.confidenceThreshold = clamp(settings.getDouble("confidence-threshold", DEFAULT_CONFIDENCE_THRESHOLD), 0.0, 100.0);
            this.cooldownSeconds = Math.max(0, settings.getInt("cooldown-seconds", DEFAULT_COOLDOWN_SECONDS));
            this.requireQuestionMarker = settings.getBoolean("require-question-marker", true);
            this.requireExplicitQuestion = settings.getBoolean("require-explicit-question", true);
            this.replyInPublic = settings.getBoolean("reply-in-public", false);
            this.unmatchedLog = settings.getString("unmatched-log", DEFAULT_UNMATCHED_LOG);
            this.discordChannels = parseChannelIds(settings.getStringList("discord-channels"));
        } else {
            this.confidenceThreshold = DEFAULT_CONFIDENCE_THRESHOLD;
            this.cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
            this.requireQuestionMarker = true;
            this.requireExplicitQuestion = true;
            this.replyInPublic = false;
            this.unmatchedLog = DEFAULT_UNMATCHED_LOG;
            this.discordChannels = Collections.emptyList();
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Long> parseChannelIds(List<String> raw) {
        List<Long> result = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isRequireQuestionMarker() {
        return requireQuestionMarker;
    }

    public boolean isRequireExplicitQuestion() {
        return requireExplicitQuestion;
    }

    public boolean isReplyInPublic() {
        return replyInPublic;
    }

    public List<Long> getDiscordChannels() {
        return discordChannels;
    }

    public String getUnmatchedLog() {
        return unmatchedLog;
    }
}
