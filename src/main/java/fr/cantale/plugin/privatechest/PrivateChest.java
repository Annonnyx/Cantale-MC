package fr.cantale.plugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PrivateChest implements InventoryHolder {
    private final UUID ownerUuid;
    private final Inventory inventory;
    private final int size;

    public PrivateChest(UUID ownerUuid, int size) {
        this.ownerUuid = ownerUuid;
        this.size = size;
        this.inventory = Bukkit.createInventory(this, size, "§6Coffre Privé §7#1");
    }

    public PrivateChest(UUID ownerUuid, ItemStack[] contents) {
        this(ownerUuid, 27);
        if (contents != null && contents.length <= size) {
            inventory.setContents(contents);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public ItemStack[] getContents() {
        return inventory.getContents();
    }

    public void setContents(ItemStack[] contents) {
        inventory.setContents(contents);
    }
}
