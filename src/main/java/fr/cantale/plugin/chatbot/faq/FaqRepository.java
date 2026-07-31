package fr.cantale.plugin.chatbot.faq;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Charge et recharge la base de connaissances faq.yml.
 */
public class FaqRepository {

    private final Logger logger = Logger.getLogger(FaqRepository.class.getName());
    private final File file;

    private FaqConfig config;
    private List<FaqEntry> entries = Collections.emptyList();

    public FaqRepository(File file) {
        this.file = file;
    }

    public void load() {
        if (!file.exists()) {
            logger.warning("faq.yml introuvable : " + file.getAbsolutePath());
            this.config = new FaqConfig(new YamlConfiguration());
            this.entries = Collections.emptyList();
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.config = new FaqConfig(yaml);
        this.entries = loadEntries(yaml);
        logger.info("FAQ chargée : " + entries.size() + " entrées.");
    }

    private List<FaqEntry> loadEntries(FileConfiguration yaml) {
        List<FaqEntry> result = new ArrayList<>();
        if (!yaml.isList("entries")) {
            logger.warning("faq.yml ne contient pas de section 'entries'.");
            return result;
        }

        List<?> rawEntries = yaml.getList("entries");
        for (Object obj : rawEntries) {
            if (!(obj instanceof java.util.Map<?, ?> rawMap)) continue;
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;

            String id = String.valueOf(map.getOrDefault("id", ""));
            if (id.isBlank()) continue;

            List<String> triggers = new ArrayList<>();
            Object triggersObj = map.get("triggers");
            if (triggersObj instanceof List<?> list) {
                for (Object t : list) {
                    if (t != null) triggers.add(String.valueOf(t));
                }
            }

            String response = String.valueOf(map.getOrDefault("response", ""));
            int cooldown = parseInt(map.getOrDefault("cooldown-seconds", 0));
            List<String> channels = new ArrayList<>();
            Object channelsObj = map.get("discord-channels");
            if (channelsObj instanceof List<?> list) {
                for (Object c : list) {
                    if (c != null) channels.add(String.valueOf(c));
                }
            }
            boolean enabled = Boolean.parseBoolean(String.valueOf(map.getOrDefault("enabled", "true")));

            result.add(new FaqEntry(id, triggers, response, cooldown, channels, enabled));
        }

        return result;
    }

    private int parseInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public FaqConfig loadAndGetConfig() {
        load();
        return config;
    }

    public FaqConfig getConfig() {
        return config;
    }

    public List<FaqEntry> getEntries() {
        return entries;
    }
}
