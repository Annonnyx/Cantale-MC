package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CantalameManager;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (!player.hasPermission("cantale.admin")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        try {
            CustomItemType type = CustomItemType.valueOf(args[0].toUpperCase());
            ItemStack item;
            
            if (type.isCantalame()) {
                item = cantalameManager.createCantalame();
            } else {
                item = customItemManager.createCustomItem(type);
            }
            
            player.getInventory().addItem(item);
            player.sendMessage("§8[§6Cantale§8] §aItem custom donné : §e" + type.getDisplayName());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§8[§6Cantale§8] §cType d'item inconnu : §e" + args[0]);
            sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8[§6Cantale§8] §7=== Items Custom ===");
        player.sendMessage("§7Usage : /customitem <type>");
        player.sendMessage("§7Types disponibles :");
        for (CustomItemType type : CustomItemType.values()) {
            player.sendMessage("§7  - §e" + type.name().toLowerCase());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(CustomItemType.values())
                    .map(type -> type.name().toLowerCase())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
