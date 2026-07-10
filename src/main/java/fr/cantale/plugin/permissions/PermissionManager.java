package fr.cantale.plugin.permissions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PermissionManager {

    private final CantalePlugin plugin;
    private final DatabaseManager databaseManager;

    public PermissionManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public enum Role {
        OWNER("owner", 4),
        ADMIN("admin", 3),
        MODERATOR("moderator", 2),
        VIP("vip", 1),
        PLAYER("player", 0);

        private final String name;
        private final int level;

        Role(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public static Role fromString(String name) {
            for (Role role : values()) {
                if (role.name.equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return PLAYER;
        }
    }

    // Permissions pour chaque rôle
    private static final Set<String> OWNER_PERMISSIONS = new HashSet<>(Arrays.asList(
        // Plugin Cantale - toutes les permissions
        "cantale.*",
        "cantale.admin",
        "cantale.moderator",
        "cantale.vip",
        "cantale.givevie",
        "cantale.owner",
        // Minecraft - toutes les permissions
        "minecraft.*",
        "bukkit.*",
        "spigot.*"
    ));

    private static final Set<String> ADMIN_PERMISSIONS = new HashSet<>(Arrays.asList(
        // Plugin Cantale - admin
        "cantale.admin",
        "cantale.moderator",
        "cantale.vip",
        "cantale.givevie",
        "cantale.customitem",
        "cantale.pasdic",
        "cantale.wanted",
        // Minecraft - admin
        "minecraft.command.*",
        "minecraft.command.ban",
        "minecraft.command.ban-ip",
        "minecraft.command.kick",
        "minecraft.command.op",
        "minecraft.command.deop",
        "minecraft.command.whitelist",
        "minecraft.command.gamemode",
        "minecraft.command.give",
        "minecraft.command.tp",
        "minecraft.command.teleport",
        "minecraft.command.effect",
        "minecraft.command.enchant",
        "minecraft.command.xp",
        "minecraft.command.time",
        "minecraft.command.weather",
        "minecraft.command.difficulty",
        "minecraft.command.stop",
        "minecraft.command.save-all",
        "minecraft.command.save-on",
        "minecraft.command.save-off",
        "bukkit.command.*",
        "bukkit.command.gamemode",
        "bukkit.command.teleport",
        "bukkit.command.kick",
        "bukkit.command.ban",
        "bukkit.command.ban.ip",
        "bukkit.command.unban",
        "bukkit.command.unban.ip",
        "bukkit.command.op",
        "bukkit.command.deop",
        "bukkit.command.whitelist",
        "bukkit.command.whitelist.add",
        "bukkit.command.whitelist.remove",
        "bukkit.command.whitelist.list",
        "bukkit.command.whitelist.reload",
        "bukkit.command.whitelist.enable",
        "bukkit.command.whitelist.disable"
    ));

    private static final Set<String> MODERATOR_PERMISSIONS = new HashSet<>(Arrays.asList(
        // Plugin Cantale - modération
        "cantale.moderator",
        "cantale.vip",
        "cantale.givevie",
        // Minecraft - modération
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

    private static final Set<String> VIP_PERMISSIONS = new HashSet<>(Arrays.asList(
        // Plugin Cantale - VIP
        "cantale.vip",
        // Minecraft - VIP
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

    public Role getPlayerRole(UUID uuid) {
        String sql = "SELECT role FROM player_permissions WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Role.fromString(rs.getString("role"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getPlayerRole : " + e.getMessage());
        }
        return Role.PLAYER;
    }

    public void setPlayerRole(UUID uuid, Role role) {
        String sql = "REPLACE INTO player_permissions (uuid, role, updated_at) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, role.getName());
            stmt.setInt(3, (int)(System.currentTimeMillis() / 1000));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setPlayerRole : " + e.getMessage());
        }
    }

    public boolean hasPermission(UUID uuid, String permission) {
        Role role = getPlayerRole(uuid);
        
        // Owner a toutes les permissions
        if (role == Role.OWNER) {
            return true;
        }

        // Vérifier les permissions selon le rôle
        Set<String> rolePermissions = getPermissionsForRole(role);
        
        // Vérification exacte
        if (rolePermissions.contains(permission)) {
            return true;
        }

        // Vérification par wildcard
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

    public boolean hasPermission(UUID uuid, Role requiredRole) {
        return getPlayerRole(uuid).getLevel() >= requiredRole.getLevel();
    }

    private Set<String> getPermissionsForRole(Role role) {
        switch (role) {
            case OWNER:
                return OWNER_PERMISSIONS;
            case ADMIN:
                return ADMIN_PERMISSIONS;
            case MODERATOR:
                return MODERATOR_PERMISSIONS;
            case VIP:
                return VIP_PERMISSIONS;
            default:
                return new HashSet<>();
        }
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
