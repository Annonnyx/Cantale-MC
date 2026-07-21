package fr.cantale.plugin.permissions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.DatabaseManager;
import fr.cantale.plugin.rank.PlayerRank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gestionnaire de permissions unifié basé sur PlayerRank.
 * Les anciens rôles admin/moderator/vip/player sont mappés sur PlayerRank.
 */
public class PermissionManager {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public PermissionManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    // Permissions pour OWNER (tout)
    private static final Set<String> OWNER_PERMISSIONS = new HashSet<>(Arrays.asList(
        "cantale.*",
        "cantale.admin",
        "cantale.moderator",
        "cantale.vip",
        "cantale.givevie",
        "cantale.owner",
        "minecraft.*",
        "bukkit.*",
        "spigot.*"
    ));

    // Permissions pour MODERATOR
    private static final Set<String> MODERATOR_PERMISSIONS = new HashSet<>(Arrays.asList(
        "cantale.moderator",
        "cantale.vip",
        "cantale.givevie",
        "minecraft.command.kick",
        "minecraft.command.mute",
        "minecraft.command.ban",
        "minecraft.command.ban-ip",
        "minecraft.command.tp",
        "minecraft.command.teleport",
        "minecraft.command.gamemode",
        "minecraft.command.gamemode.spectator",
        "minecraft.command.msg",
        "minecraft.command.tell",
        "minecraft.command.w",
        "minecraft.command.whisper",
        "bukkit.command.kick",
        "bukkit.command.teleport",
        "bukkit.command.gamemode",
        "bukkit.command.gamemode.spectator",
        "bukkit.command.whitelist.list"
    ));

    // Permissions pour VIP
    private static final Set<String> VIP_PERMISSIONS = new HashSet<>(Arrays.asList(
        "cantale.vip",
        "minecraft.command.fly",
        "minecraft.command.gamemode",
        "minecraft.command.gamemode.survival",
        "minecraft.command.gamemode.creative",
        "minecraft.command.home",
        "minecraft.command.sethome",
        "minecraft.command.tpahere",
        "minecraft.command.tpa",
        "minecraft.command.warp",
        "minecraft.command.setwarp",
        "minecraft.command.delwarp"
    ));

    /**
     * Récupère le grade du joueur depuis la table player_permissions.
     * Utilise PlayerRank comme source unique.
     */
    public PlayerRank getPlayerRole(UUID uuid) {
        String sql = "SELECT role FROM player_permissions WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return PlayerRank.fromString(rs.getString("role"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPlayerRole : " + e.getMessage());
        }
        return PlayerRank.NONE;
    }

    /**
     * Définit le grade d'un joueur ( PlayerRank unique).
     */
    public void setPlayerRole(UUID uuid, PlayerRank rank) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?)"
                : "INSERT INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = VALUES(updated_at)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, rank.name());
            stmt.setInt(3, (int)(System.currentTimeMillis() / 1000));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setPlayerRole : " + e.getMessage());
        }
    }

    /**
     * Compatibilité avec l'ancien système : accepte les anciens noms de rôle.
     * admin -> ADMIN, moderator -> MODERATOR, vip -> VIP, player -> NONE, owner -> OWNER
     */
    public void setPlayerRole(UUID uuid, String roleName) {
        PlayerRank rank = PlayerRank.fromString(roleName);
        setPlayerRole(uuid, rank);
    }

    /**
     * Vérifie une permission string en fonction du PlayerRank.
     */
    public boolean hasPermission(UUID uuid, String permission) {
        PlayerRank rank = getPlayerRole(uuid);

        if (rank == PlayerRank.OWNER) {
            return true;
        }

        Set<String> rolePermissions = getPermissionsForRole(rank);

        if (rolePermissions.contains(permission)) {
            return true;
        }

        for (String perm : rolePermissions) {
            if (perm.endsWith(".*")) {
                String prefix = perm.substring(0, perm.length() - 1);
                if (permission.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Vérifie si le joueur a au moins le grade requis.
     */
    public boolean hasPermission(UUID uuid, PlayerRank requiredRank) {
        PlayerRank playerRank = getPlayerRole(uuid);
        return playerRank.ordinal() >= requiredRank.ordinal();
    }

    private Set<String> getPermissionsForRole(PlayerRank rank) {
        return switch (rank) {
            case OWNER, ADMIN -> OWNER_PERMISSIONS;
            case MODERATOR -> MODERATOR_PERMISSIONS;
            case VIP -> VIP_PERMISSIONS;
            default -> new HashSet<>();
        };
    }

    public void removePlayerRole(UUID uuid) {
        String sql = "DELETE FROM player_permissions WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur removePlayerRole : " + e.getMessage());
        }
    }

}
