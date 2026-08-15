package fr.cantale.plugin.collections;

import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Material;

import java.util.List;

public record CollectionCategory(
        String id,
        String label,
        Material icon,
        boolean hidden,
        List<String> keys,
        List<Integer> tiers,
        List<CollectionReward> rewards
) {
}
