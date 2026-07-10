package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.PrivateChest;
import fr.cantale.plugin.privatechest.PrivateChestManager;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PrivateChestCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final PrivateChestManager privateChestManager;

    public PrivateChestCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.privateChestManager = new PrivateChestManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        PlayerRank rank = plugin.getRankManager().getRank(player);
        int slots = rank.getPcSlots();

        // Open private chest with rank-appropriate size
        PrivateChest chest = new PrivateChest(player.getUniqueId(), slots);
        
        // Load saved contents
        PrivateChest saved = plugin.getDatabaseManager() != null 
            ? new fr.cantale.plugin.privatechest.PrivateChestDAO(plugin).loadPrivateChest(player.getUniqueId())
            : null;
        if (saved != null && saved.getContents() != null) {
            // Copy saved items into the new sized chest
            org.bukkit.inventory.ItemStack[] savedContents = saved.getContents();
            org.bukkit.inventory.ItemStack[] newContents = chest.getContents();
            int copyLen = Math.min(savedContents.length, newContents.length);
            for (int i = 0; i < copyLen; i++) {
                newContents[i] = savedContents[i];
            }
            chest.setContents(newContents);
        }

        player.openInventory(chest.getInventory());
        player.sendMessage("§8[§6Cantale§8] §7Coffre privé ouvert §7(§e" + slots + " slots§7)");
        return true;
    }

    public PrivateChestManager getPrivateChestManager() {
        return privateChestManager;
    }
}
