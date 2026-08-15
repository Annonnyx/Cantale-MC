package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuestConfig {

    private final CantalePlugin plugin;
    private int seasonDurationDays = 75;
    private int soloDaily = 5;
    private int soloWeekly = 3;
    private int soloSeasonal = 2;
    private int factionDaily = 12;
    private int factionWeekly = 6;
    private int factionSeasonal = 3;
    private double weeklyMult = 6.0;
    private double seasonalMult = 220.0;
    private double factionMult = 8.0;
    private int variancePercent = 15;
    private double bonusChance = 8;
    private int bonusCantoxMin = 500;
    private int bonusCantoxMax = 2500;
    private double bonusLifeChance = 20;
    private int powerDaily = 1;
    private int powerWeekly = 7;
    private int powerSeasonal = 200;
    private int participantCantox = 150;
    private List<Integer> topCantox = List.of(800, 500, 300);
    private List<CrateType> topKeys = List.of(CrateType.EPIC, CrateType.RARE, CrateType.RARE);
    private double weeklySpawnerChance = 15;
    private final List<QuestTemplate> templates = new ArrayList<>();

    public QuestConfig(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("quests.yml", false);
        File file = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        seasonDurationDays = Math.max(14, yaml.getInt("season.duration-days", 75));
        ConfigurationSection solo = yaml.getConfigurationSection("pools.solo");
        if (solo != null) {
            soloDaily = Math.max(1, solo.getInt("daily", 5));
            soloWeekly = Math.max(1, solo.getInt("weekly", 3));
            soloSeasonal = Math.max(1, solo.getInt("seasonal", 2));
        }
        ConfigurationSection fac = yaml.getConfigurationSection("pools.faction");
        if (fac != null) {
            factionDaily = Math.max(1, fac.getInt("daily", 12));
            factionWeekly = Math.max(1, fac.getInt("weekly", 6));
            factionSeasonal = Math.max(1, fac.getInt("seasonal", 3));
        }
        ConfigurationSection mult = yaml.getConfigurationSection("multipliers");
        if (mult != null) {
            weeklyMult = Math.max(1, mult.getDouble("weekly", 6));
            seasonalMult = Math.max(1, mult.getDouble("seasonal", 220));
            factionMult = Math.max(1, mult.getDouble("faction", 8));
            variancePercent = Math.max(0, mult.getInt("variance-percent", 15));
        }
        ConfigurationSection bonus = yaml.getConfigurationSection("bonus");
        if (bonus != null) {
            bonusChance = bonus.getDouble("chance-percent", 8);
            bonusCantoxMin = bonus.getInt("cantox-min", 500);
            bonusCantoxMax = Math.max(bonusCantoxMin, bonus.getInt("cantox-max", 2500));
            bonusLifeChance = bonus.getDouble("life-chance-percent", 20);
        }
        ConfigurationSection fr = yaml.getConfigurationSection("faction-rewards");
        if (fr != null) {
            powerDaily = fr.getInt("power-daily", 1);
            powerWeekly = fr.getInt("power-weekly", 7);
            powerSeasonal = fr.getInt("power-seasonal", 200);
            participantCantox = fr.getInt("participant-cantox", 150);
            weeklySpawnerChance = fr.getDouble("weekly-spawner-chance-percent", 15);
            List<Integer> tc = fr.getIntegerList("top-cantox");
            if (!tc.isEmpty()) topCantox = List.copyOf(tc);
            List<String> keys = fr.getStringList("top-keys");
            if (!keys.isEmpty()) {
                List<CrateType> parsed = new ArrayList<>();
                for (String k : keys) {
                    try {
                        parsed.add(CrateType.valueOf(k.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!parsed.isEmpty()) topKeys = List.copyOf(parsed);
            }
        }

        templates.clear();
        List<Map<?, ?>> raw = yaml.getMapList("templates");
        for (Map<?, ?> map : raw) {
            try {
                String id = String.valueOf(map.get("id"));
                String title = String.valueOf(map.get("title"));
                QuestObjectiveType obj = QuestObjectiveType.valueOf(String.valueOf(map.get("objective")).toUpperCase(Locale.ROOT));
                String target = String.valueOf(map.get("target"));
                int daily = Integer.parseInt(String.valueOf(map.get("daily")));
                if (id.isBlank() || daily < 1) continue;
                templates.add(new QuestTemplate(id, title, obj, target, daily));
            } catch (Exception e) {
                plugin.getLogger().warning("[Quêtes] Template ignoré : " + map + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info("[Quêtes] " + templates.size() + " templates chargés.");
    }

    public int poolSize(QuestScope scope, QuestPeriod period) {
        return switch (scope) {
            case SOLO -> switch (period) {
                case DAILY -> soloDaily;
                case WEEKLY -> soloWeekly;
                case SEASONAL -> soloSeasonal;
            };
            case FACTION -> switch (period) {
                case DAILY -> factionDaily;
                case WEEKLY -> factionWeekly;
                case SEASONAL -> factionSeasonal;
            };
        };
    }

    public double amountMultiplier(QuestPeriod period, QuestScope scope) {
        double base = switch (period) {
            case DAILY -> 1.0;
            case WEEKLY -> weeklyMult;
            case SEASONAL -> seasonalMult;
        };
        if (scope == QuestScope.FACTION) base *= factionMult;
        return base;
    }

    public int seasonDurationDays() { return seasonDurationDays; }
    public int variancePercent() { return variancePercent; }
    public double bonusChance() { return bonusChance; }
    public int bonusCantoxMin() { return bonusCantoxMin; }
    public int bonusCantoxMax() { return bonusCantoxMax; }
    public double bonusLifeChance() { return bonusLifeChance; }
    public int powerFor(QuestPeriod period) {
        return switch (period) {
            case DAILY -> powerDaily;
            case WEEKLY -> powerWeekly;
            case SEASONAL -> powerSeasonal;
        };
    }
    public int participantCantox() { return participantCantox; }
    public List<Integer> topCantox() { return topCantox; }
    public List<CrateType> topKeys() { return topKeys; }
    public double weeklySpawnerChance() { return weeklySpawnerChance; }
    public List<QuestTemplate> templates() { return Collections.unmodifiableList(templates); }
}
