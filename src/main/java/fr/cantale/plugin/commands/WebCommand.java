package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.web.WebLinkDAO;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /web link <code>  → lie le compte Minecraft au site web Cantale.
 *
 * Le joueur va sur le site, clique « Se connecter avec Minecraft », obtient un
 * code 6 chiffres, tape /web link 123456 ici → site valide automatiquement
 * (polling) et émet un cookie de session.
 */
public class WebCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final WebLinkDAO webLinkDAO;

    public WebCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.webLinkDAO = new WebLinkDAO(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(format("&cCette commande est réservée aux joueurs."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "link" -> handleLink(player, args);
            case "help" -> sendHelp(player);
            default -> {
                player.sendMessage(format("&cSous-commande inconnue."));
                sendHelp(player);
            }
        }
        return true;
    }

    private void handleLink(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(format("&cUsage : /web link <code 6 chiffres>"));
            player.sendMessage(format("&7Récupère ton code sur le site web : §bcantale.world"));
            return;
        }
        String code = args[1].trim();
        if (!code.matches("\\d{6}")) {
            player.sendMessage(format("&cLe code doit faire 6 chiffres."));
            return;
        }

        // Exécution DB en async pour ne pas bloquer le tick
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = webLinkDAO.consumeCode(code, player.getUniqueId(), player.getName());
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if ("OK".equals(result)) {
                    player.sendMessage(format("&8&m─────────────────────────"));
                    player.sendMessage(format("&aCompte web lié avec succès !"));
                    player.sendMessage(format("&7Retourne sur le site, tu es connecté."));
                    player.sendMessage(format("&8&m─────────────────────────"));
                } else if ("ALREADY_USED".equals(result)) {
                    player.sendMessage(format("&cCe code a déjà été utilisé. Génère-en un nouveau sur le site."));
                } else {
                    player.sendMessage(format("&cCode invalide ou expiré. Génère un nouveau code sur le site."));
                }
            });
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(format("&6=== Cantale Web ==="));
        player.sendMessage(format("&7Lie ton compte Minecraft au site web pour voir ton profil, gérer ton panier de boutique, et plus."));
        player.sendMessage(format(""));
        player.sendMessage(format("&71. Va sur §bcantale.world §7→ §eSe connecter avec Minecraft"));
        player.sendMessage(format("&72. Récupère le code 6 chiffres affiché"));
        player.sendMessage(format("&73. Tape ici : &e/web link <code>"));
    }

    private String format(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return Arrays.asList("link", "help").stream().filter(s -> s.startsWith(p)).toList();
        }
        return Collections.emptyList();
    }
}
