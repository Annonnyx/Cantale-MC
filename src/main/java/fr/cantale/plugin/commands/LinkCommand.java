package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.bukkit.Bukkit;
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
        // Exécuter les opérations BD en asynchrone pour éviter de bloquer le thread principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String uuid = player.getUniqueId().toString();

            // Vérifier si déjà lié
            String existingDiscordId = getLinkedDiscordId(uuid);
            if (existingDiscordId != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§8[§6Cantale§8] §cTon compte est déjà lié à Discord !");
                    player.sendMessage("§7ID Discord : §f" + existingDiscordId);
                    player.sendMessage("§7Si tu veux changer de compte Discord, contacte un administrateur.");
                });
                return;
            }

            // Cleanup et vérification de code actif
            cleanupExpiredCodes();
            String existing = getActiveCode(uuid);
            if (existing != null) {
                Bukkit.getScheduler().runTask(plugin, () -> sendLinkCodeMessages(player, existing, false));
                return;
            }

            String code = generateCode();
            long now = System.currentTimeMillis() / 1000;
            long expires = now + 600; // 10 minutes

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO link_codes (code, uuid, player_name, created_at, expires_at, used) VALUES (?, ?, ?, ?, ?, 0)")) {
                stmt.setString(1, code);
                stmt.setString(2, uuid);
                stmt.setString(3, player.getName());
                stmt.setLong(4, now);
                stmt.setLong(5, expires);
                stmt.executeUpdate();

                Bukkit.getScheduler().runTask(plugin, () -> sendLinkCodeMessages(player, code, true));
            } catch (Exception e) {
                plugin.getLogger().warning("[Link] Erreur création code: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cErreur lors de la génération du code."));
            }
        });

        return true;
    }

    /**
     * Envoie les messages de code de liaison. Le code est cliquable (copie presse-papiers).
     */
    private void sendLinkCodeMessages(Player player, String code, boolean newlyCreated) {
        Component codeComponent = Component.text(code)
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Clique pour copier").color(NamedTextColor.GRAY)));

        player.sendMessage(Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Cantale", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Ton code de liaison : ", NamedTextColor.YELLOW))
                .append(codeComponent));

        player.sendMessage("§7Rejoins notre Discord : §bhttps://discord.gg/eDTfYWtuYp");

        Component usage = Component.text("Et utilise le code avec ", NamedTextColor.GRAY)
                .append(Component.text("/link ", NamedTextColor.WHITE))
                .append(Component.text(code)
                        .color(NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.copyToClipboard(code))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Clique pour copier").color(NamedTextColor.GRAY))))
                .append(Component.text(" <ton_pseudo> ", NamedTextColor.WHITE))
                .append(Component.text(newlyCreated
                        ? "sur Discord pour lier ton compte."
                        : "sur Discord.", NamedTextColor.GRAY));
        player.sendMessage(usage);

        player.sendMessage("§7Le code expire dans 10 minutes.");
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
