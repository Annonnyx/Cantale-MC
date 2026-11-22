package fr.cantale.plugin.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretResolverTest {

    @Test
    void environmentOverridesFileAndFileIsFallback() {
        SecretResolver resolver = resolver(Map.of("CANTALE_DB_PASS", "from-env"), List.of());
        assertEquals("from-env", resolver.resolve("CANTALE_DB_PASS", "from-file", "database.mysql.password", true));

        SecretResolver fallback = resolver(Map.of(), List.of());
        assertEquals("from-file", fallback.resolve("CANTALE_DB_HOST", "from-file", "database.mysql.host", true));
    }

    @Test
    void missingPlaceholderDoesNotLeakAndWarnsOnce() {
        List<String> warnings = new ArrayList<>();
        Map<String, String> env = new HashMap<>();
        SecretResolver resolver = new SecretResolver(env::get, warnings::add);
        String secretLooking = "fixture-secret-must-not-be-logged";

        String first = resolver.resolve("CANTALE_DB_PASS", "${CANTALE_DB_PASS}", "database.mysql.password", true);
        String second = resolver.resolve("CANTALE_DB_PASS", secretLooking.startsWith("fixture")
                ? "${CANTALE_DB_PASS}" : secretLooking, "database.mysql.password", true);

        assertEquals("", first);
        assertEquals("", second);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("CANTALE_DB_PASS"));
        assertFalse(warnings.get(0).contains("fixture-secret"));
        assertFalse(warnings.get(0).contains("${"));
    }

    @Test
    void usableFileValueIsNotWrittenToTheWarning() {
        List<String> warnings = new ArrayList<>();
        SecretResolver resolver = new SecretResolver(key -> null, warnings::add);
        String value = "fixture-secret-must-not-be-logged";

        assertEquals(value, resolver.resolve("CANTALE_DISCORD_TOKEN", value, "discord.token", true));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void firstUsableSkipsBlankOverride() {
        SecretResolver resolver = resolver(Map.of(), List.of());
        assertEquals("from-tokens", resolver.firstUsable(
                "CANTALE_VOTE_SERVEURPRIVE_TOKEN", "vote", false, "", "${CANTALE_VOTE_SERVEURPRIVE_TOKEN}", "from-tokens"));
    }

    private static SecretResolver resolver(Map<String, String> env, List<String> warnings) {
        return new SecretResolver(env::get, warnings::add);
    }
}
