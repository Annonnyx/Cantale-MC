package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

/**
 * Commande /link — génère un code temporaire pour lier Discord ↔ Minecraft.
 */
public class LinkCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final Random random = new Random();

    public LinkCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        // Vérifier si le compte est déjà lié
        String existingDiscordId = getLinkedDiscordId(player.getUniqueId().toString());
        if (existingDiscordId != null) {
            player.sendMessage("§8[§6Cantale§8] §cTon compte est déjà lié à Discord !");
            player.sendMessage("§7ID Discord : §f" + existingDiscordId);
            player.sendMessage("§7Si tu veux changer de compte Discord, contacte un administrateur.");
            return true;
        }

        // Supprimer anciens codes expirés
        cleanupExpiredCodes();

        // Vérifier s'il existe déjà un code actif
        String existing = getActiveCode(player.getUniqueId().toString());
        if (existing != null) {
            player.sendMessage("§8[§6Cantale§8] §eTon code de liaison : §f" + existing);
            player.sendMessage("§7Utilise §f/link <code> §7sur Discord pour lier ton compte.");
            player.sendMessage("§7Le code expire dans 10 minutes.");
            return true;
        }

        String code = generateCode();
        long now = System.currentTimeMillis() / 1000;
        long expires = now + 600; // 10 minutes

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO link_codes (code, uuid, player_name, created_at, expires_at, used) VALUES (?, ?, ?, ?, ?, 0)")) {
            stmt.setString(1, code);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setString(3, player.getName());
            stmt.setLong(4, now);
            stmt.setLong(5, expires);
            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[Link] Erreur création code: " + e.getMessage());
            player.sendMessage("§cErreur lors de la génération du code.");
            return true;
        }

        player.sendMessage("§8[§6Cantale§8] §eTon code de liaison : §f§l" + code);
        player.sendMessage("§7Va sur Discord et fais §f/link " + code + " §7pour lier ton compte.");
        player.sendMessage("§7Le code expire dans 10 minutes.");
        return true;
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String getLinkedDiscordId(String uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT discord_id FROM discord_links WHERE uuid = ?")) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (Exception e) {
            plugin.getLogger().warning("[Link] Erreur getLinkedDiscordId: " + e.getMessage());
        }
        return null;
    }

    private String getActiveCode(String uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT code FROM link_codes WHERE uuid = ? AND used = 0 AND expires_at > ?")) {
            stmt.setString(1, uuid);
            stmt.setLong(2, System.currentTimeMillis() / 1000);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("code");
        } catch (Exception e) {
            plugin.getLogger().warning("[Link] Erreur getActiveCode: " + e.getMessage());
        }
        return null;
    }

    private void cleanupExpiredCodes() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM link_codes WHERE expires_at < ?")) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[Link] Erreur cleanup: " + e.getMessage());
        }
    }
}
