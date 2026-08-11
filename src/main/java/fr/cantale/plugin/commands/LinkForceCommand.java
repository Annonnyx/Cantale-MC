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
 * /linkforce &lt;Pseudo MC&gt; &lt;ID Discord&gt; — force la liaison Discord ↔ Minecraft (admin).
 */
public class LinkForceCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final DiscordLinkService linkService;

    public LinkForceCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.linkService = new DiscordLinkService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée. (cantale.admin)");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage : /linkforce <Pseudo MC> <ID Discord>");
            sender.sendMessage("§7Exemple : §f/linkforce Anox26 1122092101459517481");
            return true;
        }

        String mcName = args[0];
        String discordId = args[1];

        sender.sendMessage("§8[§6Cantale§8] §7Liaison forcée en cours…");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DiscordLinkService.ForceLinkResult result = linkService.forceLink(mcName, discordId, null, null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String line : result.toMinecraftMessage().split("\n")) {
                    sender.sendMessage(line);
                }
                if (result.success()) {
                    plugin.getLogger().info("[LinkForce] " + sender.getName() + " a lié "
                        + result.player().username() + " ↔ Discord " + result.discordId());
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
