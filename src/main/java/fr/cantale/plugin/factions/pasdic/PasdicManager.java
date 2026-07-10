package fr.cantale.plugin.factions.pasdic;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PasdicManager {
    private final CantalePlugin plugin;
    private final Set<Integer> pasdicClaims;
    private final Set<UUID> councilMembers;

    public PasdicManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.pasdicClaims = ConcurrentHashMap.newKeySet();
        this.councilMembers = ConcurrentHashMap.newKeySet();
    }

    public void addCouncilMember(UUID uuid) {
        councilMembers.add(uuid);
    }

    public boolean isCouncilMember(UUID uuid) {
        return councilMembers.contains(uuid);
    }

    public void approvePasdic(int claimId, UUID approver) {
        pasdicClaims.add(claimId);
        Bukkit.broadcastMessage("§8[§bPASDIC§8] §aUn nouveau claim protégé a été approuvé!");
    }

    public boolean isPasdic(int claimId) {
        return pasdicClaims.contains(claimId);
    }

    public void setAdventureMode(Player player) {
        if (isInPasdic(player)) {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage("§8[§bPASDIC§8] §7Mode Adventure forcé - Zone protégée");
        }
    }

    public void restoreGameMode(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
    }

    private boolean isInPasdic(Player player) {
        return false;
    }

    public Set<Integer> getPasdicClaims() {
        return Set.copyOf(pasdicClaims);
    }
}
