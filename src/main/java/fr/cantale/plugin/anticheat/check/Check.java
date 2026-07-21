package fr.cantale.plugin.anticheat.check;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Classe abstraite de base pour tous les checks anti-cheat.
 * Chaque check spécifique (Speed, Fly, Reach…) hérite de cette classe
 * et implémente la logique de détection dans {@link #check(Player, PlayerData)}.
 */
public abstract class Check {

    protected final CheckType type;
    protected final AntiCheatConfig config;

    protected Check(CheckType type, AntiCheatConfig config) {
        this.type = type;
        this.config = config;
    }

    /**
     * Exécute la vérification sur le joueur avec ses données courantes.
     *
     * @param player le joueur à vérifier
     * @param data   les données anti-cheat du joueur
     * @return le résultat du check (violation ou pass)
     */
    public abstract CheckResult check(Player player, PlayerData data);

    /**
     * Vérifie si ce check est activé dans la configuration.
     */
    public boolean isEnabled() {
        return config.isCheckEnabled(type);
    }

    /**
     * Retourne la pénalité de trust configurée pour ce check.
     */
    public int getTrustPenalty() {
        return config.getTrustPenalty(type);
    }

    public CheckType getType() {
        return type;
    }
}
