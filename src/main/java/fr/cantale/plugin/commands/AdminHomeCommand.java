package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.teleport.Home;
import fr.cantale.plugin.teleport.HomeManager;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Commandes staff homes :
 * <ul>
 *   <li>{@code /adminhome check|list <joueur>} — liste les homes</li>
 *   <li>{@code /adminhome <joueur> <nom>} ou {@code /adminhome tp <joueur> <nom>} — TP vers ce home</li>
 * </ul>
 */
public class AdminHomeCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final HomeManager homeManager;

    public AdminHomeCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.homeManager = plugin.getHomeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!StaffAccess.canUseStaffTools(plugin, sender)) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée. (cantale.admin / cantale.moderator)");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);

        if (first.equals("check") || first.equals("list") || first.equals("ls")) {
            if (args.length < 2) {
                sender.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " check <joueur>");
                return true;
            }
            OfflinePlayer target = resolveOffline(args[1]);
            if (target == null) {
                sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + args[1]);
                return true;
            }
            homeManager.listHomesOf(sender, target.getUniqueId(), displayName(target, args[1]));
            return true;
        }

        if (first.equals("tp") || first.equals("teleport") || first.equals("goto")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage("§8[§6Cantale§8] §cLa téléportation est réservée aux joueurs.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " tp <joueur> <nom_du_home>");
                return true;
            }
            OfflinePlayer target = resolveOffline(args[1]);
            if (target == null) {
                sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + args[1]);
                return true;
            }
            homeManager.teleportAdminToHome(admin, target.getUniqueId(), args[2], displayName(target, args[1]));
            return true;
        }

        // Forme courte : /adminhome <joueur> <nom>
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("§8[§6Cantale§8] §cLa téléportation est réservée aux joueurs. Pour lister : /"
                    + label + " check <joueur>");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        OfflinePlayer target = resolveOffline(args[0]);
        if (target == null) {
            sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + args[0]);
            return true;
        }
        homeManager.teleportAdminToHome(admin, target.getUniqueId(), args[1], displayName(target, args[0]));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8[§6Cantale§8] §7=== Admin homes ===");
        sender.sendMessage("§7/" + "adminhome check <joueur> §8— §7liste les homes");
        sender.sendMessage("§7/" + "adminhome <joueur> <nom> §8— §7TP vers ce home");
        sender.sendMessage("§7/" + "adminhome tp <joueur> <nom> §8— §7idem (explicite)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!StaffAccess.canUseStaffTools(plugin, sender)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("check");
            options.add("list");
            options.add("tp");
            options.addAll(onlineNames());
            return filterPrefix(options, args[0]);
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("check") || first.equals("list") || first.equals("ls")
                    || first.equals("tp") || first.equals("teleport") || first.equals("goto")) {
                return filterPrefix(onlineNames(), args[1]);
            }
            // /adminhome <joueur> <nom> → compléter les noms de homes
            OfflinePlayer target = resolveOffline(args[0]);
            if (target == null) return Collections.emptyList();
            return filterPrefix(homeNames(target.getUniqueId()), args[1]);
        }

        if (args.length == 3) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("tp") || first.equals("teleport") || first.equals("goto")) {
                OfflinePlayer target = resolveOffline(args[1]);
                if (target == null) return Collections.emptyList();
                return filterPrefix(homeNames(target.getUniqueId()), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> homeNames(UUID uuid) {
        Map<String, Home> homes = homeManager.ensureHomesLoaded(uuid);
        return homes.keySet().stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static OfflinePlayer resolveOffline(String name) {
        if (name == null || name.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;
        // Essai case-insensitive parmi les joueurs en ligne
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return offline.getUniqueId() != null && offline.hasPlayedBefore() ? offline : null;
    }

    private static String displayName(OfflinePlayer target, String fallback) {
        if (target.getName() != null) return target.getName();
        return fallback;
    }
}
