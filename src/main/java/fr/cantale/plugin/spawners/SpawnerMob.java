package fr.cantale.plugin.spawners;

import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Mobs de spawners Cantale (14 obtenables). Pas de chevaux, loups, ni bosses.
 * {@link #GHAST} est conservé pour la désérialisation BDD / items existants :
 * il n'est plus rollable ni donnable ; chargé → migré en {@link #MAGMA_CUBE}.
 */
public enum SpawnerMob {
    COW(EntityType.COW, SpawnerRarity.RARE, "Vache", false),
    PIG(EntityType.PIG, SpawnerRarity.RARE, "Cochon", false),
    SHEEP(EntityType.SHEEP, SpawnerRarity.RARE, "Mouton", false),
    CHICKEN(EntityType.CHICKEN, SpawnerRarity.RARE, "Poulet", false),
    ZOMBIE(EntityType.ZOMBIE, SpawnerRarity.RARE, "Zombie", false),
    SKELETON(EntityType.SKELETON, SpawnerRarity.RARE, "Squelette", false),
    SPIDER(EntityType.SPIDER, SpawnerRarity.RARE, "Araignée", false),
    CREEPER(EntityType.CREEPER, SpawnerRarity.RARE, "Creeper", false),
    ENDERMAN(EntityType.ENDERMAN, SpawnerRarity.EPIC, "Enderman", false),
    BLAZE(EntityType.BLAZE, SpawnerRarity.EPIC, "Blaze", false),
    WITCH(EntityType.WITCH, SpawnerRarity.EPIC, "Sorcière", false),
    SLIME(EntityType.SLIME, SpawnerRarity.EPIC, "Slime", false),
    GHAST(EntityType.GHAST, SpawnerRarity.MYTHIC, "Ghast", false, false),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, SpawnerRarity.MYTHIC, "Cube de magma", false),
    IRON_GOLEM(EntityType.IRON_GOLEM, SpawnerRarity.MYTHIC, "Golem de fer", true);

    private final EntityType entityType;
    private final SpawnerRarity rarity;
    private final String frenchName;
    private final boolean slowSpawn;
    private final boolean obtainable;

    SpawnerMob(EntityType entityType, SpawnerRarity rarity, String frenchName, boolean slowSpawn) {
        this(entityType, rarity, frenchName, slowSpawn, true);
    }

    SpawnerMob(EntityType entityType, SpawnerRarity rarity, String frenchName, boolean slowSpawn, boolean obtainable) {
        this.entityType = entityType;
        this.rarity = rarity;
        this.frenchName = frenchName;
        this.slowSpawn = slowSpawn;
        this.obtainable = obtainable;
    }

    public EntityType entityType() {
        return entityType;
    }

    public SpawnerRarity rarity() {
        return rarity;
    }

    public String frenchName() {
        return frenchName;
    }

    /** Intervalle ×2 (ex. golem de fer). */
    public boolean slowSpawn() {
        return slowSpawn;
    }

    /** Rollable / donnable / posable. {@code false} pour les types retirés encore présents en BDD. */
    public boolean obtainable() {
        return obtainable;
    }

    /**
     * Type posé / spawné. Les mobs retirés (Ghast) deviennent cube de magma (même rareté mythique).
     */
    public SpawnerMob canonicalMob() {
        return this == GHAST ? MAGMA_CUBE : this;
    }

    public String itemDisplayName(int tier) {
        String base = "Spawner à " + frenchName;
        return tier > 1 ? base + " x" + tier : base;
    }

    public String hologramLabel(int tier) {
        return "Spawner à " + frenchName + " x" + Math.max(1, tier);
    }

    public static Optional<SpawnerMob> fromName(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Optional.of(valueOf(key));
        } catch (IllegalArgumentException ignored) {
            for (SpawnerMob mob : values()) {
                if (mob.frenchName.equalsIgnoreCase(raw.trim())
                        || mob.entityType.name().equals(key)) {
                    return Optional.of(mob);
                }
            }
            return Optional.empty();
        }
    }

    public static List<SpawnerMob> ofRarity(SpawnerRarity rarity) {
        return Arrays.stream(values()).filter(m -> m.rarity == rarity && m.obtainable).toList();
    }

    public static List<SpawnerMob> obtainableValues() {
        return Arrays.stream(values()).filter(m -> m.obtainable).toList();
    }
}
