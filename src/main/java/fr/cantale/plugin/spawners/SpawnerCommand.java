package fr.cantale.plugin.spawners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public SpawnerCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean console = sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
        if (!console && !sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cPermission refusée (cantale.admin).");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage : /spawner give <joueur> <mob> [tier]");
            sender.sendMessage("§7Mobs : " + SpawnerMob.obtainableValues().stream()
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", ")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable (doit être en ligne) : §e" + args[1]);
            return true;
        }

        var mobOpt = SpawnerMob.fromName(args[2]);
        if (mobOpt.isEmpty()) {
            sender.sendMessage("§cMob inconnu : §e" + args[2]);
            return true;
        }
        if (!mobOpt.get().obtainable()) {
            sender.sendMessage("§cCe mob n'est plus un spawner Cantale : §e" + args[2]);
            return true;
        }

        int tier = 1;
        if (args.length >= 4) {
            try {
                tier = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPalier invalide (1–8).");
                return true;
            }
        }

        SpawnerManager manager = plugin.getSpawnerManager();
        if (manager == null) {
            sender.sendMessage("§cSpawners non initialisés.");
            return true;
        }
        tier = manager.clampTier(tier);
        ItemStack item = manager.createItem(mobOpt.get(), tier);
        target.getInventory().addItem(item);
        sender.sendMessage("§8[§6Cantale§8] §aDonné §e" + mobOpt.get().itemDisplayName(tier)
                + " §aà §e" + target.getName());
        if (!sender.equals(target)) {
            target.sendMessage("§8[§6Cantale§8] §aTu as reçu §e" + mobOpt.get().itemDisplayName(tier) + "§a.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.admin") && !(sender instanceof ConsoleCommandSender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return partial(List.of("give"), args[0]);
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return SpawnerMob.obtainableValues().stream()
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .filter(n -> n.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 4) {
            return partial(List.of("1", "2", "3", "4", "5", "6", "7", "8"), args[3]);
        }
        return Collections.emptyList();
    }

    private static List<String> partial(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
