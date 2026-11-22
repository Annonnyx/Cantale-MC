package fr.cantale.plugin.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lit un secret d'abord dans l'environnement, puis dans la valeur fichier.
 * Les placeholders {@code ${CANTALE_...}} et les chaînes vides ne sont pas des secrets utilisables.
 * Aucune valeur n'est écrite dans les journaux.
 */
public final class SecretResolver {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private final Function<String, String> environment;
    private final Consumer<String> warning;

    public SecretResolver(Function<String, String> environment, Consumer<String> warning) {
        this.environment = environment == null ? key -> null : environment;
        this.warning = warning == null ? message -> { } : warning;
    }

    public static SecretResolver system(Consumer<String> warning) {
        return new SecretResolver(System::getenv, warning);
    }

    /** Réservé aux tests : réarme les avertissements « secret manquant ». */
    public static void resetWarningsForTests() {
        WARNED.clear();
    }

    public String resolve(String envKey, String fileValue, String label, boolean required) {
        return firstUsable(envKey, label, required, fileValue);
    }

    public String firstUsable(String envKey, String label, boolean required, String... fileValues) {
        String fromEnv = environment.apply(envKey);
        if (isUsable(fromEnv)) {
            return fromEnv.trim();
        }
        if (fileValues != null) {
            for (String fileValue : fileValues) {
                if (isUsable(fileValue)) {
                    return fileValue.trim();
                }
            }
        }
        if (required) {
            warnMissing(envKey, label);
        }
        return "";
    }

    public static boolean isUsable(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !isPlaceholder(trimmed);
    }

    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.length() > 3;
    }

    private void warnMissing(String envKey, String label) {
        if (!WARNED.add(envKey + "|" + label)) {
            return;
        }
        warning.accept("Secret manquant (" + label + ") : définir " + envKey
                + " ou une valeur dans la configuration. Aucun secret n'est écrit dans les logs.");
    }
}
