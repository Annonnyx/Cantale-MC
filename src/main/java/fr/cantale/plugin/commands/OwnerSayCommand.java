package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Commande /say réservée au propriétaire (Discord ID 1122092101459517481).
 * Le joueur doit avoir lié son compte Minecraft à ce Discord ID.
 * Le message est broadcasté sur le serveur Minecraft.
 */
public class OwnerSayCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private static final long OWNER_DISCORD_ID = 1122092101459517481L;

    public OwnerSayCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée au joueur propriétaire.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /say <message>");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isOwner = isLinkedOwner(player);
            if (!isOwner) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cCette commande est réservée au propriétaire."));
                return;
            }

            String message = String.join(" ", args);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            });
        });

        return true;
    }

    private boolean isLinkedOwner(Player player) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM discord_links WHERE discord_id = ? AND uuid = ?")) {
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
