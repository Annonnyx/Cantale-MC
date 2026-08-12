package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestHolder;
import fr.cantale.plugin.privatechest.PrivateChestService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /adminec <joueur>} — ouvre l'ender chest d'un joueur (édition live si en ligne).
 * Hors ligne : Paper n'expose pas OfflinePlayer#getEnderChest ; fallback legacy DB si présent.
 */
public class AdminEcCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public AdminEcCommand(CantalePlugin plugin) {
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
            staff.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " <joueur>");
            return true;
        }

        OfflinePlayer offline = resolveOffline(args[0]);
        if (offline == null) {
            staff.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + args[0]);
            return true;
        }

        String name = offline.getName() != null ? offline.getName() : args[0];
        Player online = offline.getPlayer();
        if (online != null && online.isOnline()) {
            staff.openInventory(online.getEnderChest());
            staff.sendMessage("§8[§6Cantale§8] §aEnder chest de §e" + name + " §aouvert (édition live).");
            return true;
        }

        // Hors ligne : pas d'API OfflinePlayer#getEnderChest (Paper) — legacy DB uniquement
        PrivateChestService pcs = plugin.getPrivateChestService();
        ItemStack[] legacy = pcs != null ? pcs.peekStorage(offline.getUniqueId(), ChestType.EC) : new ItemStack[0];
        if (!hasItems(legacy)) {
            staff.sendMessage("§8[§6Cantale§8] §cEnder chest vanilla inaccessible hors ligne.");
            staff.sendMessage("§7Le joueur doit être connecté pour inspecter/éditer son EC Minecraft.");
            return true;
        }

        PrivateChestHolder holder = new PrivateChestHolder(ChestType.EC, offline.getUniqueId(), true);
        Inventory inv = Bukkit.createInventory(holder, 27, "§c[Staff] §5Ender Chest §8— §e" + name + " §7(legacy)");
        holder.setInventory(inv);
        ItemStack[] contents = new ItemStack[27];
        for (int i = 0; i < Math.min(legacy.length, 27); i++) {
            contents[i] = legacy[i] == null ? null : legacy[i].clone();
        }
        inv.setContents(contents);
        staff.openInventory(inv);
        staff.sendMessage("§8[§6Cantale§8] §eEC legacy (DB) de §6" + name + " §eouvert — le vanilla hors ligne n'est pas disponible.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!StaffAccess.canUseStaffTools(plugin, sender)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean hasItems(ItemStack[] contents) {
        if (contents == null) return false;
        for (ItemStack s : contents) {
            if (s != null && !s.getType().isAir()) return true;
        }
        return false;
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
