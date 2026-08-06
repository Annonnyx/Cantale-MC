package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.events.FactionEventManager;
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

import java.util.List;

/**
 * UI de dépôt pour l'événement HARVEST (/recolte).
 */
public class HarvestDepositUI implements Listener {

    private final CantalePlugin plugin;
    private final Player player;
    private final FactionEventManager factionEventManager;
    private final Inventory inventory;
    private final String title;
    private boolean deposited;

    public HarvestDepositUI(CantalePlugin plugin, Player player, FactionEventManager factionEventManager) {
        this.plugin = plugin;
        this.player = player;
        this.factionEventManager = factionEventManager;
        this.title = ChatColor.translateAlternateColorCodes('&', "&8[&6Récolte&8] &7Déposer");
        this.inventory = Bukkit.createInventory(null, 36, title);
    }

    public void open() {
        for (int i = 27; i < 36; i++) {
            inventory.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(30, pane(Material.LIME_CONCRETE, "§a§lDéposer",
                "§7Convertit tes récoltes en points faction."));
        inventory.setItem(32, pane(Material.RED_CONCRETE, "§c§lAnnuler",
                "§7Ferme et te rend les items."));
        inventory.setItem(31, pane(Material.WHEAT, "§e§lRécolte",
                "§7Place blé, carottes, pommes de terre…"));

        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getView().getTitle().equals(title)) return;

        int raw = event.getRawSlot();
        // Zone dépôt 0-26 : autoriser
        if (raw >= 0 && raw < 27) {
            return;
        }
        // Inventaire joueur : autoriser
        if (raw >= 36) {
            return;
        }

        event.setCancelled(true);
        if (raw == 30) {
            doDeposit();
        } else if (raw == 32) {
            returnItems();
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getView().getTitle().equals(title)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 27 && slot < 36) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getView().getTitle().equals(title)) return;
        if (!deposited) {
            returnItems();
        }
        HandlerList.unregisterAll(this);
    }

    private void doDeposit() {
        ItemStack[] contents = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            contents[i] = inventory.getItem(i);
        }
        int result = factionEventManager.depositHarvest(player, contents);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, contents[i]);
        }
        deposited = true;
        returnItems();
        player.closeInventory();
        if (result < 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Récolte&8] &cPas de récolte en cours."));
        }
    }

    private void returnItems() {
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            inventory.setItem(i, null);
            var leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private ItemStack pane(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
