package fr.cantale.plugin.lang;

import java.util.Locale;

/** {@code /lang} n'accepte que {@code fr} et {@code en}. */
public final class LanguageCodes {

    public static final String FR = "fr";
    public static final String EN = "en";

    private LanguageCodes() {
    }

    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (FR.equals(trimmed) || EN.equals(trimmed)) {
            return trimmed;
        }
        return null;
    }

    public static String orFrench(String raw) {
        String canonical = canonical(raw);
        return canonical == null ? FR : canonical;
    }
}
