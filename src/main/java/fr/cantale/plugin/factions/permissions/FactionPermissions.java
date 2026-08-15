package fr.cantale.plugin.factions.permissions;

import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionRank;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permissions fixes par grade de faction (non configurables).
 */
public final class FactionPermissions {

    private static final Map<FactionRank, Set<FactionPermission>> BY_RANK = new EnumMap<>(FactionRank.class);

    static {
        Set<FactionPermission> recruit = EnumSet.of(
            FactionPermission.BANK_ADD
        );

        Set<FactionPermission> member = EnumSet.copyOf(recruit);
        member.addAll(EnumSet.of(
            FactionPermission.ADD_CLAIMS,
            FactionPermission.INFO_BANK,
            FactionPermission.FWARP
        ));

        Set<FactionPermission> officer = EnumSet.copyOf(member);
        officer.addAll(EnumSet.of(
            FactionPermission.REMOVE_CLAIMS,
            FactionPermission.FACCEPT,
            FactionPermission.FKICK,
            FactionPermission.BAN,
            FactionPermission.UNBAN,
            FactionPermission.BANK_TAKE,
            FactionPermission.SECRET,
            FactionPermission.RAID,
            FactionPermission.PASDIC,
            FactionPermission.WAR
        ));

        Set<FactionPermission> leader = EnumSet.allOf(FactionPermission.class);

        BY_RANK.put(FactionRank.RECRUIT, Collections.unmodifiableSet(recruit));
        BY_RANK.put(FactionRank.MEMBER, Collections.unmodifiableSet(member));
        BY_RANK.put(FactionRank.OFFICER, Collections.unmodifiableSet(officer));
        BY_RANK.put(FactionRank.LEADER, Collections.unmodifiableSet(leader));
    }

    private FactionPermissions() {}

    public static boolean has(FactionRank rank, FactionPermission permission) {
        Set<FactionPermission> perms = BY_RANK.get(rank);
        return perms != null && perms.contains(permission);
    }

    public static boolean has(Faction faction, UUID playerUuid, FactionPermission permission) {
        if (faction == null) return false;
        if (faction.isLeader(playerUuid)) return true;
        return has(faction.getMemberRank(playerUuid), permission);
    }

    public static Map<FactionPermission, Boolean> getAll(FactionRank rank) {
        Map<FactionPermission, Boolean> result = new EnumMap<>(FactionPermission.class);
        for (FactionPermission perm : FactionPermission.values()) {
            result.put(perm, has(rank, perm));
        }
        return result;
    }

    public static Set<FactionPermission> getGranted(FactionRank rank) {
        return BY_RANK.getOrDefault(rank, Collections.emptySet());
    }

    /**
     * Autorise les non-membres à récolter les cultures dans les claims de la faction.
     * Basé sur le grade Recrue (politique publique de la faction).
     */
    public static boolean allowsPublicFarming() {
        return has(FactionRank.RECRUIT, FactionPermission.CULTURES);
    }
}
