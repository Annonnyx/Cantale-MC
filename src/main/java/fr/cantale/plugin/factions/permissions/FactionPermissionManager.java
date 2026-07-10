package fr.cantale.plugin.factions.permissions;

import fr.cantale.plugin.factions.FactionRank;

import java.util.Map;

/**
 * Point d'accès aux permissions de faction (fixes par grade).
 */
public class FactionPermissionManager {

    public boolean hasPermission(int factionId, FactionRank rank, FactionPermission perm) {
        return FactionPermissions.has(rank, perm);
    }

    public Map<FactionPermission, Boolean> getPermissions(int factionId, FactionRank rank) {
        return FactionPermissions.getAll(rank);
    }
}
