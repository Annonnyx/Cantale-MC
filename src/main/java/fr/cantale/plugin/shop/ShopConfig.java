package fr.cantale.plugin.shop;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Charge {@code shop.yml} et résout, pour chaque {@link Material}, s'il est
 * négociable au shop et à quel prix — via une entrée exacte, sinon via la
 * première règle de pattern correspondante.
 * <p>
 * Une liste noire dure est appliquée en code, indépendamment du contenu de
 * shop.yml : ces blocs/items ne seront jamais négociables au shop, quelle
 * que soit la configuration.
 */
public class ShopConfig {

    /** Jamais achetable ni vendable au shop, quoi qu'il arrive (risques d'exploit/duplication). */
    private static final Set<Material> HARD_BLACKLIST = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.DEBUG_STICK,
            Material.KNOWLEDGE_BOOK,
            Material.END_PORTAL_FRAME,
            Material.DRAGON_EGG,
            Material.WRITTEN_BOOK,
            Material.WRITABLE_BOOK,
            Material.SPAWNER,
            Material.SAND,
            Material.GUNPOWDER
    );

    private final CantalePlugin plugin;
    private final List<ShopCategory> categories = new ArrayList<>();
    private final Map<Material, ShopItem> resolved = new LinkedHashMap<>();
    private final Map<String, List<ShopItem>> byCategory = new LinkedHashMap<>();

    public ShopConfig(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        resolved.clear();
        byCategory.clear();

        File file = new File(plugin.getDataFolder(), "shop.yml");
        // Ne pas appeler saveResource si le fichier existe déjà : Bukkit logue un WARN
        // trompeur (« Could not save… because … already exists ») alors que c'est le
        // comportement voulu (replace=false). Copie JAR → disque uniquement au 1er run.
        if (!file.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        // Fusionne les items du JAR absents du fichier disque (ex. HOPPER réintroduit)
        // sans écraser les prix/customisations existantes.
        mergeMissingItemsFromJar(file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        loadCategories(yaml);
        List<PatternRule> patterns = loadPatterns(yaml);
        Map<Material, ShopItem> exactItems = loadExactItems(yaml);

        for (Material material : Material.values()) {
            // Patterns like _STEM / _WOOD also hit LEGACY_* and block-only materials
            // (MELON_STEM, ATTACHED_*_STEM, BIG_DRIPLEAF_STEM…). Those are not valid
            // ItemStacks — including them makes ShopCategoryUI.init() throw and the
            // category (notably BOIS) fail to open.
            if (material.isLegacy() || !material.isItem()) continue;
            if (HARD_BLACKLIST.contains(material)) continue;

            ShopItem item = exactItems.get(material);
            if (item == null) {
                item = matchPattern(material, patterns);
            }
            if (item == null) continue;

            resolved.put(material, item);
            byCategory.computeIfAbsent(item.getCategoryId(), k -> new ArrayList<>()).add(item);
        }

        for (List<ShopItem> items : byCategory.values()) {
            items.sort((a, b) -> a.getMaterial().name().compareTo(b.getMaterial().name()));
        }
    }

    private void loadCategories(YamlConfiguration yaml) {
        List<?> list = yaml.getList("categories");
        if (list == null) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) continue;
            String id = String.valueOf(map.get("id"));
            Object displayNameRaw = map.get("display-name");
            String displayName = displayNameRaw != null ? String.valueOf(displayNameRaw) : id;
            Material icon = parseMaterial(String.valueOf(map.get("icon")), Material.CHEST);
            categories.add(new ShopCategory(id, displayName, icon));
        }
    }

    private List<PatternRule> loadPatterns(YamlConfiguration yaml) {
        List<PatternRule> rules = new ArrayList<>();
        List<?> list = yaml.getList("patterns");
        if (list == null) return rules;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) continue;
            List<String> matches = new ArrayList<>();
            Object matchObj = map.get("match");
            if (matchObj instanceof List<?> matchList) {
                for (Object m : matchList) matches.add(String.valueOf(m));
            }
            if (matches.isEmpty()) continue;

            String category = String.valueOf(map.get("category"));
            double buy = toDouble(map.get("buy"), 0);
            double sell = toDouble(map.get("sell"), 0);
            boolean buyEnabled = toBoolean(map.get("buy-enabled"), buy > 0);
            boolean sellEnabled = toBoolean(map.get("sell-enabled"), sell > 0);
            int dailySellCap = (int) toDouble(map.get("daily-sell-cap"), 0);
            int dailyBuyCap = (int) toDouble(map.get("daily-buy-cap"), 0);

            rules.add(new PatternRule(matches, category, buy, sell, buyEnabled, sellEnabled, dailySellCap, dailyBuyCap));
        }
        return rules;
    }

    /**
     * Ajoute dans le {@code shop.yml} disque les clés {@code items.*} présentes dans le JAR
     * mais absentes du fichier live. Ne modifie jamais une entrée déjà définie.
     */
    private void mergeMissingItemsFromJar(File diskFile) {
        try (InputStream in = plugin.getResource("shop.yml")) {
            if (in == null || !diskFile.exists()) return;
            YamlConfiguration jarYaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration diskYaml = YamlConfiguration.loadConfiguration(diskFile);

            ConfigurationSection jarItems = jarYaml.getConfigurationSection("items");
            if (jarItems == null) return;

            ConfigurationSection diskItems = diskYaml.getConfigurationSection("items");
            if (diskItems == null) {
                diskYaml.createSection("items");
                diskItems = diskYaml.getConfigurationSection("items");
            }
            if (diskItems == null) return;

            int added = 0;
            for (String key : jarItems.getKeys(false)) {
                if (diskItems.contains(key)) continue;
                Object value = jarItems.get(key);
                diskItems.set(key, value);
                added++;
                plugin.getLogger().info("[Shop] Item manquant fusionné depuis le JAR : items." + key);
            }
            if (added > 0) {
                diskYaml.save(diskFile);
                plugin.getLogger().info("[Shop] " + added + " item(s) ajouté(s) au shop.yml disque (merge non destructif).");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Shop] Échec merge items JAR → shop.yml : " + e.getMessage(), e);
        }
    }

    private Map<Material, ShopItem> loadExactItems(YamlConfiguration yaml) {
        Map<Material, ShopItem> items = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return items;

        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key, null);
            if (material == null) {
                plugin.getLogger().warning("[Shop] Material inconnu dans shop.yml (items." + key + ") — ignoré.");
                continue;
            }

            String category = "BLOCS";
            double buy = 0;
            double sell = 0;
            boolean buyEnabled;
            boolean sellEnabled;
            int dailySellCap = 0;
            int dailyBuyCap = 0;

            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry != null) {
                category = entry.getString("category", "BLOCS");
                buy = entry.getDouble("buy", 0);
                sell = entry.getDouble("sell", 0);
                buyEnabled = entry.getBoolean("buy-enabled", buy > 0);
                sellEnabled = entry.getBoolean("sell-enabled", sell > 0);
                dailySellCap = entry.getInt("daily-sell-cap", 0);
                dailyBuyCap = entry.getInt("daily-buy-cap", 0);
            } else {
                // Flow-style YAML {k: v} parfois exposé comme Map et non ConfigurationSection
                Object raw = section.get(key);
                if (!(raw instanceof Map<?, ?> map)) continue;
                Object catObj = map.get("category");
                category = catObj != null ? String.valueOf(catObj) : "BLOCS";
                buy = toDouble(map.get("buy"), 0);
                sell = toDouble(map.get("sell"), 0);
                buyEnabled = toBoolean(map.get("buy-enabled"), buy > 0);
                sellEnabled = toBoolean(map.get("sell-enabled"), sell > 0);
                dailySellCap = (int) toDouble(map.get("daily-sell-cap"), 0);
                dailyBuyCap = (int) toDouble(map.get("daily-buy-cap"), 0);
            }

            items.put(material, new ShopItem(material, category, buy, sell, buyEnabled, sellEnabled, dailySellCap, dailyBuyCap));
        }
        return items;
    }

    private ShopItem matchPattern(Material material, List<PatternRule> patterns) {
        for (PatternRule rule : patterns) {
            if (rule.matches(material.name())) {
                return new ShopItem(material, rule.category, rule.buy, rule.sell,
                        rule.buyEnabled, rule.sellEnabled, rule.dailySellCap, rule.dailyBuyCap);
            }
        }
        return null;
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        return def;
    }

    private static boolean toBoolean(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        return def;
    }

    /** @return l'item résolu (prix + règles) pour ce Material, ou {@code null} si non négociable au shop. */
    public ShopItem resolve(Material material) {
        return resolved.get(material);
    }

    public boolean isHardBlacklisted(Material material) {
        return HARD_BLACKLIST.contains(material);
    }

    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<ShopItem> getItemsInCategory(String categoryId) {
        return byCategory.getOrDefault(categoryId, Collections.emptyList());
    }

    private static final class PatternRule {
        final List<String> matches;
        final String category;
        final double buy;
        final double sell;
        final boolean buyEnabled;
        final boolean sellEnabled;
        final int dailySellCap;
        final int dailyBuyCap;

        PatternRule(List<String> matches, String category, double buy, double sell,
                    boolean buyEnabled, boolean sellEnabled, int dailySellCap, int dailyBuyCap) {
            this.matches = matches;
            this.category = category;
            this.buy = buy;
            this.sell = sell;
            this.buyEnabled = buyEnabled;
            this.sellEnabled = sellEnabled;
            this.dailySellCap = dailySellCap;
            this.dailyBuyCap = dailyBuyCap;
        }

        boolean matches(String materialName) {
            for (String m : matches) {
                if (materialName.contains(m)) return true;
            }
            return false;
        }
    }
}
