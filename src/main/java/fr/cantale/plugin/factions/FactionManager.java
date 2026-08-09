package fr.cantale.plugin.factions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.factions.permissions.FactionPermission;

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
        faction.setPower(calculateInitialPower());
        int id = factionDAO.createFaction(faction);
        if (id > 0) {
            faction.setId(id);
            cacheFaction(faction);
            return faction;
        }
        return null;
    }

    private int calculateInitialPower() {
        return 60; // 6 claims de départ (power/10), bonus minimal de 5 claims via getMaxClaims()
    }

    /**
     * Ajoute du pouvoir à la faction d'un joueur. Retourne true si le joueur a une faction.
     * Les pertes sont plafonnées à 0 et déclenchent un ajustement des claims.
     */
    public boolean addPowerToPlayerFaction(UUID playerUuid, int amount) {
        Faction faction = getPlayerFaction(playerUuid);
        if (faction == null) return false;

        if (amount >= 0) {
            faction.addPower(amount);
        } else {
            faction.removePower(-amount);
        }
        factionDAO.updatePower(faction.getId(), faction.getPower());

        if (amount < 0) {
            checkAndAdjustClaims(faction.getId());
        }
        return true;
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

        // Les membres de la faction perdent le rôle Discord de faction.
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            for (UUID member : faction.getMembers()) {
                plugin.getDiscordBot().syncFactionRoleForPlayer(member, false);
            }
        }

        // Supprimer tous les claims de la faction
        for (Claim claim : new ArrayList<>(faction.getClaims())) {
            removeClaim(claim.getWorld(), claim.getChunkX(), claim.getChunkZ());
        }

        // Nettoyer les chunks fortifiés (au cas où un claim aurait été perdu sans passer par removeClaim)
        if (plugin.getFortifiedChunkDAO() != null) {
            plugin.getFortifiedChunkDAO().removeAllForFaction(id);
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

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().syncFactionRoleForPlayer(playerUuid, true);
        }
        return true;
    }

    public boolean removeMember(int factionId, UUID playerUuid) {
        Faction faction = getFaction(factionId);
        if (faction == null) return false;

        factionDAO.removeMember(factionId, playerUuid);
        faction.removeMember(playerUuid);
        playerFactionCache.remove(playerUuid);

        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().syncFactionRoleForPlayer(playerUuid, false);
        }
        
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

    /**
     * Distance minimale (en chunks, distance de Chebyshev) entre un nouveau claim
     * et les claims des autres factions. Les claims de la même faction peuvent
     * rester adjacents pour permettre l'expansion d'une base.
     */
    public static final int MIN_ENEMY_CLAIM_DISTANCE = 2;

    public boolean isTooCloseToOtherFaction(String world, int chunkX, int chunkZ, int factionId) {
        for (int dx = -MIN_ENEMY_CLAIM_DISTANCE; dx <= MIN_ENEMY_CLAIM_DISTANCE; dx++) {
            for (int dz = -MIN_ENEMY_CLAIM_DISTANCE; dz <= MIN_ENEMY_CLAIM_DISTANCE; dz++) {
                if (dx == 0 && dz == 0) continue;
                Claim claim = getClaimAt(world, chunkX + dx, chunkZ + dz);
                if (claim != null && claim.getFactionId() != factionId) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean addClaim(int factionId, String world, int chunkX, int chunkZ) {
        Claim existing = getClaimAt(world, chunkX, chunkZ);
        if (existing != null) {
            return false;
        }

        // Filet de sécurité central : vaut pour /f claim, l'auto-claim et tout autre appel
        if (isTooCloseToOtherFaction(world, chunkX, chunkZ, factionId)) {
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

        // Une fortification ne survit pas à la perte du claim
        if (plugin.getFortifiedChunkDAO() != null) {
            plugin.getFortifiedChunkDAO().unfortifyChunk(world, chunkX, chunkZ);
        }
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

        // Mêmes règles que /f claim : permission, quota de power, zone protégée
        if (!hasPermission(playerUuid, fr.cantale.plugin.factions.permissions.FactionPermission.ADD_CLAIMS)) {
            return false;
        }
        if (faction.getClaimCount() >= faction.getMaxClaims()) {
            return false;
        }

        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        if (bukkitWorld != null) {
            org.bukkit.Location center = new org.bukkit.Location(bukkitWorld, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
            if (!plugin.getSpawnProtection().canClaim(center)) {
                return false;
            }
        }

        // La distance minimale avec les autres factions est vérifiée dans addClaim
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

        // Même quota que /f claim (plancher de 5 claims inclus)
        int maxClaims = faction.getMaxClaims();
        int currentClaims = faction.getClaimCount();

        if (currentClaims <= maxClaims) return;

        int claimsToRemove = currentClaims - maxClaims;
        List<Claim> claimsToRemoveList = new ArrayList<>(faction.getClaims());

        // Retirer les claims les plus récents d'abord : la base historique est protégée
        claimsToRemoveList.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        int removed = 0;
        for (Claim claim : claimsToRemoveList) {
            if (removed >= claimsToRemove) break;
            removeClaim(claim.getWorld(), claim.getChunkX(), claim.getChunkZ());
            removed++;
        }

        plugin.getLogger().info("[Faction] " + faction.getName() + " : " + removed
                + " claim(s) retiré(s) (quota " + currentClaims + "/" + maxClaims + ")");
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

    private final Set<UUID> factionChatToggled = ConcurrentHashMap.newKeySet();

    public boolean isFactionChatToggled(UUID playerUuid) {
        return factionChatToggled.contains(playerUuid);
    }

    public boolean toggleFactionChat(UUID playerUuid) {
        if (factionChatToggled.contains(playerUuid)) {
            factionChatToggled.remove(playerUuid);
            return false;
        } else {
            factionChatToggled.add(playerUuid);
            return true;
        }
    }

    public void setFactionChatToggled(UUID playerUuid, boolean enabled) {
        if (enabled) factionChatToggled.add(playerUuid);
        else factionChatToggled.remove(playerUuid);
    }

    public void sendFactionMessage(org.bukkit.entity.Player sender, Faction faction, String message) {
        String formatted = "§8[§6Faction §e" + faction.getName() + "§8] §e" + sender.getName() + " : §f" + message;
        plugin.getActionLogger().logChatMessage("[Faction " + faction.getName() + "] " + sender.getName(), message);
        for (UUID memberUuid : faction.getMembers()) {
            org.bukkit.entity.Player member = org.bukkit.Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(formatted);
            }
        }
    }

    public void reload() {
        loadedFactions.clear();
        playerFactionCache.clear();
        claimCache.clear();
        factionChatToggled.clear();
    }
}
