package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * GUI lecture seule : inventaire au moment de la dernière mort (staff).
 * Layout Paper : slots 0-35 stockage, 36-39 armure, 40 offhand.
 */
public class LastDeathInventoryUI extends BaseUI {

    private final ItemStack[] contents;
    private final String targetName;

    public LastDeathInventoryUI(CantalePlugin plugin, Player viewer, String targetName, ItemStack[] contents) {
        super(plugin, viewer, "§8[§6Cantale§8] §7Mort — " + targetName, 6);
        this.contents = contents != null ? contents : new ItemStack[0];
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        inventory.clear();
        fillEmpty();

        setItem(4, Material.SKELETON_SKULL, "§c§lInventaire à la mort",
                "§7Joueur : §e" + targetName,
                "§7Lecture seule — aucun item n'est restitué",
                "§8Stockage · armure · main secondaire");

        // Stockage principal (0-35) → grille 9-44
        for (int i = 0; i < 36 && i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && !stack.getType().isAir()) {
                inventory.setItem(9 + i, stack.clone());
            }
        }

        // Armure 36-39 → ligne du bas (slots 0-3), labels si vide
        String[] armorLabels = {"§7Bottes", "§7Jambières", "§7Plastron", "§7Casque"};
        for (int a = 0; a < 4; a++) {
            int src = 36 + a;
            ItemStack stack = src < contents.length ? contents[src] : null;
            if (stack != null && !stack.getType().isAir()) {
                inventory.setItem(a, stack.clone());
            } else {
                setItem(a, Material.GRAY_STAINED_GLASS_PANE, armorLabels[a], "§8Vide");
            }
        }

        // Offhand 40
        ItemStack off = contents.length > 40 ? contents[40] : null;
        if (off != null && !off.getType().isAir()) {
            inventory.setItem(8, off.clone());
        } else {
            setItem(8, Material.GRAY_STAINED_GLASS_PANE, "§7Main secondaire", "§8Vide");
        }

        setItem(size - 5, Material.BARRIER, "§c§l✕ Fermer", "§7Fermer (aucun item pris)");
    }

    @Override
    protected void handleClick(int slot) {
        // Tout est bloqué dans onClick ci-dessous.
    }

    @EventHandler
    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked != inventory) return;

        if (event.getSlot() == size - 5) {
            close();
        }
    }

    @EventHandler
    @Override
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;
        event.setCancelled(true);
    }
}
