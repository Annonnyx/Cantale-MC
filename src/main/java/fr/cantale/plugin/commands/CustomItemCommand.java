package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CantalameManager;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CustomItemCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final CantalameManager cantalameManager;

    public CustomItemCommand(CantalePlugin plugin) {
        this(plugin, null);
    }

    public CustomItemCommand(CantalePlugin plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager != null ? customItemManager : new CustomItemManager();
        this.cantalameManager = new CantalameManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean console = sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
        if (!console && !sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Console : /customitem <type> <joueur>
        // Joueur  : /customitem <type> [joueur]  (défaut = soi-même)
        Player target;
        String typeArg = args[0];

        if (console) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage console : customitem <type> <joueur>");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable (doit être en ligne).");
                return true;
            }
        } else {
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cJoueur introuvable : §e" + args[1]);
                    return true;
                }
            } else {
                target = (Player) sender;
            }
        }

        try {
            CustomItemType type = CustomItemType.valueOf(typeArg.toUpperCase(Locale.ROOT));
            ItemStack item = type.isCantalame()
                ? cantalameManager.createCantalame()
                : customItemManager.createCustomItem(type);

            var leftover = target.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(i ->
                    target.getWorld().dropItemNaturally(target.getLocation(), i));
                sender.sendMessage("§8[§6Cantale§8] §eInventaire plein — item droppé aux pieds de §e" + target.getName());
            }

            sender.sendMessage("§8[§6Cantale§8] §aItem §e" + type.getDisplayName()
                + " §adonné à §e" + target.getName() + "§a.");
            if (sender != target) {
                target.sendMessage("§8[§6Cantale§8] §aTu as reçu : §e" + type.getDisplayName());
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§8[§6Cantale§8] §cType d'item inconnu : §e" + typeArg);
            sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8[§6Cantale§8] §7=== Items Custom ===");
        sender.sendMessage("§7Usage joueur  : /customitem <type> [joueur]");
        sender.sendMessage("§7Usage console : customitem <type> <joueur>");
        sender.sendMessage("§7Types :");
        for (CustomItemType type : CustomItemType.values()) {
            sender.sendMessage("§7  - §e" + type.name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(CustomItemType.values())
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .filter(n -> n.startsWith(prefix))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
