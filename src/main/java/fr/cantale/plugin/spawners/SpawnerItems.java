package fr.cantale.plugin.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SpawnerItems {

    private final SpawnerKeys keys;

    public SpawnerItems(SpawnerKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(SpawnerMob mob, int tier) {
        int clamped = Math.max(1, Math.min(8, tier));
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, mob.name());
        pdc.set(keys.tier, PersistentDataType.INTEGER, clamped);
        pdc.set(keys.rarity, PersistentDataType.STRING, mob.rarity().name());

        meta.displayName(Component.text(mob.itemDisplayName(clamped), mob.rarity().color())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Rareté : ", NamedTextColor.GRAY)
                        .append(Component.text(mob.rarity().frenchName(), mob.rarity().color()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Palier : ", NamedTextColor.GRAY)
                        .append(Component.text("x" + clamped, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Pose uniquement dans tes claims.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Touché de soie (toute pioche) pour récupérer.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        if (meta instanceof BlockStateMeta blockMeta
                && blockMeta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(mob.entityType());
            blockMeta.setBlockState(spawner);
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCantaleSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && keys.isSpawnerItem(meta.getPersistentDataContainer());
    }

    public SpawnerMob readMob(ItemStack item) {
        if (!isCantaleSpawner(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(keys.type, PersistentDataType.STRING);
        return SpawnerMob.fromName(raw).orElse(null);
    }

    public int readTier(ItemStack item) {
        if (!isCantaleSpawner(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer tier = meta.getPersistentDataContainer().get(keys.tier, PersistentDataType.INTEGER);
        if (tier == null) return 1;
        return Math.max(1, Math.min(8, tier));
    }
}
