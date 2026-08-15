package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.ui.inventory.LastDeathInventoryUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Affiche date/coords de la dernière mort.
 * Staff (admin toujours ; modo en mode trace {@code /moderation}) : ouvre aussi
 * une GUI lecture seule de l'inventaire au moment de la mort.
 */
public class LastDeathCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;

    public LastDeathCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cCette commande nécessite un joueur cible."));
                return true;
            }
            showLastDeath(sender, ((Player) sender).getUniqueId(), ((Player) sender).getName(), false);
            return true;
        }

        UUID uuid = resolveTargetUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cJoueur introuvable : &e" + args[0]));
            return true;
        }
        String name = resolveDisplayName(uuid, args[0]);
        boolean other = !(sender instanceof Player p) || !p.getUniqueId().equals(uuid);
        showLastDeath(sender, uuid, name, other);
        return true;
    }

    private UUID resolveTargetUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }

        UUID fromDb = playerDAO.getUuidByUsername(name);
        if (fromDb != null) {
            return fromDb;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()
                || playerDAO.playerExists(offline.getUniqueId())) {
            return offline.getUniqueId();
        }
        return null;
    }

    private String resolveDisplayName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return fallback;
    }

    private void showLastDeath(CommandSender sender, UUID uuid, String displayName, boolean viewingOther) {
        if (!playerDAO.playerExists(uuid)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cJoueur introuvable en base."));
            return;
        }

        long lastDeath = playerDAO.getLastDeath(uuid);
        int deaths = playerDAO.getDeaths(uuid);

        if (lastDeath == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Ce joueur n'est jamais mort."));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String date = sdf.format(new Date(lastDeath * 1000));

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Dernière Mort ==="));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Joueur : &e" + displayName));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Date : &e" + date));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Nombre total de morts : &c" + deaths));

        PlayerDAO.LastDeathLocation loc = playerDAO.getLastDeathLocation(uuid);
        if (loc != null) {
            String coords = String.format("%s &7X:&e%d &7Y:&e%d &7Z:&e%d",
                    loc.getWorld(),
                    (int) Math.floor(loc.getX()),
                    (int) Math.floor(loc.getY()),
                    (int) Math.floor(loc.getZ()));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Localisation : &e" + coords));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Localisation : &eNon disponible &7(mort avant enregistrement des coords)"));
        }

        if (!(sender instanceof Player viewer)) {
            return;
        }

        if (!canViewDeathInventory(viewer)) {
            if (viewingOther && (viewer.hasPermission("cantale.moderator")
                    || viewer.hasPermission("cantale.admin"))) {
                // Modo hors mode trace
                if (!viewer.hasPermission("cantale.admin")) {
                    viewer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&cActivez le mode trace avec &e/moderation &cpour voir l'inventaire de mort."));
                }
            }
            return;
        }

        ItemStack[] inv = playerDAO.getLastDeathInventory(uuid);
        if (inv == null) {
            viewer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Inventaire à la mort : &eNon disponible &7(mort avant enregistrement de l'inv)"));
            return;
        }

        viewer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Ouverture de l'inventaire à la mort (&electure seule&7)..."));
        plugin.getUIManager().getNavigationManager().openUI(viewer,
                new LastDeathInventoryUI(plugin, viewer, displayName, inv));
    }

    /**
     * Admin : toujours. Modo : uniquement en mode trace ({@code /moderation}).
     */
    private boolean canViewDeathInventory(Player staff) {
        if (staff.hasPermission("cantale.admin")) {
            return true;
        }
        if (!staff.hasPermission("cantale.moderator")) {
            return false;
        }
        var modCmd = plugin.getModerationCommand();
        return modCmd != null && modCmd.getTraceManager() != null
                && modCmd.getTraceManager().isInTrace(staff.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        if (!sender.hasPermission("cantale.admin") && !sender.hasPermission("cantale.moderator")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
