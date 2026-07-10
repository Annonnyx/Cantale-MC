package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PowerManager {

    private final CantalePlugin plugin;
    private final FactionDAO factionDAO;
    private final Map<Integer, Integer> cachedPower;

    public PowerManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionDAO = new FactionDAO(plugin);
        this.cachedPower = new HashMap<>();
    }

    // Calculer le pouvoir initial (600 de départ)
    public int calculateInitialPower() {
        return 600;
    }

    // Gains quotidiens
    public int calculateDailyGains(Faction faction) {
        int gains = 0;
        
        // +1 par membre actif (connecté dans les 7 derniers jours)
        int activeMembers = countActiveMembers(faction);
        gains += activeMembers;
        
        // Bonus classement mensuel (à implémenter avec un système de classement)
        // Top 1: +200, Top 2: +120, Top 3: +60
        
        // Note annuaire staff (à implémenter)
        // +5 par point (max +50)
        
        return gains;
    }

    // Pertes quotidiennes
    public int calculateDailyLosses(Faction faction) {
        int losses = 0;
        
        // -15 par mort de membre (HC)
        // À implémenter avec un système de tracking des morts
        
        // -3 par membre inactif (+14 jours)
        int inactiveMembers = countInactiveMembers(faction);
        losses += inactiveMembers * 3;
        
        // -5 pour mauvaise note annuaire (< 4/10)
        // À implémenter
        
        return losses;
    }

    // Mettre à jour le pouvoir d'une faction
    public void updateFactionPower(int factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return;

        int gains = calculateDailyGains(faction);
        int losses = calculateDailyLosses(faction);
        int netChange = gains - losses;

        faction.addPower(netChange);
        factionDAO.updatePower(factionId, faction.getPower());

        cachedPower.put(factionId, faction.getPower());

        // Ajuster les claims si nécessaire
        plugin.getFactionManager().checkAndAdjustClaims(factionId);
    }

    // Compter les membres actifs (connectés dans les 7 derniers jours)
    private int countActiveMembers(Faction faction) {
        // À implémenter avec un système de tracking de dernière connexion
        return faction.getMemberCount(); // Pour l'instant, tous les membres
    }

    // Compter les membres inactifs (+14 jours)
    private int countInactiveMembers(Faction faction) {
        // À implémenter avec un système de tracking de dernière connexion
        return 0; // Pour l'instant, aucun
    }

    // Obtenir le pouvoir depuis le cache ou le calculer
    public int getFactionPower(int factionId) {
        if (cachedPower.containsKey(factionId)) {
            return cachedPower.get(factionId);
        }
        
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction != null) {
            cachedPower.put(factionId, faction.getPower());
            return faction.getPower();
        }
        
        return 0;
    }
}
