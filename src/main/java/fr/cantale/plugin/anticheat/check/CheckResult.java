package fr.cantale.plugin.anticheat.check;

/**
 * Résultat d'une vérification anti-cheat.
 * Contient si une violation a été détectée, le score de pénalité,
 * le type de check et les détails pour le log/alerte.
 */
public class CheckResult {

    private final boolean violated;
    private final double score;
    private final String details;
    private final CheckType type;

    private CheckResult(boolean violated, double score, String details, CheckType type) {
        this.violated = violated;
        this.score = score;
        this.details = details;
        this.type = type;
    }

    /**
     * Crée un résultat indiquant une violation.
     */
    public static CheckResult violation(CheckType type, double score, String details) {
        return new CheckResult(true, score, details, type);
    }

    /**
     * Crée un résultat indiquant qu'aucune violation n'a été détectée.
     */
    public static CheckResult pass(CheckType type) {
        return new CheckResult(false, 0, "", type);
    }

    public boolean isViolated() {
        return violated;
    }

    public double getScore() {
        return score;
    }

    public String getDetails() {
        return details;
    }

    public CheckType getType() {
        return type;
    }
}
