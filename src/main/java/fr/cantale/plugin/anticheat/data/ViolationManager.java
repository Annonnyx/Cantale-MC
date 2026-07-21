package fr.cantale.plugin.anticheat.data;

import fr.cantale.plugin.anticheat.check.CheckType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les violations anti-cheat pour tous les joueurs.
 * Chaque violation est comptée par joueur et par type de check.
 * Les violations décroissent automatiquement au fil du temps (decay).
 */
public class ViolationManager {

    /** Violations par joueur → par type de check */
    private final Map<UUID, Map<CheckType, Integer>> violations = new ConcurrentHashMap<>();

    /**
     * Ajoute une violation pour un joueur sur un type de check donné.
     *
     * @return le nouveau nombre de violations pour ce joueur/type
     */
    public int addViolation(UUID playerId, CheckType type) {
        Map<CheckType, Integer> playerViolations = violations.computeIfAbsent(playerId,
                k -> new EnumMap<>(CheckType.class));
        int current = playerViolations.getOrDefault(type, 0) + 1;
        playerViolations.put(type, current);
        return current;
    }

    /**
     * Retourne le nombre de violations pour un joueur et un type.
     */
    public int getViolations(UUID playerId, CheckType type) {
        Map<CheckType, Integer> playerViolations = violations.get(playerId);
        if (playerViolations == null) return 0;
        return playerViolations.getOrDefault(type, 0);
    }

    /**
     * Retourne le nombre total de violations pour un joueur (tous types confondus).
     */
    public int getTotalViolations(UUID playerId) {
        Map<CheckType, Integer> playerViolations = violations.get(playerId);
        if (playerViolations == null) return 0;
        return playerViolations.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Retourne toutes les violations d'un joueur.
     */
    public Map<CheckType, Integer> getAllViolations(UUID playerId) {
        return violations.getOrDefault(playerId, new EnumMap<>(CheckType.class));
    }

    /**
     * Réinitialise toutes les violations d'un joueur.
     */
    public void clearViolations(UUID playerId) {
        violations.remove(playerId);
    }

    /**
     * Applique la décroissance (decay) des violations.
     * Appelé périodiquement pour réduire les compteurs et éviter l'accumulation.
     * Retire 1 violation par type toutes les X secondes.
     */
    public void decayAll() {
        for (Map.Entry<UUID, Map<CheckType, Integer>> entry : violations.entrySet()) {
            Map<CheckType, Integer> playerViolations = entry.getValue();
            playerViolations.replaceAll((type, count) -> Math.max(0, count - 1));
            // Nettoyer les entrées à 0
            playerViolations.entrySet().removeIf(e -> e.getValue() <= 0);
        }
        // Nettoyer les joueurs sans violations
        violations.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Supprime toutes les données d'un joueur (déconnexion).
     */
    public void removePlayer(UUID playerId) {
        violations.remove(playerId);
    }
}
