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
            player.sendMessage(formatMessage("&eUsage: /discord <message> &7— Envoie un message sur Discord"));
            player.sendMessage(formatMessage("&eUsage: /discord <joueur> <message> &7— Envoie un MP Discord au joueur lié"));
            return true;
        }

        // Déterminer si le premier argument est un joueur existant en BDD avec un lien Discord
        // On tente de résoudre comme un joueur : s'il existe un lien discord pour args[0], c'est un /discord <joueur> <message>
        // Sinon, on considère tout comme un message public
        String possiblePlayer = args[0];

        // Si un seul argument, c'est forcément un message public (pas un joueur sans message)
        if (args.length == 1) {
            sendPublicMessage(player, args[0], bot);
            return true;
        }

        // Essayer de résoudre le premier argument comme un joueur lié
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String discordId = getDiscordIdForPlayer(possiblePlayer);
            if (discordId != null && args.length >= 2) {
                // C'est un /discord <joueur> <message> — envoyer un MP
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                sendPrivateMessage(player, possiblePlayer, discordId, message, bot);
            } else {
                // Pas de joueur lié trouvé → envoyer tout comme message public
                String message = String.join(" ", args);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sendPublicMessage(player, message, bot));
            }
        });

        return true;
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
            // D'abord récupérer l'UUID du joueur
            String uuid = null;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT uuid FROM players WHERE username = ? COLLATE NOCASE")) {
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    uuid = rs.getString("uuid");
                }
            }
            if (uuid == null) return null;

            // Ensuite chercher le lien Discord
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT discord_id FROM discord_links WHERE uuid = ?")) {
                stmt.setString(1, uuid);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Proposer les joueurs en ligne
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
