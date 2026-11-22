package fr.cantale.plugin.lang;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogTest {

    @Test
    void everyKeyExistsInBothBundles() throws Exception {
        MessageCatalog catalog = load();
        assertEquals(catalog.frenchKeys(), catalog.englishKeys());
        assertTrue(catalog.frenchKeys().contains("prefix"));
        assertTrue(catalog.frenchKeys().contains("pass.locked"));
        assertTrue(catalog.englishKeys().contains("daily.already"));
    }

    @Test
    void missingKeyFallsBackToFrenchThenToTheKey() throws Exception {
        MessageCatalog catalog = load();
        assertTrue(catalog.resolve("en", "welcome.lives").contains("Welcome"));
        assertEquals(catalog.resolve("fr", "ban.zero"), catalog.resolve("fr", "ban.zero"));
        assertEquals("this.key.is.absent", catalog.resolve("en", "this.key.is.absent"));
        assertEquals("still.missing", catalog.resolve("fr", "still.missing"));
        assertEquals("Bonjour Noa", new MessageCatalog(
                java.util.Map.of("only.fr", "Bonjour {0}"),
                java.util.Map.of()).format("en", "only.fr", "Noa"));
    }

    @Test
    void langAcceptsOnlyFrenchAndEnglish() {
        assertEquals("fr", LanguageCodes.canonical("FR"));
        assertEquals("en", LanguageCodes.canonical(" en "));
        assertNull(LanguageCodes.canonical("english"));
        assertNull(LanguageCodes.canonical("fr_fr"));
        assertNull(LanguageCodes.canonical("de"));
        assertEquals("fr", LanguageCodes.orFrench("es"));
    }

    private static MessageCatalog load() throws Exception {
        try (InputStream french = MessageCatalogTest.class.getResourceAsStream("/messages_fr.yml");
             InputStream english = MessageCatalogTest.class.getResourceAsStream("/messages_en.yml")) {
            return MessageCatalog.load(french, english);
        }
    }
}
