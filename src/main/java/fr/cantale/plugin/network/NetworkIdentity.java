package fr.cantale.plugin.network;

import fr.cantale.plugin.config.SecretResolver;

/**
 * Identité du nœud Paper et activation Velocity.
 * Le secret n'est jamais lu depuis le YAML : seul {@code CANTALE_VELOCITY_SECRET} allume le mode proxy.
 */
public final class NetworkIdentity {

    public static final String DEFAULT_SERVER_ID = "paper-1";
    public static final String ENV_SERVER_ID = "CANTALE_SERVER_ID";
    public static final String ENV_VELOCITY_SECRET = "CANTALE_VELOCITY_SECRET";

    private NetworkIdentity() {
    }

    public static boolean velocityEnabled(String environmentSecret) {
        return SecretResolver.isUsable(environmentSecret);
    }

    /**
     * Environnement, puis fichier, puis {@code paper-1}.
     * Caractères hors {@code [A-Za-z0-9._-]} retirés, longueur bornée à 32.
     */
    public static String serverId(String environmentValue, String fileValue) {
        if (SecretResolver.isUsable(environmentValue)) {
            return sanitize(environmentValue);
        }
        if (fileValue != null && !fileValue.isBlank() && !SecretResolver.isPlaceholder(fileValue)) {
            return sanitize(fileValue);
        }
        return DEFAULT_SERVER_ID;
    }

    static String sanitize(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        StringBuilder out = new StringBuilder(Math.min(trimmed.length(), 32));
        for (int i = 0; i < trimmed.length() && out.length() < 32; i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.isEmpty() ? DEFAULT_SERVER_ID : out.toString();
    }
}
