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
 *
 * Thread-safety : les maps internes sont synchronisées — le decay tourne sur le
 * thread principal (tâche sync) mais des violations peuvent remonter du bridge
 * Grim après hop sur le main thread ; la synchronisation protège tout accès
 * concurrent résiduel.
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
        synchronized (playerViolations) {
            int current = playerViolations.getOrDefault(type, 0) + 1;
            playerViolations.put(type, current);
            return current;
        }
    }

    /**
     * Retourne le nombre de violations pour un joueur et un type.
     */
    public int getViolations(UUID playerId, CheckType type) {
        Map<CheckType, Integer> playerViolations = violations.get(playerId);
        if (playerViolations == null) return 0;
        synchronized (playerViolations) {
            return playerViolations.getOrDefault(type, 0);
        }
    }

    /**
     * Retourne le nombre total de violations pour un joueur (tous types confondus).
     */
    public int getTotalViolations(UUID playerId) {
        Map<CheckType, Integer> playerViolations = violations.get(playerId);
        if (playerViolations == null) return 0;
        synchronized (playerViolations) {
            return playerViolations.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Retourne une copie des violations d'un joueur.
     */
    public Map<CheckType, Integer> getAllViolations(UUID playerId) {
        Map<CheckType, Integer> playerViolations = violations.get(playerId);
        if (playerViolations == null) return new EnumMap<>(CheckType.class);
        synchronized (playerViolations) {
            return new EnumMap<>(playerViolations);
        }
    }

    /**
     * Réinitialise toutes les violations d'un joueur.
     */
    public void clearViolations(UUID playerId) {
        violations.remove(playerId);
    }

    /**
     * Applique la décroissance (decay) des violations.
     * Appelé périodiquement (thread principal) pour réduire les compteurs.
     * Retire 1 violation par type toutes les X secondes.
     */
    public void decayAll() {
        for (Map.Entry<UUID, Map<CheckType, Integer>> entry : violations.entrySet()) {
            Map<CheckType, Integer> playerViolations = entry.getValue();
            synchronized (playerViolations) {
                playerViolations.replaceAll((type, count) -> Math.max(0, count - 1));
                playerViolations.entrySet().removeIf(e -> e.getValue() <= 0);
            }
        }
        violations.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Supprime toutes les données d'un joueur (déconnexion).
     */
    public void removePlayer(UUID playerId) {
        violations.remove(playerId);
    }
}
