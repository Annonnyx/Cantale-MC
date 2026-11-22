package fr.cantale.plugin.siege;

import fr.cantale.plugin.skills.SkillFormulas;

import java.util.HashMap;
import java.util.Map;

/**
 * Compte les tentatives de casse d'un bloc pendant un siège.
 * Sans architecte, la première tentative passe. Le plafond est dans {@link SkillFormulas}.
 */
public final class SiegeBreakPace {

    private final Map<String, Integer> hits = new HashMap<>();

    public boolean ready(String blockKey, int architects) {
        int needed = SkillFormulas.architectBreaksRequired(architects);
        int seen = hits.getOrDefault(blockKey, 0) + 1;
        if (seen >= needed) {
            hits.remove(blockKey);
            return true;
        }
        hits.put(blockKey, seen);
        return false;
    }
}
