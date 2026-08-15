package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordLinkService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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

/**
 * /unlink — délie son propre compte Discord (et d'éventuels restes de lien site).
 * Confirmation : {@code /unlink confirm}.
 */
public class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final DiscordLinkService linkService;

    public UnlinkCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.linkService = new DiscordLinkService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§8[§6Cantale§8] §cSeuls les joueurs peuvent utiliser /unlink.");
            sender.sendMessage("§7Pour délier un autre compte : §f/forceunlink <joueur>");
            return true;
        }

        if (args.length == 0) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                DiscordLinkService.LinkInfo info = linkService.getLinkInfo(player.getUniqueId().toString());
                Bukkit.getScheduler().runTask(plugin, () -> sendConfirmPrompt(player, info));
            });
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage : /unlink confirm");
            sender.sendMessage("§7Délie ton propre compte Discord. Tape d'abord §f/unlink §7pour voir le statut.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DiscordLinkService.UnlinkResult result =
                linkService.unlinkByUuid(player.getUniqueId().toString(), player.getName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String line : result.toMinecraftMessage().split("\n")) {
                    player.sendMessage(line);
                }
                if (result.success()) {
                    plugin.getLogger().info("[Unlink] " + player.getName() + " a délié Discord "
                        + (result.discordId() != null ? result.discordId() : "(site seulement)"));
                }
            });
        });
        return true;
    }

    private void sendConfirmPrompt(Player player, DiscordLinkService.LinkInfo info) {
        if (!info.isLinked()) {
            player.sendMessage("§8[§6Cantale§8] §cTon compte n'est pas lié à Discord.");
            player.sendMessage("§7Pour lier : §e/link §7en jeu, puis §e/link §7sur Discord.");
            return;
        }

        player.sendMessage("§8[§6Cantale§8] §eTu vas délier ton compte Discord.");
        if (info.discordId() != null) {
            String extra = info.discordUsername() != null && !info.discordUsername().isBlank()
                ? " §7(" + info.discordUsername() + ")"
                : "";
            player.sendMessage("§7ID Discord actuel : §f" + info.discordId() + extra);
        }
        if (info.hadWebCodes()) {
            player.sendMessage("§7Un ancien lien site (/web link) sera aussi nettoyé.");
        }
        player.sendMessage("§7L'accès site cantale.world (OAuth Discord) ne verra plus ton Minecraft.");
        player.sendMessage(Component.text("Confirme avec ", NamedTextColor.GRAY)
            .append(Component.text("/unlink confirm", NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand("/unlink confirm"))
                .hoverEvent(HoverEvent.showText(
                    Component.text("Clique pour confirmer").color(NamedTextColor.GRAY))))
            .append(Component.text(".", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("confirm".startsWith(prefix)) {
                return new ArrayList<>(List.of("confirm"));
            }
        }
        return Collections.emptyList();
    }
}
