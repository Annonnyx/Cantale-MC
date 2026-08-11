package fr.cantale.plugin.discord;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service central des liaisons Discord ↔ Minecraft ({@code discord_links}).
 * Utilisé par {@code /link} (joueur) et {@code /linkforce} (admin).
 */
public class DiscordLinkService {

    private static final Pattern DISCORD_SNOWFLAKE = Pattern.compile("^[0-9]{17,20}$");
    private static final long JOUEUR_ROLE_ID = 1504909677786300577L;

    private final CantalePlugin plugin;

    public DiscordLinkService(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isValidDiscordSnowflake(String discordId) {
        if (discordId == null || discordId.isBlank()) return false;
        String trimmed = discordId.trim();
        if (!DISCORD_SNOWFLAKE.matcher(trimmed).matches()) return false;
        try {
            long id = Long.parseUnsignedLong(trimmed);
            // Snowflake Discord : epoch 2015 ; refuse les IDs trop petits
            return id >= 1_000_000_000_000_000L;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Résout un joueur MC par pseudo : online → table {@code players} → OfflinePlayer connu.
     */
    public ResolvedPlayer resolveMinecraftPlayer(String username) {
        if (username == null || username.isBlank()) return null;
        String name = username.trim();

        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    online = p;
                    break;
                }
            }
        }
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId().toString(), online.getName());
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT uuid, username FROM players WHERE username = ? COLLATE utf8mb4_general_ci LIMIT 1")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ResolvedPlayer(rs.getString("uuid"), rs.getString("username"));
                }
            }
        } catch (Exception e) {
            // SQLite n'a pas COLLATE utf8mb4_general_ci — fallback case-insensitive
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid, username FROM players WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new ResolvedPlayer(rs.getString("uuid"), rs.getString("username"));
                    }
                }
            } catch (Exception e2) {
                plugin.getLogger().warning("[DiscordLink] Résolution joueur DB: " + e2.getMessage());
            }
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            String resolvedName = offline.getName() != null ? offline.getName() : name;
            return new ResolvedPlayer(offline.getUniqueId().toString(), resolvedName);
        }

        return null;
    }

    /**
     * Force la liaison MC ↔ Discord : écrase les anciennes associations
     * (même UUID MC et/ou même Discord ID), comme le {@code /link} Discord.
     */
    public ForceLinkResult forceLink(String mcUsername, String discordIdRaw,
                                     String discordUsername, String discordDisplayName) {
        String discordId = discordIdRaw == null ? "" : discordIdRaw.trim();
        if (!isValidDiscordSnowflake(discordId)) {
            return ForceLinkResult.fail("ID Discord invalide. Attendu : snowflake numérique (17–20 chiffres).");
        }

        ResolvedPlayer player = resolveMinecraftPlayer(mcUsername);
        if (player == null) {
            return ForceLinkResult.fail("Joueur Minecraft introuvable : « " + mcUsername + " » "
                + "(ni en ligne, ni dans la table players, ni connu du serveur).");
        }

        List<String> conflicts = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Conflits existants
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT discord_id, uuid FROM discord_links WHERE uuid = ? OR discord_id = ?")) {
                stmt.setString(1, player.uuid());
                stmt.setString(2, discordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String oldDiscord = rs.getString("discord_id");
                        String oldUuid = rs.getString("uuid");
                        if (player.uuid().equals(oldUuid) && discordId.equals(oldDiscord)) {
                            conflicts.add("liaison déjà identique (aucun changement de mapping)");
                        } else if (player.uuid().equals(oldUuid)) {
                            conflicts.add("MC " + player.username() + " était lié à Discord " + oldDiscord);
                        } else {
                            conflicts.add("Discord " + oldDiscord + " était lié à UUID " + oldUuid);
                        }
                    }
                }
            }

            // Invalider les codes /link en attente pour ce UUID
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE link_codes SET used = 1 WHERE uuid = ? AND used = 0")) {
                stmt.setString(1, player.uuid());
                stmt.executeUpdate();
            }

            // Upsert : même logique que handleLink Discord
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM discord_links WHERE uuid = ? OR discord_id = ?")) {
                stmt.setString(1, player.uuid());
                stmt.setString(2, discordId);
                stmt.executeUpdate();
            }

            String username = discordUsername;
            String displayName = discordDisplayName;
            if ((username == null || displayName == null) && plugin.getDiscordBot() != null
                && plugin.getDiscordBot().isConnected()) {
                try {
                    var jda = plugin.getDiscordBot().getJda();
                    var user = jda.retrieveUserById(discordId).complete();
                    if (user != null) {
                        if (username == null) username = user.getName();
                        if (displayName == null) {
                            displayName = user.getGlobalName() != null ? user.getGlobalName() : user.getName();
                        }
                    }
                } catch (Exception ignored) {
                    // Membre hors cache / ID hors serveur : on lie quand même
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO discord_links (discord_id, uuid, linked_at, discord_username, discord_display_name) "
                    + "VALUES (?, ?, ?, ?, ?)")) {
                stmt.setString(1, discordId);
                stmt.setString(2, player.uuid());
                stmt.setLong(3, System.currentTimeMillis() / 1000);
                stmt.setString(4, username);
                stmt.setString(5, displayName);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordLink] forceLink error: " + e.getMessage());
            return ForceLinkResult.fail("Erreur base de données lors de la liaison : " + e.getMessage());
        }

        // Rôles Discord (Joueur + faction) — best effort
        applyPostLinkRoles(player.uuid(), discordId);

        StringBuilder msg = new StringBuilder();
        msg.append("Liaison forcée : **").append(player.username()).append("** (`")
            .append(player.uuid()).append("`) ↔ Discord `").append(discordId).append("`");
        if (!conflicts.isEmpty()) {
            msg.append("\nAnciennes associations remplacées : ").append(String.join(" ; ", conflicts));
        }
        return ForceLinkResult.ok(msg.toString(), player, discordId, conflicts);
    }

    private void applyPostLinkRoles(String uuidStr, String discordId) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;
        try {
            UUID playerUuid = UUID.fromString(uuidStr);
            var jda = plugin.getDiscordBot().getJda();
            var guild = jda.getGuildById(plugin.getDiscordBot().getConfig().getGuildId());
            if (guild == null) return;

            guild.retrieveMemberById(discordId).queue(member -> {
                var joueurRole = guild.getRoleById(JOUEUR_ROLE_ID);
                if (joueurRole != null) {
                    guild.addRoleToMember(member, joueurRole).queue(
                        success -> {},
                        error -> plugin.getLogger().warning(
                            "[DiscordLink] Rôle Joueur: " + error.getMessage())
                    );
                }
                boolean inFaction = plugin.getFactionManager() != null
                    && plugin.getFactionManager().getPlayerFaction(playerUuid) != null;
                plugin.getDiscordBot().syncFactionRoleForPlayer(playerUuid, inFaction);
            }, error -> plugin.getLogger().info(
                "[DiscordLink] Membre Discord " + discordId + " introuvable sur la guilde (lien DB OK)."));
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordLink] applyPostLinkRoles: " + e.getMessage());
        }
    }

    public record ResolvedPlayer(String uuid, String username) {}

    public record ForceLinkResult(
        boolean success,
        String message,
        ResolvedPlayer player,
        String discordId,
        List<String> conflicts
    ) {
        public static ForceLinkResult ok(String message, ResolvedPlayer player, String discordId, List<String> conflicts) {
            return new ForceLinkResult(true, message, player, discordId, conflicts);
        }

        public static ForceLinkResult fail(String message) {
            return new ForceLinkResult(false, message, null, null, List.of());
        }

        /** Message Minecraft (codes couleur §). */
        public String toMinecraftMessage() {
            String plain = message
                .replace("**", "")
                .replace("`", "");
            if (success) {
                return "§8[§6Cantale§8] §a" + plain.replace("\n", "\n§8[§6Cantale§8] §7");
            }
            return "§8[§6Cantale§8] §c" + plain;
        }
    }
}
