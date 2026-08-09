package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Charge {@code events.yml} — réactions chat, événements faction, drop clé hebdo.
 */
public class EventsConfig {

    private final CantalePlugin plugin;

    // Chat reactions
    private boolean chatEnabled = true;
    private int chatMaxPerDay = 9;
    private int chatStartHour = 10;
    private int chatEndHour = 22;
    private int chatMinIntervalMinutes = 45;
    private int chatMaxIntervalMinutes = 120;
    private int chatRewardMin = 200;
    private int chatRewardMax = 500;
    private int chatTimeoutSeconds = 90;
    private double scrambleChance = 0.55;
    private List<String> scrambleWords = new ArrayList<>();
    private List<Material> breakBlocks = new ArrayList<>();

    // Faction events
    private boolean factionEnabled = true;
    private int factionPerWeekMin = 3;
    private int factionPerWeekMax = 4;
    private int factionDurationMinutes = 60;
    private int factionStartHour = 14;
    private int factionEndHour = 22;
    /** Heures mini entre la fin d'un event et le début du suivant. */
    private int factionMinHoursBetween = 8;
    /** 0 = pas de limite ; 1 = au plus un event par jour civil. */
    private int factionMaxEventsPerDay = 1;
    private List<FactionEventType> factionTypes = new ArrayList<>();
    private int harvestPointsPerItem = 1;
    private int miningPointsPerOre = 1;
    private int pvpPointsPerKill = 10;
    private Map<Material, Integer> harvestItems = new LinkedHashMap<>();
    private List<Material> miningOres = new ArrayList<>();
    private List<Integer> rewardPower = new ArrayList<>();
    private List<Integer> rewardCantox = new ArrayList<>();

    // Weekly key drop
    private boolean weeklyEnabled = true;
    private boolean weeklyRandomDay = true;
    private int weeklyFixedDay = 6;
    private boolean weeklyRandomHour = true;
    private int weeklyFixedHour = 18;
    private int weeklyStartHour = 14;
    private int weeklyEndHour = 21;
    private List<CrateType> weeklyKeyTypes = new ArrayList<>();

    // Player info boss bar
    private boolean bossBarEnabled = true;
    private int bossBarIntervalSeconds = 5;

    public EventsConfig(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("events.yml", false);
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        loadChat(yaml.getConfigurationSection("chat-reactions"));
        loadFaction(yaml.getConfigurationSection("faction-events"));
        loadWeekly(yaml.getConfigurationSection("weekly-key-drop"));
        loadBossBar(yaml.getConfigurationSection("player-info-bossbar"));
    }

    private void loadChat(ConfigurationSection section) {
        scrambleWords.clear();
        breakBlocks.clear();
        if (section == null) {
            applyChatDefaults();
            return;
        }
        chatEnabled = section.getBoolean("enabled", true);
        chatMaxPerDay = section.getInt("max-per-day", 9);
        chatStartHour = section.getInt("start-hour", 10);
        chatEndHour = section.getInt("end-hour", 22);
        chatMinIntervalMinutes = section.getInt("min-interval-minutes", 45);
        chatMaxIntervalMinutes = section.getInt("max-interval-minutes", 120);
        chatRewardMin = section.getInt("reward-min", 200);
        chatRewardMax = section.getInt("reward-max", 500);
        chatTimeoutSeconds = section.getInt("timeout-seconds", 90);
        scrambleChance = section.getDouble("scramble-chance", 0.55);

        for (String word : section.getStringList("words")) {
            if (word != null && !word.isBlank()) {
                scrambleWords.add(word.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (scrambleWords.isEmpty()) {
            scrambleWords.addAll(List.of("cantale", "faction", "diamant", "netherite", "chateau"));
        }

        for (String name : section.getStringList("break-blocks")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null && mat.isBlock()) {
                breakBlocks.add(mat);
            }
        }
        if (breakBlocks.isEmpty()) {
            breakBlocks.addAll(List.of(Material.STONE, Material.DIRT, Material.OAK_LOG));
        }
    }

    private void applyChatDefaults() {
        chatEnabled = true;
        scrambleWords = new ArrayList<>(List.of("cantale", "faction", "diamant"));
        breakBlocks = new ArrayList<>(List.of(Material.STONE, Material.DIRT));
    }

    private void loadFaction(ConfigurationSection section) {
        factionTypes.clear();
        harvestItems.clear();
        miningOres.clear();
        rewardPower.clear();
        rewardCantox.clear();
        if (section == null) {
            factionEnabled = true;
            factionTypes.addAll(List.of(FactionEventType.HARVEST, FactionEventType.MINING, FactionEventType.PVP));
            rewardPower.addAll(List.of(50, 35, 25, 15, 10));
            rewardCantox.addAll(List.of(2000, 1500, 1000, 750, 500));
            return;
        }
        factionEnabled = section.getBoolean("enabled", true);
        factionPerWeekMin = section.getInt("events-per-week-min", 3);
        factionPerWeekMax = section.getInt("events-per-week-max", 4);
        if (factionPerWeekMax < factionPerWeekMin) {
            factionPerWeekMax = factionPerWeekMin;
        }
        factionDurationMinutes = Math.max(1, section.getInt("duration-minutes", 60));
        factionStartHour = section.getInt("start-hour", 14);
        factionEndHour = section.getInt("end-hour", 22);
        factionMinHoursBetween = Math.max(0, section.getInt("min-hours-between-events", 8));
        factionMaxEventsPerDay = Math.max(0, section.getInt("max-events-per-day", 1));

        for (String typeName : section.getStringList("types")) {
            try {
                factionTypes.add(FactionEventType.valueOf(typeName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("[Events] Type d'événement inconnu : " + typeName);
            }
        }
        if (factionTypes.isEmpty()) {
            factionTypes.addAll(List.of(FactionEventType.HARVEST, FactionEventType.MINING, FactionEventType.PVP));
        }

        ConfigurationSection points = section.getConfigurationSection("points");
        if (points != null) {
            harvestPointsPerItem = points.getInt("harvest-per-item", 1);
            miningPointsPerOre = points.getInt("mining-per-ore", 1);
            pvpPointsPerKill = points.getInt("pvp-per-kill", 10);
        }

        ConfigurationSection harvestSection = section.getConfigurationSection("harvest-items");
        if (harvestSection != null) {
            for (String key : harvestSection.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    harvestItems.put(mat, Math.max(1, harvestSection.getInt(key, 1)));
                }
            }
        }
        if (harvestItems.isEmpty()) {
            harvestItems.put(Material.WHEAT, 1);
            harvestItems.put(Material.CARROT, 1);
            harvestItems.put(Material.POTATO, 1);
        }

        for (String name : section.getStringList("mining-ores")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                miningOres.add(mat);
            }
        }
        if (miningOres.isEmpty()) {
            miningOres.add(Material.DIAMOND_ORE);
            miningOres.add(Material.IRON_ORE);
            miningOres.add(Material.ANCIENT_DEBRIS);
        }

        ConfigurationSection rewards = section.getConfigurationSection("rewards");
        if (rewards != null) {
            rewardPower.addAll(rewards.getIntegerList("power"));
            rewardCantox.addAll(rewards.getIntegerList("cantox-per-participant"));
        }
        if (rewardPower.isEmpty()) {
            rewardPower.addAll(List.of(50, 35, 25, 15, 10));
        }
        if (rewardCantox.isEmpty()) {
            rewardCantox.addAll(List.of(2000, 1500, 1000, 750, 500));
        }
    }

    private void loadWeekly(ConfigurationSection section) {
        weeklyKeyTypes.clear();
        if (section == null) {
            weeklyEnabled = true;
            weeklyKeyTypes.addAll(List.of(CrateType.RARE, CrateType.EPIC, CrateType.MYTHIC));
            return;
        }
        weeklyEnabled = section.getBoolean("enabled", true);
        weeklyRandomDay = section.getBoolean("random-day", true);
        weeklyFixedDay = section.getInt("fixed-day", 6);
        weeklyRandomHour = section.getBoolean("random-hour", true);
        weeklyFixedHour = section.getInt("fixed-hour", 18);
        weeklyStartHour = section.getInt("start-hour", 14);
        weeklyEndHour = section.getInt("end-hour", 21);

        for (String name : section.getStringList("key-types")) {
            try {
                CrateType type = CrateType.valueOf(name.toUpperCase(Locale.ROOT));
                if (type != CrateType.LEGENDARY && type != CrateType.VOTE) {
                    weeklyKeyTypes.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("[Events] Type de clé inconnu : " + name);
            }
        }
        if (weeklyKeyTypes.isEmpty()) {
            weeklyKeyTypes.addAll(List.of(CrateType.RARE, CrateType.EPIC, CrateType.MYTHIC));
        }
    }

    // --- Getters chat ---
    public boolean isChatEnabled() { return chatEnabled; }
    public int getChatMaxPerDay() { return chatMaxPerDay; }
    public int getChatStartHour() { return chatStartHour; }
    public int getChatEndHour() { return chatEndHour; }
    public int getChatMinIntervalMinutes() { return chatMinIntervalMinutes; }
    public int getChatMaxIntervalMinutes() { return chatMaxIntervalMinutes; }
    public int getChatRewardMin() { return chatRewardMin; }
    public int getChatRewardMax() { return chatRewardMax; }
    public int getChatTimeoutSeconds() { return chatTimeoutSeconds; }
    public double getScrambleChance() { return scrambleChance; }
    public List<String> getScrambleWords() { return Collections.unmodifiableList(scrambleWords); }
    public List<Material> getBreakBlocks() { return Collections.unmodifiableList(breakBlocks); }

    // --- Getters faction ---
    public boolean isFactionEnabled() { return factionEnabled; }
    public int getFactionPerWeekMin() { return factionPerWeekMin; }
    public int getFactionPerWeekMax() { return factionPerWeekMax; }
    public int getFactionDurationMinutes() { return factionDurationMinutes; }
    public int getFactionStartHour() { return factionStartHour; }
    public int getFactionEndHour() { return factionEndHour; }
    public int getFactionMinHoursBetween() { return factionMinHoursBetween; }
    public int getFactionMaxEventsPerDay() { return factionMaxEventsPerDay; }
    public List<FactionEventType> getFactionTypes() { return Collections.unmodifiableList(factionTypes); }
    public int getHarvestPointsPerItem() { return harvestPointsPerItem; }
    public int getMiningPointsPerOre() { return miningPointsPerOre; }
    public int getPvpPointsPerKill() { return pvpPointsPerKill; }
    public Map<Material, Integer> getHarvestItems() { return Collections.unmodifiableMap(harvestItems); }
    public List<Material> getMiningOres() { return Collections.unmodifiableList(miningOres); }
    public List<Integer> getRewardPower() { return Collections.unmodifiableList(rewardPower); }
    public List<Integer> getRewardCantox() { return Collections.unmodifiableList(rewardCantox); }

    private void loadBossBar(ConfigurationSection section) {
        if (section == null) {
            bossBarEnabled = true;
            bossBarIntervalSeconds = 5;
            return;
        }
        bossBarEnabled = section.getBoolean("enabled", true);
        bossBarIntervalSeconds = Math.max(2, section.getInt("interval-seconds", 5));
    }

    // --- Getters weekly ---
    public boolean isWeeklyEnabled() { return weeklyEnabled; }
    public boolean isWeeklyRandomDay() { return weeklyRandomDay; }
    public int getWeeklyFixedDay() { return weeklyFixedDay; }
    public boolean isWeeklyRandomHour() { return weeklyRandomHour; }
    public int getWeeklyFixedHour() { return weeklyFixedHour; }
    public int getWeeklyStartHour() { return weeklyStartHour; }
    public int getWeeklyEndHour() { return weeklyEndHour; }
    public List<CrateType> getWeeklyKeyTypes() { return Collections.unmodifiableList(weeklyKeyTypes); }

    // --- Getters boss bar ---
    public boolean isBossBarEnabled() { return bossBarEnabled; }
    public int getBossBarIntervalSeconds() { return bossBarIntervalSeconds; }
}
