package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Gestion des tickets légendaires gagnés en jeu.
 * Chaque ticket est numéroté, enregistré en base, et un salon Discord est ouvert automatiquement.
 */
public class LegendaryTicketManager {

    private static final EnumSet<Permission> OPEN_PERMS =
        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES);

    private final CantalePlugin plugin;

    public LegendaryTicketManager(CantalePlugin plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS legendary_tickets (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    ticket_number VARCHAR(32) NOT NULL UNIQUE,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    discord_id VARCHAR(32),
                    status VARCHAR(16) DEFAULT 'OPEN',
                    created_at INTEGER DEFAULT 0,
                    used_at INTEGER DEFAULT 0
                )
                """);
        } catch (Exception e) {
            plugin.getLogger().warning("[LegendaryTicket] Impossible de créer/vérifier la table : " + e.getMessage());
        }
    }

    /**
     * Appelé quand un joueur gagne un ticket légendaire.
     */
    public void onTicketWon(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String ticketNumber = generateTicketNumber(player);
            if (ticketNumber == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§8[§6Cantale§8] §cErreur lors de la création du ticket légendaire. Contacte un administrateur."));
                return;
            }
            String discordId = getLinkedDiscordId(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§8[§6Cantale§8] §6Tu as obtenu le §lTicket Légendaire §r§6#" + ticketNumber + "§6 !");
                player.sendMessage("§7Un ticket Discord a été ouvert automatiquement pour échanger avec le staff.");
                openDiscordTicket(player, ticketNumber, discordId);
            });
        });
    }

    private String generateTicketNumber(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        long now = System.currentTimeMillis() / 1000;
        String discordId = getLinkedDiscordId(player.getUniqueId());

        String insert = "INSERT INTO legendary_tickets (ticket_number, player_uuid, player_name, discord_id, status, created_at) VALUES (?, ?, ?, ?, 'OPEN', ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "TMP");
            ps.setString(2, uuid);
            ps.setString(3, name);
            ps.setString(4, discordId);
            ps.setLong(5, now);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    String ticketNumber = String.format("LT-%05d", id);
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE legendary_tickets SET ticket_number = ? WHERE id = ?")) {
                        upd.setString(1, ticketNumber);
                        upd.setInt(2, id);
                        upd.executeUpdate();
                    }
                    return ticketNumber;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LegendaryTicket] Erreur génération numéro : " + e.getMessage());
        }
        return null;
    }

    private String getLinkedDiscordId(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT discord_id FROM discord_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("discord_id");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LegendaryTicket] Erreur récupération lien Discord : " + e.getMessage());
        }
        return null;
    }

    private void openDiscordTicket(Player player, String ticketNumber, String discordId) {
        DiscordBot bot = plugin.getDiscordBot();
        if (bot == null || !bot.isConnected()) {
            plugin.getLogger().warning("[LegendaryTicket] Bot Discord non connecté, ticket " + ticketNumber + " non ouvert.");
            return;
        }
        JDA jda = bot.getJda();
        var config = bot.getConfig();
        Guild guild = jda.getGuildById(config.getGuildId());
        if (guild == null) {
            plugin.getLogger().warning("[LegendaryTicket] Guild Discord introuvable.");
            return;
        }

        String playerName = player.getName();
        String sanitizedPlayer = playerName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String channelName = "legendary-" + ticketNumber.toLowerCase().replace("lt-", "") + "-" + sanitizedPlayer;

        guild.createTextChannel(channelName)
            .setParent(null)
            .addPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL))
            .queue(ch -> {
                // Permissions staff
                for (Long roleId : config.getStaffRoleIds()) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        ch.getManager().putPermissionOverride(role, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue();
                    }
                }

                // Membre lié
                Member member = null;
                if (discordId != null) {
                    member = guild.getMemberById(discordId);
                    if (member != null) {
                        ch.getManager().putPermissionOverride(member, OPEN_PERMS, EnumSet.noneOf(Permission.class)).queue();
                    }
                }

                long modPingRoleId = config.getModeratorPingRoleId();
                String modPing = modPingRoleId > 0 ? "<@&" + modPingRoleId + "> " : "";

                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0xFFD700))
                    .setTitle("🎟️ Ticket Légendaire — " + ticketNumber)
                    .setDescription("**" + playerName + "** a obtenu un Ticket Légendaire en jeu.\n"
                        + "Numéro unique : **" + ticketNumber + "**\n"
                        + "Compte Discord lié : " + (member != null ? member.getAsMention() : (discordId != null ? "<@" + discordId + ">" : "Non lié")))
                    .setFooter("Ticket ouvert automatiquement", null)
                    .setTimestamp(Instant.now());

                ch.sendMessage(modPing + "Nouveau ticket légendaire !")
                    .setEmbeds(embed.build())
                    .queue();
            }, error -> plugin.getLogger().warning("[LegendaryTicket] Échec création salon : " + error.getMessage()));
    }
}
