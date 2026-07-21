package fr.cantale.plugin.crate;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CrateReward(
        String name,
        double weight,
        int cantox,
        List<ItemStack> items,
        int lives,
        String message
) {
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }
}
