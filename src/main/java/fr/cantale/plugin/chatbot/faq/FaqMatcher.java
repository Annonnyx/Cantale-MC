package fr.cantale.plugin.chatbot.faq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Moteur de matching entre un message normalisé et les triggers de la FAQ.
 *
 * Principe anti faux-positifs :
 * - les mots "vides" (comment, on, se, une, le…) ne comptent presque pas ;
 * - seuls les mots de contenu (faction, connecter, spawn…) portent le score ;
 * - un trigger sans mot de contenu ne peut pas matcher.
 */
public final class FaqMatcher {

    private static final int LEVENSHTEIN_TOLERANCE = 2;

    /**
     * Mots trop génériques pour porter un match à eux seuls.
     * Sans ça, "comment on se connecte" matche "comment on fait une faction"
     * uniquement grâce à "comment" + "on".
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "comment", "on", "se", "je", "tu", "il", "elle", "nous", "vous", "ils", "elles",
        "le", "la", "les", "un", "une", "des", "du", "de", "d", "a", "au", "aux",
        "et", "ou", "en", "dans", "pour", "avec", "sur", "par", "est", "sont",
        "faire", "fait", "fais", "peux", "peut", "puis", "quoi", "quel", "quelle",
        "quels", "quelles", "qui", "quand", "pourquoi", "combien",
        "c", "ca", "ce", "cet", "cette", "ces", "mon", "ton", "son", "ma", "ta", "sa",
        "mes", "tes", "ses", "y", "me", "te", "moi", "toi", "lui", "leur", "leurs",
        "que", "qu", "ne", "pas", "plus", "tres", "bien", "tout", "tous", "toute",
        "avoir", "etre", "aller", "va", "vais", "vont", "veux", "veut", "voudrais"
    );

    private FaqMatcher() {
    }

    public static MatchResult findBestMatch(String normalizedMessage, List<FaqEntry> entries, double threshold) {
        FaqEntry bestEntry = null;
        double bestScore = 0.0;

        for (FaqEntry entry : entries) {
            if (!entry.isEnabled()) continue;
            double entryScore = scoreEntry(normalizedMessage, entry);
            if (entryScore > bestScore) {
                bestScore = entryScore;
                bestEntry = entry;
            }
        }

        if (bestEntry == null || bestScore < threshold) {
            return null;
        }
        return new MatchResult(bestEntry, bestScore);
    }

    private static double scoreEntry(String message, FaqEntry entry) {
        double bestTriggerScore = 0.0;
        for (String trigger : entry.getTriggers()) {
            String normalizedTrigger = FaqNormalizer.normalize(trigger);
            if (normalizedTrigger.isBlank()) continue;
            double score = scoreTrigger(message, normalizedTrigger);
            if (score > bestTriggerScore) {
                bestTriggerScore = score;
            }
        }
        return bestTriggerScore;
    }

    private static double scoreTrigger(String message, String trigger) {
        // Match exact de phrase (mots entiers, dans l'ordre) → score max
        if (phraseContainedAsWords(message, trigger)) {
            // Un trigger purement composé de stop-words ne doit jamais gagner
            if (contentWords(FaqNormalizer.words(trigger)).isEmpty()) {
                return 0.0;
            }
            return 100.0;
        }

        String[] messageWords = FaqNormalizer.words(message);
        String[] triggerWords = FaqNormalizer.words(trigger);
        if (triggerWords.length == 0) return 0.0;

        List<String> triggerContent = contentWords(triggerWords);
        List<String> messageContent = contentWords(messageWords);

        // Trigger sans contenu significatif → silence
        if (triggerContent.isEmpty()) {
            return 0.0;
        }

        // Message sans contenu (juste "comment on ?" etc.) → silence
        if (messageContent.isEmpty()) {
            return 0.0;
        }

        Set<Integer> usedMessageWords = new HashSet<>();
        double matchedWeight = 0.0;

        for (String triggerWord : triggerContent) {
            boolean wordMatched = false;
            for (int i = 0; i < messageContent.size(); i++) {
                if (usedMessageWords.contains(i)) continue;
                if (messageContent.get(i).equals(triggerWord)) {
                    matchedWeight += 1.0;
                    usedMessageWords.add(i);
                    wordMatched = true;
                    break;
                }
            }
            if (!wordMatched) {
                for (int i = 0; i < messageContent.size(); i++) {
                    if (usedMessageWords.contains(i)) continue;
                    String messageWord = messageContent.get(i);
                    int maxLen = Math.max(triggerWord.length(), messageWord.length());
                    if (maxLen > 2) {
                        int distance = levenshtein(messageWord, triggerWord);
                        if (distance <= LEVENSHTEIN_TOLERANCE) {
                            double bonus = (distance == 1) ? 0.75 : 0.35;
                            matchedWeight += bonus;
                            usedMessageWords.add(i);
                            break;
                        }
                    }
                }
            }
        }

        // Aucun mot de contenu matché → silence (même si des stop-words matchent)
        if (matchedWeight <= 0.0) {
            return 0.0;
        }

        double triggerCoverage = matchedWeight / triggerContent.size();

        // Pénalité si le message a beaucoup de contenu non couvert par le trigger
        // (évite qu'un trigger court vole une question plus précise).
        double messageCoverage = matchedWeight / messageContent.size();
        double score = ((triggerCoverage * 0.7) + (messageCoverage * 0.3)) * 100.0;

        // Bonus léger si TOUS les mots de contenu du trigger matchent exactement
        if (matchedWeight >= triggerContent.size()) {
            score = Math.min(100.0, score + 10.0);
        }

        return score;
    }

    private static List<String> contentWords(String[] words) {
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            if (word.equals("?")) continue;
            if (STOP_WORDS.contains(word)) continue;
            // Ignore les mots trop courts (sauf s'ils sont des commandes/abréviations utiles)
            if (word.length() <= 1) continue;
            result.add(word);
        }
        return result;
    }

    private static boolean phraseContainedAsWords(String message, String trigger) {
        String[] words = FaqNormalizer.words(trigger);
        if (words.length == 0) return false;
        StringBuilder regex = new StringBuilder(".*\\b");
        for (int i = 0; i < words.length; i++) {
            regex.append(Pattern.quote(words[i]));
            if (i < words.length - 1) {
                regex.append("\\s+");
            }
        }
        regex.append("\\b.*");
        return message.matches(regex.toString());
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[b.length()];
    }

    public record MatchResult(FaqEntry entry, double score) {
    }
}
