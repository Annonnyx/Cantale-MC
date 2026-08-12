package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PrivateChestListener implements Listener {

    private final CantalePlugin plugin;
    private final PrivateChestService chestService;

    public PrivateChestListener(CantalePlugin plugin, PrivateChestService chestService) {
        this.plugin = plugin;
        this.chestService = chestService;
    }

    private ChestType resolve(Inventory top, String title) {
        ChestType byHolder = chestService.resolveTypeFromInventory(top);
        if (byHolder != null) return byHolder;
        return chestService.resolveTypeFromTitle(title);
    }

    private PlayerRank resolveNavRank(Player player, Inventory top) {
        if (top.getHolder() instanceof PrivateChestHolder holder && holder.isForeignInspect()
                && holder.getOwnerUuid() != null) {
            return plugin.getRankManager().getRank(holder.getOwnerUuid());
        }
        boolean isStaff = player.hasPermission("cantale.admin");
        return isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        ChestType type = resolve(top, event.getView().getTitle());
        if (type == null || type == ChestType.EC) return;

        int size = top.getSize();
        int bottomRowStart = size - 9;
        PlayerRank rank = resolveNavRank(player, top);
        boolean hasNavigation = chestService.getPrivateChestCount(rank) > 1;
        if (!hasNavigation) return;

        boolean isStaff = player.hasPermission("cantale.admin")
                || (top.getHolder() instanceof PrivateChestHolder h && h.isForeignInspect());

        // Clics dans la barre de navigation
        if (event.getClickedInventory() == top) {
            int slot = event.getSlot();
            if (slot >= bottomRowStart && slot < size) {
                event.setCancelled(true);

                if (slot == bottomRowStart + 8) {
                    ItemStack item = top.getItem(slot);
                    if (item != null && chestService.isNavItem(item)) {
                        chestService.saveChest(player, top, type, isStaff);
                        ChestType next = chestService.getNextPrivateChest(rank, type);
                        if (top.getHolder() instanceof PrivateChestHolder holder && holder.isForeignInspect()
                                && holder.getOwnerUuid() != null) {
                            UUID owner = holder.getOwnerUuid();
                            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(owner).getName();
                            chestService.openForeignChest(player, owner, ownerName, next);
                        } else {
                            chestService.openChest(player, next, isStaff);
                        }
                    }
                }
                return;
            }
        }

        // Empêcher shift-click / collect depuis l'inventaire joueur vers la barre nav
        if (event.getClickedInventory() != top) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == ClickType.DOUBLE_CLICK) {
                if (!hasFreeStorageSlot(top, bottomRowStart) && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        ChestType type = resolve(top, event.getView().getTitle());
        if (type == null || type == ChestType.EC) return;

        PlayerRank rank = resolveNavRank(player, top);
        if (chestService.getPrivateChestCount(rank) <= 1) return;

        int bottomRowStart = top.getSize() - 9;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && rawSlot >= bottomRowStart) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // closeInventory() pendant une save ne doit pas re-entrer (récursion StackOverflow).
        if (!chestService.enterCloseSaveGuard(player.getUniqueId())) {
            return;
        }
        try {
            Inventory top = event.getView().getTopInventory();
            ChestType type = resolve(top, event.getView().getTitle());
            if (type == null) return;

            // EC vanilla live : Paper persiste tout seul. EC legacy hors ligne (staff) → DB.
            if (type == ChestType.EC) {
                if (top.getHolder() instanceof PrivateChestHolder holder
                        && holder.isForeignInspect()
                        && holder.getOwnerUuid() != null) {
                    ItemStack[] contents = top.getContents();
                    chestService.adminReplaceStorage(holder.getOwnerUuid(), ChestType.EC, contents);
                }
                return;
            }

            boolean isStaff = player.hasPermission("cantale.admin")
                    || (top.getHolder() instanceof PrivateChestHolder h && h.isForeignInspect());
            chestService.saveChest(player, top, type, isStaff);
        } finally {
            chestService.leaveCloseSaveGuard(player.getUniqueId());
        }
    }

    private boolean hasFreeStorageSlot(Inventory top, int bottomRowStart) {
        for (int i = 0; i < bottomRowStart; i++) {
            ItemStack s = top.getItem(i);
            if (s == null || s.getType().isAir()) return true;
        }
        return false;
    }
}
