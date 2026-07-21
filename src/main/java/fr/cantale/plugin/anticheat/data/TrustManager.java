package fr.cantale.plugin.anticheat.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le score de confiance (trust) de chaque joueur.
 *
 * Règles :
 * - Score de départ = valeur configurée (défaut 100).
 * - Chaque violation retire des points selon la gravité du check.
 * - Si le joueur est calme, le score remonte lentement.
 * - Plus le score est bas, plus les vérifications sont strictes (seuils réduits).
 */
public class TrustManager {

    private final Map<UUID, Integer> trustScores = new ConcurrentHashMap<>();
    private final int defaultTrust;
    private final int recoveryPerMinute;

    public TrustManager(int defaultTrust, int recoveryPerMinute) {
        this.defaultTrust = defaultTrust;
        this.recoveryPerMinute = recoveryPerMinute;
    }

    /**
     * Retourne le score de confiance d'un joueur (initialise si absent).
     */
    public int getTrust(UUID playerId) {
        return trustScores.getOrDefault(playerId, defaultTrust);
    }

    /**
     * Retire des points de trust suite à une violation.
     *
     * @param playerId le joueur concerné
     * @param penalty  le nombre de points à retirer
     * @return le nouveau score
     */
    public int removeTrust(UUID playerId, int penalty) {
        int current = getTrust(playerId);
        int newScore = Math.max(0, current - penalty);
        trustScores.put(playerId, newScore);
        return newScore;
    }

    /**
     * Récupère du trust pour tous les joueurs actifs (appelé périodiquement).
     * Chaque joueur récupère {@code recoveryPerMinute} points, sans dépasser {@code defaultTrust}.
     */
    public void recoverAll() {
        for (Map.Entry<UUID, Integer> entry : trustScores.entrySet()) {
            int current = entry.getValue();
            if (current < defaultTrust) {
                entry.setValue(Math.min(defaultTrust, current + recoveryPerMinute));
            }
        }
    }

    /**
     * Réinitialise le score d'un joueur à la valeur par défaut.
     */
    public void resetTrust(UUID playerId) {
        trustScores.put(playerId, defaultTrust);
    }

    /**
     * Supprime les données d'un joueur (déconnexion).
     */
    public void removePlayer(UUID playerId) {
        trustScores.remove(playerId);
    }

    /**
     * Calcule un multiplicateur de sévérité basé sur le trust.
     * Trust bas = checks plus stricts.
     *
     * @return un multiplicateur entre 1.0 (trust plein) et 2.0 (trust 0)
     */
    public double getSeverityMultiplier(UUID playerId) {
        int trust = getTrust(playerId);
        if (trust >= defaultTrust) return 1.0;
        // Linéaire : trust 0 → multiplicateur 2.0, trust max → multiplicateur 1.0
        return 1.0 + (1.0 - ((double) trust / defaultTrust));
    }

    public int getDefaultTrust() {
        return defaultTrust;
    }
}
