package fr.cantale.plugin.chatbot.faq;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Normalise le texte pour le matching.
 * - minuscules
 * - retire les accents
 * - retire les emojis et caractères spéciaux
 * - supprime les répétitions de lettres (coooooomment → comment)
 * - expande les abréviations
 * - retire les mots de politesse optionnels
 */
public final class FaqNormalizer {

    private static final Map<String, String> ABBREVIATIONS = new HashMap<>();
    private static final Set<String> POLITENESS_WORDS = Set.of(
        "salut", "bonjour", "hey", "slt", "bonsoir", "merci",
        "s il te plait", "sil te plait", "stp", "plz", "pls",
        "s il vous plait", "sil vous plait", "svp",
        "bjr", "bsr", "cc", "yo", "re",
        "dsl", "deso", "pardon",
        "mdr", "ptdr", "xptdr", "lol", "lmao", "rofl",
        "jsp", "jsaispa", "chepa", "chaispas", "idk",
        "jpp", "tkt", "tqt", "tktp",
        "jtm", "jtmr",
        "thx", "ty", "tysm", "np",
        "gg", "wp", "gl", "hf",
        "btw", "imo", "imoo",
        "k", "ok", "okk", "okkk", "okay",
        "nn", "nan", "nop", "nope",
        "oe", "oue", "ouais", "ui", "wi", "yes", "yep"
    );

    static {
        // mots interrogatifs
        ABBREVIATIONS.put("cmt", "comment");
        ABBREVIATIONS.put("comen", "comment");
        ABBREVIATIONS.put("commen", "comment");
        ABBREVIATIONS.put("komen", "comment");
        ABBREVIATIONS.put("komant", "comment");
        ABBREVIATIONS.put("commant", "comment");
        ABBREVIATIONS.put("coment", "comment");
        ABBREVIATIONS.put("comant", "comment");

        ABBREVIATIONS.put("pk", "pourquoi");
        ABBREVIATIONS.put("pq", "pourquoi");
        ABBREVIATIONS.put("prk", "pourquoi");
        ABBREVIATIONS.put("pouquoi", "pourquoi");
        ABBREVIATIONS.put("pkoi", "pourquoi");
        ABBREVIATIONS.put("pourkoi", "pourquoi");
        ABBREVIATIONS.put("prq", "pourquoi");

        ABBREVIATIONS.put("c", "c est");
        ABBREVIATIONS.put("cest", "c est");
        ABBREVIATIONS.put("cs", "c est");
        ABBREVIATIONS.put("ckoi", "c est quoi");
        ABBREVIATIONS.put("ckwa", "c est quoi");
        ABBREVIATIONS.put("cq", "c est quoi");
        ABBREVIATIONS.put("ki", "qui");

        ABBREVIATIONS.put("q", "quoi");
        ABBREVIATIONS.put("koi", "quoi");
        ABBREVIATIONS.put("kwa", "quoi");
        ABBREVIATIONS.put("wtf", "quoi");

        ABBREVIATIONS.put("qd", "quand");

        // politesse / salutations
        ABBREVIATIONS.put("stp", "s il te plait");
        ABBREVIATIONS.put("svp", "s il vous plait");
        ABBREVIATIONS.put("plz", "s il te plait");
        ABBREVIATIONS.put("pls", "s il te plait");
        ABBREVIATIONS.put("bjr", "bonjour");
        ABBREVIATIONS.put("bsr", "bonsoir");
        ABBREVIATIONS.put("cc", "coucou");

        // verbes / pronoms
        ABBREVIATIONS.put("g", "j ai");
        ABBREVIATIONS.put("jai", "j ai");
        ABBREVIATIONS.put("jpe", "je peux");
        ABBREVIATIONS.put("jpeu", "je peux");
        ABBREVIATIONS.put("jpeux", "je peux");
        ABBREVIATIONS.put("jp", "je peux");
        ABBREVIATIONS.put("jfe", "je fais");
        ABBREVIATIONS.put("peu", "peux");
        ABBREVIATIONS.put("pr", "pour");
        ABBREVIATIONS.put("jui", "je suis");
        ABBREVIATIONS.put("jsuis", "je suis");
        ABBREVIATIONS.put("chu", "je suis");
        ABBREVIATIONS.put("chui", "je suis");
        ABBREVIATIONS.put("jve", "je veux");
        ABBREVIATIONS.put("jvais", "je vais");
        ABBREVIATIONS.put("ta", "tu as");
        ABBREVIATIONS.put("tas", "tu as");
        ABBREVIATIONS.put("t", "tu");
        ABBREVIATIONS.put("tes", "tu es");
        ABBREVIATIONS.put("faut", "il faut");

        // adverbes / connecteurs
        ABBREVIATIONS.put("ya", "y a");
        ABBREVIATIONS.put("sa", "ca");
        ABBREVIATIONS.put("tj", "toujours");
        ABBREVIATIONS.put("tjs", "toujours");
        ABBREVIATIONS.put("pck", "parce que");
        ABBREVIATIONS.put("pcq", "parce que");
        ABBREVIATIONS.put("parske", "parce que");
        ABBREVIATIONS.put("parceke", "parce que");
        ABBREVIATIONS.put("fct", "fonctionne");
        ABBREVIATIONS.put("tb", "tres bien");
        ABBREVIATIONS.put("bcp", "beaucoup");
        ABBREVIATIONS.put("tt", "tout");
        ABBREVIATIONS.put("tts", "toutes");
        ABBREVIATIONS.put("avc", "avec");
        ABBREVIATIONS.put("ac", "avec");
        ABBREVIATIONS.put("ss", "sans");
        ABBREVIATIONS.put("rdv", "rendez vous");

        // quantités / pronoms indefinis
        ABBREVIATIONS.put("qq", "quelqu un");
        ABBREVIATIONS.put("qqn", "quelqu un");
        ABBREVIATIONS.put("qlq", "quelqu un");
        ABBREVIATIONS.put("qlqn", "quelqu un");
        ABBREVIATIONS.put("qqun", "quelqu un");
        ABBREVIATIONS.put("qqch", "quelque chose");
        ABBREVIATIONS.put("qlqch", "quelque chose");

        // communication
        ABBREVIATIONS.put("msg", "message");
        ABBREVIATIONS.put("mp", "message prive");
        ABBREVIATIONS.put("dm", "message prive");
        ABBREVIATIONS.put("rep", "repondre");

        // actions / teleports
        ABBREVIATIONS.put("tp", "teleporter");
        ABBREVIATIONS.put("telep", "teleporter");
        ABBREVIATIONS.put("teleporte", "teleporter");

        // commandes / permissions / config
        ABBREVIATIONS.put("cmd", "commande");
        ABBREVIATIONS.put("cmds", "commandes");
        ABBREVIATIONS.put("perm", "permission");
        ABBREVIATIONS.put("perms", "permissions");
        ABBREVIATIONS.put("cfg", "configuration");
        ABBREVIATIONS.put("conf", "configuration");
        ABBREVIATIONS.put("inv", "inventaire");
        ABBREVIATIONS.put("ench", "enchantement");
        ABBREVIATIONS.put("enchs", "enchantements");

        // progression
        ABBREVIATIONS.put("xp", "experience");
        ABBREVIATIONS.put("lvl", "niveau");
        ABBREVIATIONS.put("lv", "niveau");

        // economie / boutique
        ABBREVIATIONS.put("eco", "economie");
        ABBREVIATIONS.put("money", "argent");
        ABBREVIATIONS.put("cash", "argent");
        ABBREVIATIONS.put("shop", "boutique");
        ABBREVIATIONS.put("store", "boutique");

        // grades / staff
        ABBREVIATIONS.put("vip", "grade");
        ABBREVIATIONS.put("premium", "grade");
        ABBREVIATIONS.put("modo", "moderateur");
        ABBREVIATIONS.put("modos", "moderateurs");
        ABBREVIATIONS.put("admin", "administrateur");
        ABBREVIATIONS.put("admins", "administrateurs");

        // serveur / discord / minecraft
        ABBREVIATIONS.put("srv", "serveur");
        ABBREVIATIONS.put("serv", "serveur");
        ABBREVIATIONS.put("disc", "discord");
        ABBREVIATIONS.put("dc", "discord");
        ABBREVIATIONS.put("mc", "minecraft");

        // factions
        ABBREVIATIONS.put("fac", "faction");
        ABBREVIATIONS.put("facs", "factions");
        ABBREVIATIONS.put("fation", "faction");
        ABBREVIATIONS.put("facton", "faction");
        ABBREVIATIONS.put("facion", "faction");

        // problèmes / mises à jour
        ABBREVIATIONS.put("bug", "probleme");
        ABBREVIATIONS.put("pb", "probleme");
        ABBREVIATIONS.put("prob", "probleme");
        ABBREVIATIONS.put("maj", "mise a jour");
        ABBREVIATIONS.put("upd", "mise a jour");

        // fautes d'orthographe courantes
        ABBREVIATIONS.put("monaie", "monnaie");
        ABBREVIATIONS.put("monnais", "monnaie");
        ABBREVIATIONS.put("monney", "monnaie");
        ABBREVIATIONS.put("serveurr", "serveur");
        ABBREVIATIONS.put("serveure", "serveur");
        ABBREVIATIONS.put("disocrd", "discord");
        ABBREVIATIONS.put("dicord", "discord");
        ABBREVIATIONS.put("gradee", "grade");
        ABBREVIATIONS.put("grde", "grade");
        ABBREVIATIONS.put("grdae", "grade");
        ABBREVIATIONS.put("itm", "item");
        ABBREVIATIONS.put("spawnn", "spawn");
        ABBREVIATIONS.put("spwan", "spawn");
        ABBREVIATIONS.put("spawne", "spawn");

        // reactions (inutiles pour le matching, neutralisées par POLITENESS_WORDS)
        ABBREVIATIONS.put("mdr", "mdr");
        ABBREVIATIONS.put("ptdr", "mdr");
        ABBREVIATIONS.put("lol", "lol");
        ABBREVIATIONS.put("omg", "oh mon dieu");
    }

    private FaqNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) return "";

        String text = input.toLowerCase();

        // Supprime les accents
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Supprime les emojis et symboles spéciaux, garde les lettres, chiffres et espaces
        text = text.replaceAll("[^\\p{L}\\p{N}\\s?]", " ");

        // Remplace les apostrophes cassées par espace
        text = text.replace("'", " ").replace("’", " ").replace("`", " ");

        // Supprime les répétitions de lettres (3+ identiques → 1)
        text = text.replaceAll("(.)\\1{2,}", "$1");

        // Éclate en mots
        String[] words = text.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isBlank()) continue;

            // Expansion d'abréviations
            String expanded = word;
            if (ABBREVIATIONS.containsKey(word)) {
                String candidate = ABBREVIATIONS.get(word);
                // Évite "c'est" (c est) -> "c est est" quand le mot suivant est déjà "est"
                if (word.equals("c") && i + 1 < words.length && words[i + 1].equals("est")) {
                    expanded = word;
                } else {
                    expanded = candidate;
                }
            }
            builder.append(expanded).append(' ');
        }

        return builder.toString().trim().replaceAll("\\s+", " ");
    }

    public static String stripPolitenessWords(String normalized) {
        StringBuilder builder = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (!POLITENESS_WORDS.contains(word)) {
                builder.append(word).append(' ');
            }
        }
        return builder.toString().trim();
    }

    public static String[] words(String normalized) {
        return normalized.isBlank() ? new String[0] : normalized.split("\\s+");
    }
}
