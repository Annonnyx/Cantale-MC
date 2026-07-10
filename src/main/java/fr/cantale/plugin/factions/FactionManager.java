package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.factions.permissions.FactionPermission;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FactionManager {

    private final CantalePlugin plugin;
    private final FactionDAO factionDAO;
    private final Map<Integer, Faction> loadedFactions;
    private final Map<UUID, Integer> playerFactionCache;
    private final Map<String, Claim> claimCache;
    private final Map<UUID, Boolean> autoClaimEnabled;

    public FactionManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionDAO = new FactionDAO(plugin);
        this.loadedFactions = new ConcurrentHashMap<>();
        this.playerFactionCache = new ConcurrentHashMap<>();
        this.claimCache = new ConcurrentHashMap<>();
        this.autoClaimEnabled = new ConcurrentHashMap<>();
    }

    public Faction createFaction(String name, String tag, String description, UUID leader) {
        if (factionDAO.getFactionByName(name) != null) {
            return null;
        }

        Faction faction = new Faction(name, tag, description, leader);
        int id = factionDAO.createFaction(faction);
        if (id > 0) {
            faction.setId(id);
            cacheFaction(faction);
            return faction;
        }
        return null;
    }

    public Faction getFaction(int id) {
        Faction cached = loadedFactions.get(id);
        if (cached != null) {
            return cached;
        }
        Faction faction = factionDAO.getFactionById(id);
        if (faction != null) {
            cacheFaction(faction);
        }
        return faction;
    }

    public Faction getFaction(String name) {
        for (Faction faction : loadedFactions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        Faction faction = factionDAO.getFactionByName(name);
        if (faction != null) {
            cacheFaction(faction);
        }
        return faction;
    }

    public Faction getFactionByTag(String tag) {
        for (Faction faction : loadedFactions.values()) {
            if (faction.getTag().equalsIgnoreCase(tag)) {
                return faction;
            }
        }
        Faction faction = factionDAO.getFactionByTag(tag);
        if (faction != null) {
            cacheFaction(faction);
        }
        return faction;
    }

    public Faction getPlayerFaction(UUID playerUuid) {
        Integer cachedId = playerFactionCache.get(playerUuid);
        if (cachedId != null) {
            return getFaction(cachedId);
        }

        Faction faction = factionDAO.getFactionByPlayer(playerUuid);
        if (faction != null) {
            cacheFaction(faction);
            playerFactionCache.put(playerUuid, faction.getId());
        }
        return faction;
    }

    public List<Faction> getAllFactions() {
        return factionDAO.getAllFactions();
    }

    public boolean deleteFaction(int id) {
        Faction faction = getFaction(id);
        if (faction == null) return false;

        // Supprimer tous les claims de la faction
        for (Claim claim : new ArrayList<>(faction.getClaims())) {
            removeClaim(claim.getWorld(), claim.getChunkX(), claim.getChunkZ());
        }

        // Clear cache
        loadedFactions.remove(id);
        for (UUID member : faction.getMembers()) {
            playerFactionCache.remove(member);
        }

        return factionDAO.deleteFaction(id);
    }

    public void saveFaction(Faction faction) {
        factionDAO.updateFaction(faction);
    }

    public boolean addMember(int factionId, UUID playerUuid, FactionRank rank) {
        Faction faction = getFaction(factionId);
        if (faction == null) return false;

        factionDAO.addMember(factionId, playerUuid, rank);
        faction.addMember(playerUuid, rank);
        playerFactionCache.put(playerUuid, factionId);
        faction.checkPromotion();
        saveFaction(faction);
        return true;
    }

    public boolean removeMember(int factionId, UUID playerUuid) {
        Faction faction = getFaction(factionId);
        if (faction == null) return false;

        factionDAO.removeMember(factionId, playerUuid);
        faction.removeMember(playerUuid);
        playerFactionCache.remove(playerUuid);
        
        // Si c'était le dernier membre, détruire la faction
        if (faction.getMembers().isEmpty()) {
            deleteFaction(factionId);
            return true;
        }
        
        saveFaction(faction);
        return true;
    }

    public boolean setMemberRank(int factionId, UUID playerUuid, FactionRank rank) {
        Faction faction = getFaction(factionId);
        if (faction == null) return false;

        factionDAO.updateMemberRank(factionId, playerUuid, rank);
        faction.getMemberRanks().put(playerUuid, rank);
        return true;
    }

    public boolean addClaim(int factionId, String world, int chunkX, int chunkZ) {
        Claim existing = getClaimAt(world, chunkX, chunkZ);
        if (existing != null) {
            return false;
        }

        Faction faction = getFaction(factionId);
        if (faction == null) return false;

        Claim claim = new Claim(factionId, world, chunkX, chunkZ);
        factionDAO.addClaim(claim);
        faction.addClaim(claim);
        claimCache.put(claim.getChunkKey(), claim);
        return true;
    }

    public boolean removeClaim(String world, int chunkX, int chunkZ) {
        Claim claim = getClaimAt(world, chunkX, chunkZ);
        if (claim == null) return false;

        Faction faction = getFaction(claim.getFactionId());
        if (faction != null) {
            faction.removeClaim(claim);
        }

        factionDAO.removeClaim(claim.getId());
        claimCache.remove(claim.getChunkKey());
        return true;
    }

    public Claim getClaimAt(String world, int chunkX, int chunkZ) {
        String key = world + "," + chunkX + "," + chunkZ;
        return claimCache.computeIfAbsent(key, k -> factionDAO.getClaimAt(world, chunkX, chunkZ));
    }

    public Faction getFactionAtClaim(String world, int chunkX, int chunkZ) {
        Claim claim = getClaimAt(world, chunkX, chunkZ);
        if (claim != null) {
            return getFaction(claim.getFactionId());
        }
        return null;
    }

    public boolean autoClaim(UUID playerUuid, String world, int chunkX, int chunkZ) {
        // Vérifier si l'auto-claim est activé pour ce joueur
        if (!autoClaimEnabled.getOrDefault(playerUuid, false)) {
            return false;
        }

        Claim existing = getClaimAt(world, chunkX, chunkZ);
        if (existing != null) return false; // Déjà claimé

        Faction faction = getPlayerFaction(playerUuid);
        if (faction == null) return false;

        return addClaim(faction.getId(), world, chunkX, chunkZ);
    }

    public void setAutoClaim(UUID playerUuid, boolean enabled) {
        autoClaimEnabled.put(playerUuid, enabled);
    }

    public boolean isAutoClaimEnabled(UUID playerUuid) {
        return autoClaimEnabled.getOrDefault(playerUuid, false);
    }

    public void checkAndAdjustClaims(int factionId) {
        Faction faction = getFaction(factionId);
        if (faction == null) return;

        int maxClaims = faction.getPower() / 10;
        int currentClaims = faction.getClaimCount();

        if (currentClaims <= maxClaims) return;

        int claimsToRemove = currentClaims - maxClaims;
        List<Claim> claimsToRemoveList = new ArrayList<>(faction.getClaims());

        // Mélanger la liste pour un choix aléatoire
        Collections.shuffle(claimsToRemoveList);

        // Supprimer le nombre de claims nécessaires
        int removed = 0;
        for (Claim claim : claimsToRemoveList) {
            if (removed >= claimsToRemove) break;
            removeClaim(claim.getWorld(), claim.getChunkX(), claim.getChunkZ());
            removed++;
        }
    }

    public boolean hasPermission(UUID playerUuid, FactionPermission permission) {
        Faction faction = getPlayerFaction(playerUuid);
        return fr.cantale.plugin.factions.permissions.FactionPermissions.has(faction, playerUuid, permission);
    }

    private void cacheFaction(Faction faction) {
        loadedFactions.put(faction.getId(), faction);
        for (UUID member : faction.getMembers()) {
            playerFactionCache.put(member, faction.getId());
        }
        for (Claim claim : faction.getClaims()) {
            claimCache.put(claim.getChunkKey(), claim);
        }
    }

    public void reload() {
        loadedFactions.clear();
        playerFactionCache.clear();
        claimCache.clear();
    }
}
