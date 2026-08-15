package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public TpaCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs uniquement.");
            return true;
        }

        String cmd = label.toLowerCase(Locale.ROOT);
        TpaManager mgr = plugin.getTpaManager();
        if (mgr == null) {
            player.sendMessage("§8[§6Cantale§8] §cSystème TPA indisponible.");
            return true;
        }

        return switch (cmd) {
            case "tpa" -> handleRequest(player, args, TpaManager.Type.TPA);
            case "tpahere" -> handleRequest(player, args, TpaManager.Type.TPAHERE);
            case "tpyes", "tpaccept", "tpacceptation" -> {
                mgr.accept(player);
                yield true;
            }
            case "tpno", "tpdeny" -> {
                mgr.deny(player);
                yield true;
            }
            default -> {
                player.sendMessage("§8[§6Cantale§8] §e/tpa <joueur> §7(tu vas vers lui) §8· §e/tpahere <joueur> §7(il vient à toi) §8· §e/tpyes §8· §e/tpno");
                yield true;
            }
        };
    }

    private boolean handleRequest(Player player, String[] args, TpaManager.Type type) {
        if (args.length < 1) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /" + (type == TpaManager.Type.TPA ? "tpa" : "tpahere") + " <joueur>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            // Soft match
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null || !target.isOnline()) {
            player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou hors ligne.");
            return true;
        }
        var vanish = plugin.getModerationCommand() != null
            ? plugin.getModerationCommand().getVanishManager() : null;
        if (vanish != null && vanish.isVanished(target.getUniqueId())
            && !player.hasPermission("cantale.moderator")) {
            player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou hors ligne.");
            return true;
        }
        plugin.getTpaManager().sendRequest(player, target, type);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        boolean canSeeVanish = sender.hasPermission("cantale.moderator");
        var vanish = plugin.getModerationCommand() != null
            ? plugin.getModerationCommand().getVanishManager() : null;
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!canSeeVanish && vanish != null && vanish.isVanished(p.getUniqueId())) {
                continue;
            }
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(p.getName());
            }
        }
        return names.stream().sorted().collect(Collectors.toList());
    }
}
