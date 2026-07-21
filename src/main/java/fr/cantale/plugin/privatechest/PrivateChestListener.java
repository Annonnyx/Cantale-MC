package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PrivateChestListener implements Listener {

    private final CantalePlugin plugin;
    private final PrivateChestService chestService;

    public PrivateChestListener(CantalePlugin plugin, PrivateChestService chestService) {
        this.plugin = plugin;
        this.chestService = chestService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        ChestType type = chestService.resolveTypeFromTitle(title);
        if (type == null) return;

        Inventory top = event.getView().getTopInventory();
        int size = top.getSize();
        int bottomRowStart = size - 9;

        // Bloquer tous les déplacements dans la dernière rangée des coffres privés (barre de navigation)
        if (event.getClickedInventory() == top && type != ChestType.EC) {
            int slot = event.getSlot();
            if (slot >= bottomRowStart && slot < size) {
                event.setCancelled(true);

                // Bouton navigation "Suivant" (slot 8 de la dernière ligne)
                if (slot == bottomRowStart + 8) {
                    ItemStack item = top.getItem(slot);
                    if (item != null && item.getType() == org.bukkit.Material.ARROW) {
                        // Sauvegarder avant de changer d'écran
                        boolean isStaff = player.hasPermission("cantale.admin");
                        chestService.saveChest(player, top, type, isStaff);

                        PlayerRank rank = isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);
                        ChestType next = chestService.getNextPrivateChest(rank, type);
                        chestService.openChest(player, next, isStaff);
                    }
                }
                return;
            }
        }

        // Pour tout autre slot, laisser le comportement vanilla (déplacements autorisés)
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        ChestType type = chestService.resolveTypeFromTitle(title);
        if (type == null) return;

        boolean isStaff = player.hasPermission("cantale.admin");
        chestService.saveChest(player, event.getInventory(), type, isStaff);
    }
}
