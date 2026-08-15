package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Ancienne commande {@code /web link} (liaison site via code) — obsolète.
 *
 * Conservée pour afficher un message de redirection vers
 * https://www.cantale.world/connexion (OAuth Discord).
 * La liaison Discord reste {@code /link} (Minecraft + slash Discord).
 */
public class WebCommand implements CommandExecutor, TabCompleter {

    private static final String SITE_CONNEXION = "https://www.cantale.world/connexion";

    public WebCommand(CantalePlugin plugin) {
        // Conservé pour compatibilité d'enregistrement dans CantalePlugin.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sendRedirect(sender);
        return true;
    }

    private void sendRedirect(CommandSender sender) {
        sender.sendMessage(format("&8&m─────────────────────────"));
        sender.sendMessage(format("&6Site Cantale"));
        sender.sendMessage(format("&7La commande &e/web link &7n'existe plus."));
        sender.sendMessage(format("&7Connecte-toi sur le site :"));
        sender.sendMessage(format("&b" + SITE_CONNEXION));
        sender.sendMessage(format("&7Pour lier Discord ↔ Minecraft : &e/link &7en jeu, puis &e/link &7sur Discord."));
        sender.sendMessage(format("&7Pour délier : &e/unlink confirm &7en jeu."));
        sender.sendMessage(format("&8&m─────────────────────────"));
        if (sender instanceof Player player) {
            // Clickable URL for in-game clients that support it
            player.sendMessage(net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text("Ouvrir : ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(net.kyori.adventure.text.Component.text(SITE_CONNEXION, net.kyori.adventure.text.format.NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(SITE_CONNEXION))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text("Clique pour ouvrir", net.kyori.adventure.text.format.NamedTextColor.YELLOW))))
                    .build());
        }
    }

    private String format(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
