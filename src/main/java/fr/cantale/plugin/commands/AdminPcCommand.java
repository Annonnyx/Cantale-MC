package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * {@code /adminpc <joueur> [1|2|3]} — ouvre les coffres privés d'un joueur (édition live DB/cache).
 */
public class AdminPcCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public AdminPcCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!StaffAccess.canUseStaffTools(plugin, sender)) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée. (cantale.admin / cantale.moderator)");
            return true;
        }
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("§8[§6Cantale§8] §cCette commande est réservée aux joueurs.");
            return true;
        }
        if (args.length < 1) {
            staff.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " <joueur> [1|2|3]");
            return true;
        }

        OfflinePlayer offline = resolveOffline(args[0]);
        if (offline == null) {
            staff.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + args[0]);
            return true;
        }

        ChestType type = ChestType.PC1;
        if (args.length >= 2) {
            ChestType parsed = parseChestArg(args[1]);
            if (parsed == null) {
                staff.sendMessage("§8[§6Cantale§8] §cCoffre invalide. Utilise §e1§c, §e2 §cou §e3§c.");
                return true;
            }
            type = parsed;
        }

        String name = offline.getName() != null ? offline.getName() : args[0];
        plugin.getPrivateChestService().openForeignChest(staff, offline.getUniqueId(), name, type);
        staff.sendMessage("§8[§6Cantale§8] §a" + type.getTitle() + " §ade §e" + name + " §aouvert.");
        return true;
    }

    private static ChestType parseChestArg(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "1", "pc1", "pc", "p1" -> ChestType.PC1;
            case "2", "pc2", "p2" -> ChestType.PC2;
            case "3", "pc3", "p3" -> ChestType.PC3;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!StaffAccess.canUseStaffTools(plugin, sender)) return Collections.emptyList();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> opts = List.of("1", "2", "3", "pc1", "pc2", "pc3");
            return opts.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return Collections.emptyList();
    }

    private static OfflinePlayer resolveOffline(String name) {
        if (name == null || name.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;
        return null;
    }
}
