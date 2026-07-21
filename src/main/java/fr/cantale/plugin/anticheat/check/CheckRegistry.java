package fr.cantale.plugin.anticheat.check;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registre central des checks anti-cheat.
 * Permet d'enregistrer, récupérer et lister les checks actifs.
 */
public class CheckRegistry {

    private final Map<CheckType, Check> checks = new EnumMap<>(CheckType.class);

    /**
     * Enregistre un check dans le registre.
     */
    public void register(Check check) {
        checks.put(check.getType(), check);
    }

    /**
     * Récupère un check par son type.
     */
    public Check get(CheckType type) {
        return checks.get(type);
    }

    /**
     * Retourne tous les checks activés.
     */
    public List<Check> getEnabled() {
        List<Check> enabled = new ArrayList<>();
        for (Check check : checks.values()) {
            if (check.isEnabled()) {
                enabled.add(check);
            }
        }
        return enabled;
    }

    /**
     * Retourne tous les checks activés d'une catégorie donnée.
     */
    public List<Check> getEnabledByCategory(CheckType.Category category) {
        List<Check> enabled = new ArrayList<>();
        for (Check check : checks.values()) {
            if (check.isEnabled() && check.getType().getCategory() == category) {
                enabled.add(check);
            }
        }
        return enabled;
    }

    /**
     * Retourne tous les checks enregistrés (activés ou non).
     */
    public Map<CheckType, Check> getAll() {
        return checks;
    }

    /**
     * Vérifie si un check est enregistré.
     */
    public boolean isRegistered(CheckType type) {
        return checks.containsKey(type);
    }
}
