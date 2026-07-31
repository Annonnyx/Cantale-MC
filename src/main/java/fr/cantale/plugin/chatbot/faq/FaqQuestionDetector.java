package fr.cantale.plugin.chatbot.faq;

import java.util.Set;

/**
 * Détecte si un message ressemble à une question.
 */
public final class FaqQuestionDetector {

    private static final Set<String> QUESTION_WORDS = Set.of(
        "comment", "ou", "où", "quoi", "qui", "combien", "quand", "pourquoi", "quel", "quelle",
        "quels", "quelles", "est ce que", "est-ce que", "comment faire", "comment on",
        "ou est", "ou se", "comment se", "comment je", "je peux", "puis je", "puis-je",
        "fonctionne"
    );

    /**
     * Mots qui, placés au début d'une phrase, indiquent une question explicite.
     * On reste strict : pas "fonctionne", pas "je peux", etc.
     */
    private static final Set<String> EXPLICIT_START_WORDS = Set.of(
        "comment", "ou", "où", "quoi", "qui", "combien", "quand", "pourquoi",
        "quel", "quelle", "quels", "quelles"
    );

    private FaqQuestionDetector() {
    }

    public static boolean isQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) return false;

        // Marqueur explicite
        if (normalizedMessage.contains("?")) return true;

        // Mots interrogatifs
        for (String questionWord : QUESTION_WORDS) {
            if (normalizedMessage.contains(questionWord)) return true;
        }

        return false;
    }

    /**
     * Détecte une question EXPLICITE uniquement.
     * Accepte :
     *  - un point d'interrogation
     *  - une phrase commençant par un mot interrogatif de base
     *  - la présence de "est-ce que" / "est ce que"
     * En cas de doute, renvoie false.
     */
    public static boolean isExplicitQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) return false;

        if (normalizedMessage.contains("?")) return true;

        if (normalizedMessage.contains("est-ce que") || normalizedMessage.contains("est ce que")) {
            return true;
        }

        String[] words = normalizedMessage.trim().split("\\s+");
        if (words.length > 0 && EXPLICIT_START_WORDS.contains(words[0])) {
            return true;
        }

        return false;
    }
}
