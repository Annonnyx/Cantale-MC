package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class ProfileCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public ProfileCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        OfflinePlayer target = player;
        if (args.length > 0) {
            String input = args[0];
            Player online = Bukkit.getPlayerExact(input);
            OfflinePlayer searched;
            if (online != null) {
                searched = online;
            } else {
                UUID uuid = resolveUuidFromDatabase(input);
                if (uuid != null) {
                    searched = Bukkit.getOfflinePlayer(uuid);
                } else {
                    try {
                        searched = Bukkit.getOfflinePlayer(UUID.fromString(input));
                    } catch (IllegalArgumentException ignored) {
                        searched = Bukkit.getOfflinePlayer(input);
                    }
                }
            }
            if (!searched.hasPlayedBefore() && !searched.isOnline()) {
                player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou ne s'est jamais connecté.");
                return true;
            }
            target = searched;
        }

        plugin.getUIManager().openProfileMenu(player, target);
        return true;
    }

    private UUID resolveUuidFromDatabase(String name) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT uuid FROM players WHERE LOWER(username) = LOWER(?)")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    if (uuidStr != null && !uuidStr.isBlank()) {
                        return UUID.fromString(uuidStr);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Profile] Erreur recherche UUID pour " + name + ": " + e.getMessage());
        }
        return null;
    }
}
