package fr.cantale.plugin.chatbot.faq;

import java.util.Collections;
import java.util.List;

/**
 * Représente une entrée de la FAQ.
 */
public class FaqEntry {

    private final String id;
    private final List<String> triggers;
    private final String response;
    private final int cooldownSeconds;
    private final List<String> discordChannels;
    private final boolean enabled;

    public FaqEntry(String id, List<String> triggers, String response, int cooldownSeconds, List<String> discordChannels, boolean enabled) {
        this.id = id;
        this.triggers = triggers != null ? List.copyOf(triggers) : Collections.emptyList();
        this.response = response != null ? response : "";
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.discordChannels = discordChannels != null ? List.copyOf(discordChannels) : Collections.emptyList();
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public String getResponse() {
        return response;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public List<String> getDiscordChannels() {
        return discordChannels;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
