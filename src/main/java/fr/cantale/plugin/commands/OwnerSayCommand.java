package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Commande /say — fait parler le bot en jeu.
 * <ul>
 *   <li>Console / RCON : toujours autorisé</li>
 *   <li>Joueur : réservé au propriétaire (Discord ID lié)</li>
 * </ul>
 */
public class OwnerSayCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private static final long OWNER_DISCORD_ID = 1122092101459517481L;
    private static final String BOT_PREFIX = "&9[Cantale] &f";

    public OwnerSayCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /say <message>");
            return true;
        }

        String message = String.join(" ", args);

        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            broadcastAsBot(message);
            sender.sendMessage("§aMessage envoyé en jeu (bot).");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée à la console ou au propriétaire.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isOwner = isLinkedOwner(player);
            if (!isOwner) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cCette commande est réservée au propriétaire."));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                broadcastAsBot(message);
                player.sendMessage("§aMessage envoyé en jeu (bot).");
            });
        });

        return true;
    }

    private void broadcastAsBot(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', BOT_PREFIX + message));
    }

    private boolean isLinkedOwner(Player player) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT 1 FROM discord_links WHERE discord_id = ? AND uuid = ?")) {
            stmt.setString(1, String.valueOf(OWNER_DISCORD_ID));
            stmt.setString(2, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (Exception e) {
            plugin.getLogger().warning("[OwnerSay] Erreur vérification lien: " + e.getMessage());
            return false;
        }
    }
}
