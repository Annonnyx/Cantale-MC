package fr.cantale.plugin.spawners;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class SpawnerKeys {

    public final NamespacedKey type;
    public final NamespacedKey tier;
    public final NamespacedKey rarity;
    public final NamespacedKey mobFlag;
    public final NamespacedKey hologram;

    public SpawnerKeys(CantalePlugin plugin) {
        this.type = new NamespacedKey(plugin, "spawner_type");
        this.tier = new NamespacedKey(plugin, "spawner_tier");
        this.rarity = new NamespacedKey(plugin, "spawner_rarity");
        this.mobFlag = new NamespacedKey(plugin, "spawner_mob");
        this.hologram = new NamespacedKey(plugin, "spawner_holo");
    }

    public boolean isSpawnerItem(PersistentDataContainer pdc) {
        return pdc != null && pdc.has(type, PersistentDataType.STRING);
    }

    public boolean isSpawnerMob(PersistentDataContainer pdc) {
        return pdc != null && pdc.has(mobFlag, PersistentDataType.BYTE);
    }
}
