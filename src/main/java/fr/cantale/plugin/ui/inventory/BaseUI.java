package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseUI implements Listener {

    protected final CantalePlugin plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final String title;
    protected final int size;
    private boolean registered = false;

    public BaseUI(CantalePlugin plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.size = rows * 9;
        this.inventory = Bukkit.createInventory(null, size, this.title);
    }

    public void open() {
        init();
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        player.openInventory(inventory);
    }

    public void close() {
        player.closeInventory();
    }

    public String getTitle() {
        return title;
    }

    protected void setItem(int slot, Material material, String name, String... lore) {
        if (slot < 0 || slot >= size) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    protected void setItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= size) return;
        inventory.setItem(slot, item);
    }

    protected void fillEmpty(Material material) {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                setItem(i, material, " ");
            }
        }
    }

    protected void fillEmpty() {
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    protected void addNavigationBar(boolean showBack, boolean showRefresh) {
        if (showBack) {
            setItem(size - 9, Material.ARROW, "§7§l← Retour", "§7Cliquez pour revenir");
        }
        setItem(size - 5, Material.BARRIER, "§c§l✕ Fermer", "§7Cliquez pour fermer");
        if (showRefresh) {
            setItem(size - 1, Material.COMPASS, "§e§l↻ Actualiser", "§7Cliquez pour rafraîchir");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Match by inventory identity: same title is reused across pages (e.g. ShopCategoryUI).
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();

        // Empêcher tout déplacement d'item vers le menu ou à l'intérieur du menu
        if (clicked == top || event.isShiftClick()) {
            event.setCancelled(true);
        }

        if (clicked != top) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= size) return;

        // Clics sur la barre de navigation
        if (slot == size - 9 && inventory.getItem(slot) != null && inventory.getItem(slot).getType() == Material.ARROW) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == size - 5 && inventory.getItem(slot) != null && inventory.getItem(slot).getType() == Material.BARRIER) {
            close();
            return;
        }
        if (slot == size - 1 && inventory.getItem(slot) != null && inventory.getItem(slot).getType() == Material.COMPASS) {
            init();
            return;
        }

        handleClick(slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;

        // Do NOT unregister synchronously, and do NOT trust title or the close-event inventory
        // identity alone. During sync openInventory(newUI) from a click handler:
        // 1) new UI registers, 2) old inventory closes, 3) the close view can already point at
        // the new top inventory — so a same-tick identity/title match would unregister the
        // UI the player just opened (dead clicks). Defer: only drop the listener if this
        // inventory is no longer the one being viewed.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!registered) return;
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == inventory) {
                return;
            }
            HandlerList.unregisterAll(this);
            registered = false;
        });
    }

    protected abstract void init();
    protected abstract void handleClick(int slot);
}
