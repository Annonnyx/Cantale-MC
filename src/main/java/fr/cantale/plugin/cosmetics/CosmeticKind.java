package fr.cantale.plugin.cosmetics;

import java.util.List;
import java.util.Locale;

public enum CosmeticKind {
    TITLE,
    AURA,
    CAPE;

    public static CosmeticKind fromArgument(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "title", "titre" -> TITLE;
            case "aura" -> AURA;
            case "cape" -> CAPE;
            default -> null;
        };
    }
}
