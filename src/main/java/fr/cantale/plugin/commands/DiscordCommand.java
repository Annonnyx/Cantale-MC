package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Commande /discord en jeu.
 *
 * Usage :
 *   /discord <message>           → envoie un message dans le salon Discord dédié (1521952439052402838)
 *   /discord <joueur> <message>  → envoie un message privé Discord au membre lié à ce joueur MC
 */
public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private static final long DISCORD_CHAT_CHANNEL_ID = 1521952439052402838L;

    public DiscordCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        DiscordBot bot = plugin.getDiscordBot();
        if (bot == null || !bot.isConnected()) {
            player.sendMessage(formatMessage("&cLe bot Discord n'est pas connecté."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String first = args[0].toLowerCase();

        // Syntaxe explicite pour MP : /discord mp <joueur> <message>
        if (first.equals("mp") || first.equals("msg") || first.equals("pm")) {
            if (args.length < 3) {
                player.sendMessage(formatMessage("&cUsage: /discord mp <joueur> <message>"));
                return true;
            }
            String targetName = args[1];
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String discordId = getDiscordIdForPlayer(targetName);
                if (discordId == null) {
                    // Fallback JDA pour les anciens liens sans noms Discord stockés
                    discordId = resolveDiscordIdByJda(targetName, bot);
                }
                final String finalDiscordId = discordId;
                if (finalDiscordId == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(formatMessage("&cCe joueur n'a pas lié son compte Discord.")));
                    return;
                }
                sendPrivateMessage(player, targetName, finalDiscordId, message, bot);
            });
            return true;
        }

        // Syntaxe legacy : /discord <joueur> <message>
        // On tente de résoudre le premier argument comme un joueur lié
        if (args.length >= 2) {
            String possiblePlayer = args[0];
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String discordId = getDiscordIdForPlayer(possiblePlayer);
                if (discordId == null) {
                    // Fallback JDA pour les anciens liens sans noms Discord stockés
                    discordId = resolveDiscordIdByJda(possiblePlayer, bot);
                }
                if (discordId != null) {
                    String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    sendPrivateMessage(player, possiblePlayer, discordId, message, bot);
                } else {
                    String message = String.join(" ", args);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sendPublicMessage(player, message, bot));
                }
            });
            return true;
        }

        // Un seul argument = message public
        sendPublicMessage(player, args[0], bot);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8[§6Cantale§8] §7Rejoins notre serveur Discord pour lier ton compte et participer aux giveaways : §ehttps://discord.gg/eDTfYWtuYp");
        player.sendMessage(formatMessage("&e/discord <message> &7— Envoie un message sur Discord"));
        player.sendMessage(formatMessage("&e/discord mp <joueur|discord> <message> &7— Envoie un MP Discord au joueur lié"));
        player.sendMessage(formatMessage("&7Vous pouvez utiliser le pseudo Minecraft ou le pseudo Discord (@username)."));
    }

    private void sendPublicMessage(Player player, String message, DiscordBot bot) {
        TextChannel channel = bot.getJda().getTextChannelById(DISCORD_CHAT_CHANNEL_ID);
        if (channel == null) {
            player.sendMessage(formatMessage("&cSalon Discord introuvable."));
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(0x2ECC71))
            .setAuthor(player.getName(), null, "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64")
            .setDescription(message)
            .setFooter("Message depuis Minecraft", null)
            .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
            success -> player.sendMessage(formatMessage("&aMessage envoyé sur Discord !")),
            error -> player.sendMessage(formatMessage("&cErreur lors de l'envoi du message."))
        );
    }

    private void sendPrivateMessage(Player player, String targetName, String discordId, String message, DiscordBot bot) {
        User user = bot.getJda().getUserById(discordId);
        if (user == null) {
            // L'utilisateur n'est pas en cache — le récupérer
            bot.getJda().retrieveUserById(discordId).queue(
                retrieved -> {
                    sendDm(player, targetName, retrieved, message);
                },
                error -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(formatMessage("&cImpossible de trouver le membre Discord lié à &e" + targetName + "&c.")))
            );
        } else {
            sendDm(player, targetName, user, message);
        }
    }

    private void sendDm(Player player, String targetName, User user, String message) {
        user.openPrivateChannel().queue(
            dm -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0x3498DB))
                    .setTitle("📨 Message de " + player.getName() + " (Minecraft)")
                    .setDescription(message)
                    .setThumbnail("https://mc-heads.net/avatar/" + player.getUniqueId() + "/64")
                    .setFooter("Répondez avec /msg sur Discord", null)
                    .setTimestamp(Instant.now());

                // Ajouter un bouton "Répondre" 
                net.dv8tion.jda.api.interactions.components.buttons.Button replyBtn =
                    net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "mc_reply:" + player.getName(), "💬 Répondre"
                    );

                dm.sendMessageEmbeds(embed.build())
                    .setActionRow(replyBtn)
                    .queue(
                        success -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(formatMessage("&aMessage privé envoyé à &e" + targetName + " &a(Discord) !"))),
                        error -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(formatMessage("&cImpossible d'envoyer un MP à &e" + targetName + "&c. Les MP sont peut-être désactivés.")))
                    );
            },
            error -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(formatMessage("&cImpossible d'ouvrir un MP avec &e" + targetName + "&c.")))
        );
    }

    private String getDiscordIdForPlayer(String playerName) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            boolean sqlite = plugin.getDatabaseManager().isSQLite();

            // 1. Recherche par nom Minecraft
            String uuid = null;
            String uuidSql = sqlite
                    ? "SELECT uuid FROM players WHERE username = ? COLLATE NOCASE"
                    : "SELECT uuid FROM players WHERE LOWER(username) = LOWER(?)";
            try (PreparedStatement stmt = conn.prepareStatement(uuidSql)) {
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    uuid = rs.getString("uuid");
                }
            }
            if (uuid != null) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT discord_id FROM discord_links WHERE uuid = ?")) {
                    stmt.setString(1, uuid);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getString("discord_id");
                    }
                }
            }

            // 2. Recherche par nom Discord (username ou display name global)
            String discordSql = sqlite
                    ? "SELECT discord_id FROM discord_links WHERE discord_username = ? COLLATE NOCASE OR discord_display_name = ? COLLATE NOCASE"
                    : "SELECT discord_id FROM discord_links WHERE LOWER(discord_username) = LOWER(?) OR LOWER(discord_display_name) = LOWER(?)";
            try (PreparedStatement stmt = conn.prepareStatement(discordSql)) {
                stmt.setString(1, playerName);
                stmt.setString(2, playerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordCommand] Erreur getDiscordIdForPlayer: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fallback JDA pour les liens créés avant l'ajout des colonnes discord_username/display_name.
     * Compare le nom fourni avec le username Discord et le display name global.
     */
    private String resolveDiscordIdByJda(String playerName, DiscordBot bot) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT discord_id FROM discord_links");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String discordId = rs.getString("discord_id");
                try {
                    User user = bot.getJda().retrieveUserById(discordId).complete();
                    if (user != null) {
                        String username = user.getName();
                        String globalName = user.getGlobalName();
                        if (username.equalsIgnoreCase(playerName) ||
                            (globalName != null && globalName.equalsIgnoreCase(playerName))) {
                            return discordId;
                        }
                    }
                } catch (Exception ignored) {
                    // Utilisateur introuvable ou erreur JDA
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordCommand] Erreur resolveDiscordIdByJda: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("mp", "msg", "pm"));
            suggestions.addAll(org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
            return suggestions.stream()
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mp") || args[0].equalsIgnoreCase("msg") || args[0].equalsIgnoreCase("pm"))) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
