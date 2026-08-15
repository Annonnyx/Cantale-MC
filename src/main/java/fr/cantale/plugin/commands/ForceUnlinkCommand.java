package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordLinkService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /forceunlink &lt;joueur&gt; — délie Discord/web d'un joueur (admin).
 */
public class ForceUnlinkCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final DiscordLinkService linkService;

    public ForceUnlinkCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.linkService = new DiscordLinkService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée. (cantale.admin)");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage : /forceunlink <joueur>");
            sender.sendMessage("§7Exemple : §f/forceunlink Anox26");
            sender.sendMessage("§7Délie Discord (et ancien lien site) de ce joueur.");
            return true;
        }

        String mcName = args[0];
        sender.sendMessage("§8[§6Cantale§8] §7Déliaison forcée en cours…");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DiscordLinkService.UnlinkResult result = linkService.unlinkByMinecraftName(mcName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String line : result.toMinecraftMessage().split("\n")) {
                    sender.sendMessage(line);
                }
                if (result.success()) {
                    plugin.getLogger().info("[ForceUnlink] " + sender.getName() + " a délié "
                        + result.playerName() + " (Discord "
                        + (result.discordId() != null ? result.discordId() : "aucun") + ")");
                    Player target = Bukkit.getPlayerExact(result.playerName());
                    if (target != null && target != sender) {
                        target.sendMessage("§8[§6Cantale§8] §eUn administrateur a délié ton compte Discord.");
                        target.sendMessage("§7Tu peux relier un compte avec §e/link§7.");
                    }
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.admin")) return Collections.emptyList();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return Collections.emptyList();
    }
}
