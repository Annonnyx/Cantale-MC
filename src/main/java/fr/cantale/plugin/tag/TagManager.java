package fr.cantale.plugin.tag;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TagManager {

    private final CantalePlugin plugin;

    public TagManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    // --- Database Custom Tags Admin ---

    public boolean createCustomTag(String tagId, String displayName) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR REPLACE INTO custom_tags (tag_id, display_name) VALUES (?, ?)"
                : "INSERT INTO custom_tags (tag_id, display_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tagId.toLowerCase());
            stmt.setString(2, displayName);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur createCustomTag: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteCustomTag(String tagId) {
        String sql = "DELETE FROM custom_tags WHERE tag_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tagId.toLowerCase());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deleteCustomTag: " + e.getMessage());
            return false;
        }
    }

    public boolean updateCustomTagDisplayName(String tagId, String displayName) {
        String sql = "UPDATE custom_tags SET display_name = ? WHERE tag_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setString(2, tagId.toLowerCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur updateCustomTagDisplayName: " + e.getMessage());
            return false;
        }
    }

    public Map<String, String> getAllCustomTags() {
        Map<String, String> tags = new HashMap<>();
        String sql = "SELECT tag_id, display_name FROM custom_tags";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tags.put(rs.getString("tag_id"), rs.getString("display_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getAllCustomTags: " + e.getMessage());
        }
        return tags;
    }

    public String getCustomTagDisplayName(String tagId) {
        String sql = "SELECT display_name FROM custom_tags WHERE tag_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tagId.toLowerCase());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("display_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getCustomTagDisplayName: " + e.getMessage());
        }
        return null;
    }

    // --- Database Unlocked Tags per Player ---

    public boolean unlockTag(UUID uuid, String tagId) {
        String sql = plugin.getDatabaseManager().isSQLite()
                ? "INSERT OR IGNORE INTO player_unlocked_tags (uuid, tag_id) VALUES (?, ?)"
                : "INSERT IGNORE INTO player_unlocked_tags (uuid, tag_id) VALUES (?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur unlockTag: " + e.getMessage());
            return false;
        }
    }

    public boolean lockTag(UUID uuid, String tagId) {
        String sql = "DELETE FROM player_unlocked_tags WHERE uuid = ? AND tag_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lockTag: " + e.getMessage());
            return false;
        }
    }

    public List<String> getUnlockedTags(UUID uuid) {
        List<String> tags = new ArrayList<>();
        String sql = "SELECT tag_id FROM player_unlocked_tags WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tags.add(rs.getString("tag_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur getUnlockedTags: " + e.getMessage());
        }
        return tags;
    }

    public boolean hasUnlockedTag(UUID uuid, String tagId) {
        String sql = "SELECT 1 FROM player_unlocked_tags WHERE uuid = ? AND tag_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur hasUnlockedTag: " + e.getMessage());
            return false;
        }
    }

    // --- Player Settings for Tags ---

    public boolean isRankTagShown(UUID uuid) {
        String sql = "SELECT show_rank_tag FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("show_rank_tag") == 1;
            }
        } catch (SQLException e) {
            // Si la ligne n'existe pas encore ou erreur
        }
        return true; // Défaut
    }

    public void setRankTagShown(UUID uuid, boolean show) {
        String sql = "UPDATE player_permissions SET show_rank_tag = ? WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, show ? 1 : 0);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                // La ligne n'existe peut-être pas encore, on tente un INSERT
                String insert = "INSERT INTO player_permissions (uuid, role, updated_at, show_rank_tag) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, uuid.toString());
                    PlayerRank rank = plugin.getRankManager().getRank(uuid);
                    ins.setString(2, rank.name());
                    ins.setInt(3, (int) (System.currentTimeMillis() / 1000));
                    ins.setInt(4, show ? 1 : 0);
                    ins.executeUpdate();
                } catch (SQLException insertEx) {
                    // Si la ligne a été créée entre temps (duplicate key), on ignore
                    if (!isDuplicateKeyError(insertEx)) {
                        throw insertEx;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setRankTagShown: " + e.getMessage());
        }
    }

    public String getActiveCustomTag(UUID uuid) {
        String sql = "SELECT active_custom_tag FROM player_permissions WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("active_custom_tag");
            }
        } catch (SQLException e) {
            // Si la ligne n'existe pas encore ou erreur
        }
        return null;
    }

    public void setActiveCustomTag(UUID uuid, String tagId) {
        String sql = "UPDATE player_permissions SET active_custom_tag = ? WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tagId); // peut être null
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                // La ligne n'existe peut-être pas encore, on tente un INSERT
                String insert = "INSERT INTO player_permissions (uuid, role, updated_at, active_custom_tag) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, uuid.toString());
                    PlayerRank rank = plugin.getRankManager().getRank(uuid);
                    ins.setString(2, rank.name());
                    ins.setInt(3, (int) (System.currentTimeMillis() / 1000));
                    ins.setString(4, tagId);
                    ins.executeUpdate();
                } catch (SQLException insertEx) {
                    // Si la ligne a été créée entre temps (duplicate key), on ignore
                    if (!isDuplicateKeyError(insertEx)) {
                        throw insertEx;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur setActiveCustomTag: " + e.getMessage());
        }
    }

    // --- Prefix Generation & Scoreboard Updates ---

    /**
     * Calcule le préfixe complet pour le chat, le nametag ou le TAB.
     * Format: [Faction] [Grade] [Custom]
     * Les grades staff (Owner, Modérateur) sont affichés comme des grades classiques.
     */
    public String getPlayerPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        StringBuilder prefix = new StringBuilder();

        // 1. Tag de Faction (Mandatoire/Par défaut)
        Faction faction = plugin.getFactionManager().getPlayerFaction(uuid);
        if (faction != null) {
            prefix.append("§7[").append(faction.getTag()).append("] §r");
        }

        // 2. Tag de Grade/Rank (Optionnel)
        if (isRankTagShown(uuid)) {
            PlayerRank rank = plugin.getRankManager().getRank(uuid);
            if (rank != PlayerRank.NONE) {
                String rankColor = switch (rank) {
                    case AVENTURIER -> "§b";
                    case VIP -> "§a";
                    case CHEVRE -> "§6";
                    case MODERATOR -> "§9";
                    case ADMIN -> "§c";
                    case OWNER -> "§4";
                    default -> "§r";
                };
                prefix.append(rankColor).append("[").append(rank.getDisplayName()).append("] §r");
            }
        }

        // 3. Tag Customisé (Optionnel)
        String activeCustomTag = getActiveCustomTag(uuid);
        if (activeCustomTag != null && hasUnlockedTag(uuid, activeCustomTag)) {
            String customDisplayName = getCustomTagDisplayName(activeCustomTag);
            if (customDisplayName != null) {
                prefix.append(ChatColor.translateAlternateColorCodes('&', customDisplayName)).append(" §r");
            }
        }

        return prefix.toString();
    }

    /**
     * Met à jour le nametag (au-dessus de la tête) et le TAB du joueur via le Scoreboard.
     */
    public void updatePlayerNametag(Player player) {
        String prefix = getPlayerPrefix(player);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        
        // Nom de l'équipe unique basé sur le joueur pour éviter les conflits
        String teamName = "tag_" + player.getName();
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        // Assigner le scoreboard au joueur s'il ne l'a pas déjà
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Supprime l'équipe temporaire du joueur du scoreboard (appelé à la déconnexion).
     */
    public void removePlayerTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "tag_" + player.getName();
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    /**
     * Détecte une erreur de clé dupliquée, indépendamment du SGBD (MySQL ou SQLite).
     */
    private boolean isDuplicateKeyError(SQLException e) {
        return e.getErrorCode() == 1062
                || (e.getSQLState() != null && e.getSQLState().startsWith("23"))
                || e.getMessage().toLowerCase().contains("duplicate")
                || e.getMessage().toLowerCase().contains("unique constraint failed");
    }
}
