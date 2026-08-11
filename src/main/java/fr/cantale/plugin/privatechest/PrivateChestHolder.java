package fr.cantale.plugin.privatechest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder dédié aux coffres privés virtuels — identification fiable
 * (ne dépend plus uniquement du titre coloré).
 */
public class PrivateChestHolder implements InventoryHolder {

    private final ChestType type;
    private Inventory inventory;

    public PrivateChestHolder(ChestType type) {
        this.type = type;
    }

    public ChestType getType() {
        return type;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
