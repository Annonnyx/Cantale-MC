package fr.cantale.plugin.chatbot.faq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FaqCoreTest {

    @Test
    void normalizerExpandsAbbreviationsAndRemovesAccents() {
        String input = "cmt jfé pr crée une faction ???";
        String normalized = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize(input));
        assertEquals("comment je fais pour cree une faction ?", normalized);
    }

    @Test
    void questionDetectorFindsQuestions() {
        assertTrue(FaqQuestionDetector.isQuestion(FaqNormalizer.normalize("comment creer une faction")));
        assertTrue(FaqQuestionDetector.isQuestion("ou est le spawn?"));
        assertFalse(FaqQuestionDetector.isQuestion(FaqNormalizer.normalize("gg mdr j arrive")));
    }

    @Test
    void explicitQuestionDetectorOnlyRespondsToClearQuestions() {
        assertTrue(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("comment creer une faction")));
        assertTrue(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("ou est le spawn")));
        assertTrue(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("c est quoi la monnaie ?")));
        assertTrue(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("est ce que ca fonctionne")));
        // Pas assez explicite : silence
        assertFalse(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("je sais pas comment faire")));
        assertFalse(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("la faction fonctionne bien")));
        assertFalse(FaqQuestionDetector.isExplicitQuestion(FaqNormalizer.normalize("gg mdr")));
    }

    @Test
    void matcherFindsFactionEntry() {
        FaqEntry entry = new FaqEntry("faction_create",
            List.of("comment creer une faction", "creer une faction", "faction"),
            "Réponse faction", 0, List.of(), true);

        String message = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize("salut cmt jfé pr crée une faction ???"));
        FaqMatcher.MatchResult result = FaqMatcher.findBestMatch(message, List.of(entry), 60.0);
        assertNotNull(result);
        assertEquals("faction_create", result.entry().getId());
        assertTrue(result.score() >= 60.0);
    }

    @Test
    void matcherPrefersConnectOverFaction() {
        FaqEntry faction = new FaqEntry("faction_create",
            List.of("comment creer une faction", "comment on fait une faction", "faction"),
            "Réponse faction", 0, List.of(), true);
        FaqEntry ip = new FaqEntry("ip",
            List.of("comment se connecter", "comment on se connecte", "ip serveur"),
            "Réponse ip", 0, List.of(), true);

        String message = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize("comment on se connecte ?"));
        FaqMatcher.MatchResult result = FaqMatcher.findBestMatch(message, List.of(faction, ip), 70.0);
        assertNotNull(result);
        assertEquals("ip", result.entry().getId());
    }

    @Test
    void matcherStaysSilentOnVagueCommentOn() {
        FaqEntry faction = new FaqEntry("faction_create",
            List.of("comment creer une faction", "comment on fait une faction"),
            "Réponse faction", 0, List.of(), true);

        String message = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize("comment on se connecte ?"));
        FaqMatcher.MatchResult result = FaqMatcher.findBestMatch(message, List.of(faction), 70.0);
        assertNull(result);
    }

    @Test
    void matcherIgnoresNonQuestionsWhenRequired() {
        FaqEntry entry = new FaqEntry("money",
            List.of("monnaie", "argent", "cantox"),
            "Réponse money", 0, List.of(), true);

        String message = FaqNormalizer.stripPolitenessWords(FaqNormalizer.normalize("gg mdr"));
        FaqMatcher.MatchResult result = FaqMatcher.findBestMatch(message, List.of(entry), 60.0);
        assertNull(result);
    }
}
