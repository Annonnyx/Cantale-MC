package fr.cantale.plugin.collections;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import fr.cantale.plugin.spawners.SpawnerRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CollectionConfig {

    private final CantalePlugin plugin;
    private final Map<String, CollectionCategory> categories = new LinkedHashMap<>();

    public CollectionConfig(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("collections.yml", false);
        File file = new File(plugin.getDataFolder(), "collections.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Integer> defaultTiers = yaml.getIntegerList("default-tiers");
        if (defaultTiers.isEmpty()) {
            defaultTiers = List.of(1000, 5000, 25000, 100000, 500000, 2500000, 10000000);
        }
        List<CollectionReward> defaultRewards = parseRewards(yaml.getMapList("default-rewards"));

        categories.clear();
        ConfigurationSection cats = yaml.getConfigurationSection("categories");
        if (cats == null) return;
        for (String id : cats.getKeys(false)) {
            ConfigurationSection s = cats.getConfigurationSection(id);
            if (s == null) continue;
            String label = s.getString("label", id);
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;
            boolean hidden = s.getBoolean("hidden", false);
            List<String> keys = s.getStringList("keys");
            List<Integer> tiers = s.getIntegerList("tiers");
            if (tiers.isEmpty()) tiers = defaultTiers;
            List<CollectionReward> rewards = parseRewards(s.getMapList("rewards"));
            if (rewards.isEmpty()) rewards = defaultRewards;
            categories.put(id, new CollectionCategory(id, label, icon, hidden, List.copyOf(keys),
                    List.copyOf(tiers), List.copyOf(rewards)));
        }
        plugin.getLogger().info("[Collections] " + categories.size() + " catégories.");
    }

    private List<CollectionReward> parseRewards(List<Map<?, ?>> maps) {
        List<CollectionReward> list = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            int cantox = toInt(map.get("cantox"));
            int lives = toInt(map.get("lives"));
            CrateType key = null;
            Object k = map.get("key");
            if (k != null) {
                try {
                    key = CrateType.valueOf(String.valueOf(k).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
            SpawnerRarity spawner = parseSpawnerRarity(map.get("spawner"));
            list.add(new CollectionReward(cantox, lives, key, spawner));
        }
        return list;
    }

    /** {@code spawner: true} → RARE (compat) ; {@code spawner: RARE|EPIC|MYTHIC} → rareté exacte. */
    private static SpawnerRarity parseSpawnerRarity(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) return null;
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) return SpawnerRarity.RARE;
        try {
            return SpawnerRarity.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Map<String, CollectionCategory> categories() {
        return categories;
    }
}
