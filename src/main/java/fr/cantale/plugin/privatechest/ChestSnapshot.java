package fr.cantale.plugin.privatechest;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record ChestSnapshot(UUID ownerUuid, ChestType type, int size, ItemStack[] contents) {
}
