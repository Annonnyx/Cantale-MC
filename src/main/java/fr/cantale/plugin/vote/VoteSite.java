package fr.cantale.plugin.vote;

/**
 * Représente un site de vote configuré.
 *
 * @param displayName nom affiché
 * @param url         URL du site
 * @param cooldownHours délai de vote en heures (détermine le nombre de caisses)
 */
public record VoteSite(String displayName, String url, int cooldownHours) {

    /**
     * Retourne le nombre de Cadeaux du Roi gagnés pour un vote sur ce site.
     * Règle par défaut : 24h → 3, 3h → 2, tout autre → 1.
     */
    public int crateCount() {
        if (cooldownHours >= 24) return 3;
        if (cooldownHours >= 3) return 2;
        return 1;
    }
}
