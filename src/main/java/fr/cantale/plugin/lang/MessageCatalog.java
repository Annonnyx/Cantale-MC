package fr.cantale.plugin.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Deux bundles plats {@code cle: "valeur"}. Clé absente : français, puis la clé elle-même.
 * Le remplacement {@code {0}} évite {@link java.text.MessageFormat} (apostrophes françaises).
 */
public final class MessageCatalog {

    private final Map<String, String> french;
    private final Map<String, String> english;

    public MessageCatalog(Map<String, String> french, Map<String, String> english) {
        this.french = Map.copyOf(french);
        this.english = Map.copyOf(english);
    }

    public static MessageCatalog load(InputStream frenchStream, InputStream englishStream) throws IOException {
        return new MessageCatalog(read(frenchStream), read(englishStream));
    }

    public Set<String> frenchKeys() {
        return french.keySet();
    }

    public Set<String> englishKeys() {
        return english.keySet();
    }

    public String resolve(String lang, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (LanguageCodes.EN.equals(lang)) {
            String englishValue = english.get(key);
            if (englishValue != null) {
                return englishValue;
            }
        }
        String frenchValue = french.get(key);
        return frenchValue != null ? frenchValue : key;
    }

    public String format(String lang, String key, Object... args) {
        String pattern = resolve(LanguageCodes.orFrench(lang), key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        String formatted = pattern;
        for (int i = 0; i < args.length; i++) {
            String value = args[i] == null ? "" : String.valueOf(args[i]);
            formatted = formatted.replace("{" + i + "}", value);
        }
        return formatted;
    }

    static Map<String, String> read(InputStream input) throws IOException {
        if (input == null) {
            return Map.of();
        }
        Map<String, String> keys = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int colon = trimmed.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, colon).trim();
                String raw = trimmed.substring(colon + 1).trim();
                if (key.isEmpty()) {
                    continue;
                }
                keys.put(key, unquote(raw));
            }
        }
        return Collections.unmodifiableMap(keys);
    }

    private static String unquote(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
        }
        return raw;
    }
}
