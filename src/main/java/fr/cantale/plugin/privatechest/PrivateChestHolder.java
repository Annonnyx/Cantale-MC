package fr.cantale.plugin.privatechest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder dédié aux coffres privés virtuels — identification fiable
 * (ne dépend plus uniquement du titre coloré).
 * <p>
 * Pour l'inspection staff ({@code /adminpc}), {@link #getOwnerUuid()} pointe vers
 * le propriétaire du coffre et {@link #isForeignInspect()} est vrai.
 */
public class PrivateChestHolder implements InventoryHolder {

    private final ChestType type;
    private final UUID ownerUuid;
    private final boolean foreignInspect;
    private Inventory inventory;

    public PrivateChestHolder(ChestType type) {
        this(type, null, false);
    }

    public PrivateChestHolder(ChestType type, UUID ownerUuid, boolean foreignInspect) {
        this.type = type;
        this.ownerUuid = ownerUuid;
        this.foreignInspect = foreignInspect;
    }

    public ChestType getType() {
        return type;
    }

    /**
     * UUID du propriétaire du coffre (inspection staff), ou {@code null} si coffre du viewer.
     */
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isForeignInspect() {
        return foreignInspect;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
