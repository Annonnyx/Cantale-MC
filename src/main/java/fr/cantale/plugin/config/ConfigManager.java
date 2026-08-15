package fr.cantale.plugin.config;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConfigManager {

    private final CantalePlugin plugin;
    private final FileConfiguration config;

    public ConfigManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getDatabaseFile() {
        return config.getString("database.sqlite.file", "cantale.db");
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "cantale");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "");
    }

    public int getDefaultLives() {
        return config.getInt("lives.default-lives", 3);
    }

    public int getMinLives() {
        return config.getInt("lives.min-lives", 0);
    }

    public double getLifeBuyPrice() {
        return config.getDouble("lives.buy-price", 10000.0);
    }

    public boolean isBanOnZero() {
        return config.getBoolean("lives.ban-on-zero", true);
    }

    public String getBanMessage() {
        return config.getString("lives.ban-message",
                "&cVous n'avez plus de vies ! Achetez-en une ou attendez qu'un ami vous en donne une.");
    }

    /**
     * Durée (secondes) de l'effet Saturation appliqué au respawn.
     * Défaut 150 (~2,5 min). Plancher 1 s pour éviter une durée nulle/négative.
     */
    public int getRespawnSaturationSeconds() {
        return Math.max(1, config.getInt("lives.respawn-saturation-seconds", 150));
    }

    /**
     * Durée du tag combat (secondes). Défaut 20.
     * Clé absente du config live → 20. Une ancienne valeur déjà écrite sur disque gagne.
     */
    public int getCombatTagSeconds() {
        return Math.max(1, config.getInt("combat-tag-seconds", 20));
    }

    public double getStartBalance() {
        return config.getDouble("economy.start-balance", 10000.0);
    }

    public int getTeleportDelay() {
        return config.getInt("teleport.delay", 5);
    }

    public int getMaxHomes() {
        return config.getInt("teleport.max-homes", 5);
    }

    public int getMinMembersCreate() {
        return config.getInt("factions.min-members-create", 8);
    }

    public int getStartChunks() {
        return config.getInt("factions.start-chunks", 60);
    }

    public int getStartHomes() {
        return config.getInt("factions.start-homes", 30);
    }

    public double getPowerBuyBase() {
        return config.getDouble("factions.power-buy.base", 5000.0);
    }

    public double getPowerBuyGrowth() {
        return Math.max(1.01, config.getDouble("factions.power-buy.growth", 1.18));
    }

    public int getPowerBuyMaxPerPurchase() {
        return Math.max(1, config.getInt("factions.power-buy.max-per-purchase", 50));
    }

    /** Pouvoir octroyé par unité achetée (prix inchangé ; défaut 10). */
    public int getPowerBuyPowerPerUnit() {
        return Math.max(1, config.getInt("factions.power-buy.power-per-unit", 10));
    }

    public boolean isHopperLimitEnabled() {
        return config.getBoolean("factions.hopper-limit.enabled", true);
    }

    /** Diviseur pouvoir → max hoppers (défaut 20). */
    public int getHopperLimitDivisor() {
        return Math.max(1, config.getInt("factions.hopper-limit.divisor", 20));
    }

    /** Plancher du plafond hoppers (défaut 0). */
    public int getHopperLimitMin() {
        return Math.max(0, config.getInt("factions.hopper-limit.min", 0));
    }

    /** max hoppers posables = max(min, pouvoir / divisor). */
    public int getMaxHoppersForPower(int power) {
        return Math.max(getHopperLimitMin(), Math.max(0, power) / getHopperLimitDivisor());
    }

    public String getMessagePrefix() {
        return config.getString("messages.prefix", "&8[&6Cantale&8] &r");
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "");
    }

    public String getResourcePackUrl() {
        return config.getString("resource-pack.url", "");
    }

    public String getResourcePackHash() {
        return config.getString("resource-pack.sha1", "");
    }

    /**
     * Si true, le plugin renvoie le pack au join via {@code Player#setResourcePack}.
     * Doit rester false quand le pack est déjà défini dans {@code server.properties}
     * (sinon double envoi → reloads en boucle + kick avec require-resource-pack).
     */
    public boolean isResourcePackSendOnJoin() {
        return config.getBoolean("resource-pack.send-on-join", false);
    }

    public boolean isMultiCantoolVisualAdapterEnabled() {
        return config.getBoolean("custom-items.multi-cantool-visual-adapter", true);
    }

    /**
     * Retourne l'ensemble des matériaux considérés comme "stockage" dans les claims.
     * Ces blocs ne peuvent pas être cassés par les étrangers (sauf par explosion).
     */
    public Set<Material> getClaimProtectedStorageBlocks() {
        return loadMaterialSet("factions.claim-protection.storage-blocks");
    }

    /**
     * Blocs / items de raid que les étrangers peuvent poser dans un claim.
     * Union YAML + défauts Java (saveDefaultConfig ne fusionne pas le live).
     * Les lits sont toujours exclus : le wither est le seul moyen de casser l'obsidienne.
     */
    public Set<Material> getClaimExplosivePlaceBlocks() {
        Set<Material> materials = new HashSet<>(loadMaterialSet("factions.claim-protection.explosive-place-blocks"));
        materials.addAll(defaultRaidPlaceBlocks());
        materials.removeIf(material -> material != null && material.name().endsWith("_BED"));
        return materials;
    }

    /** TNT, cristaux, ancres, lodestone, seaux, wither — toujours autorisés même si le YAML live est incomplet. Pas de lits. */
    private static Set<Material> defaultRaidPlaceBlocks() {
        Set<Material> defaults = new HashSet<>();
        defaults.add(Material.TNT);
        defaults.add(Material.OBSIDIAN);
        defaults.add(Material.END_CRYSTAL);
        defaults.add(Material.RESPAWN_ANCHOR);
        defaults.add(Material.LODESTONE);
        defaults.add(Material.GLOWSTONE);
        defaults.add(Material.WATER_BUCKET);
        defaults.add(Material.LAVA_BUCKET);
        defaults.add(Material.TNT_MINECART);
        defaults.add(Material.SOUL_SAND);
        defaults.add(Material.SOUL_SOIL);
        defaults.add(Material.WITHER_SKELETON_SKULL);
        defaults.add(Material.WITHER_SKELETON_WALL_SKULL);
        return defaults;
    }

    private Set<Material> loadMaterialSet(String path) {
        List<String> names = config.getStringList(path);
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Material> materials = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            Material mat = Material.matchMaterial(name.trim().toUpperCase());
            if (mat != null) {
                materials.add(mat);
            } else {
                plugin.getLogger().warning("[Config] Matériau inconnu dans " + path + " : " + name);
            }
        }
        return materials;
    }

    // ── PASDIC ──
    public double getPasdicCost() {
        return Math.max(0, config.getDouble("factions.pasdic.cost", 50000.0));
    }

    public Set<Material> getPasdicBannedContainers() {
        return loadMaterialSet("factions.pasdic.banned-container-blocks");
    }

    /**
     * Types de spawn autorisés dans un PASDIC. Clé absente (config live pas mergée)
     * → défauts Java [SPAWNER, CUSTOM] : les spawners Cantale spawnent via
     * {@code spawnEntity} (reason CUSTOM), le spawn vanilla du bloc étant neutralisé.
     * Liste présente mais vide / invalide → ensemble vide = limitation désactivée.
     */
    public Set<CreatureSpawnEvent.SpawnReason> getPasdicAllowedSpawnReasons() {
        String path = "factions.pasdic.allowed-spawn-reasons";
        if (!config.isSet(path)) {
            return EnumSet.of(CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String raw : config.getStringList(path)) {
            if (raw == null || raw.isBlank()) continue;
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Config] SpawnReason inconnue dans " + path + " : " + raw);
            }
        }
        return reasons;
    }

    // ── Secret ──
    public double getSecretDailyCost() {
        return Math.max(0, config.getDouble("factions.secret.daily-cost", 1000.0));
    }

    public int getSecretChargeIntervalMinutes() {
        return Math.max(1, config.getInt("factions.secret.charge-interval-minutes", 20));
    }

    public int getSecretFreeDaysNewFaction() {
        return Math.max(0, config.getInt("factions.secret.free-days-new-faction", 3));
    }

    public int getSecretFreeDaysAfterRaid() {
        return Math.max(0, config.getInt("factions.secret.free-days-after-raid", 3));
    }

    public int getSecretWarningMinutesBefore() {
        return Math.max(0, config.getInt("factions.secret.warning-minutes-before", 60));
    }

    // ── Raid ──
    public int getRaidDurationMinutesSmall() {
        return Math.max(1, config.getInt("factions.raid.duration-minutes-small", 15));
    }

    public int getRaidDurationMinutesLarge() {
        return Math.max(1, config.getInt("factions.raid.duration-minutes-large", 60));
    }

    public int getRaidPowerSmall() {
        return Math.max(0, config.getInt("factions.raid.power-small", 100));
    }

    public int getRaidPowerLarge() {
        return Math.max(getRaidPowerSmall(), config.getInt("factions.raid.power-large", 500));
    }

    public int getRaidCooldownMinutesSmall() {
        return Math.max(0, config.getInt("factions.raid.cooldown-minutes-small", 1440));
    }

    public int getRaidCooldownMinutesLarge() {
        return Math.max(0, config.getInt("factions.raid.cooldown-minutes-large", 30));
    }

    public int getRaidMaxConcurrentSmall() {
        return Math.max(1, config.getInt("factions.raid.max-concurrent-small", 1));
    }

    public int getRaidMaxConcurrentLarge() {
        return Math.max(getRaidMaxConcurrentSmall(), config.getInt("factions.raid.max-concurrent-large", 3));
    }

    public int getRaidProtectionHoursSmall() {
        return Math.max(0, config.getInt("factions.raid.protection-hours-small", 72));
    }

    public int getRaidProtectionHoursLarge() {
        return Math.max(0, config.getInt("factions.raid.protection-hours-large", 2));
    }

    // ── Guerre (déclaration mutuelle) ──

    /** Minutes avant expiration d'une déclaration de guerre ou d'une proposition de paix sans réponse. */
    public int getWarDeclarationExpiryMinutes() {
        return Math.max(1, config.getInt("factions.war.declaration-expiry-minutes", 15));
    }

    /** Multiplicateur de perte de pouvoir à la mort pour un membre d'une faction en guerre. */
    public double getWarDeathPowerMultiplier() {
        return Math.max(0, config.getDouble("factions.war.death-power-multiplier", 2.0));
    }

    /** Multiplicateur de gain de pouvoir pour un kill sur un membre de la faction ennemie (en guerre). */
    public double getWarKillPowerMultiplier() {
        return Math.max(0, config.getDouble("factions.war.kill-power-multiplier", 2.0));
    }

    /**
     * false (défaut) : TOUTE mort d'un membre en guerre fait perdre le pouvoir amplifié.
     * true : uniquement les morts PvP contre le belligérant.
     */
    public boolean isWarDeathMultiplierPvpOnly() {
        return config.getBoolean("factions.war.death-multiplier-pvp-only", false);
    }

    /** Réparations Cantox (banque → banque) payées par la faction qui capitule. 0 = désactivé. */
    public double getWarSurrenderReparationsCantox() {
        return Math.max(0, config.getDouble("factions.war.surrender-reparations-cantox", 10000.0));
    }
}
