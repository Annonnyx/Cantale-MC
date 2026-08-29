package fr.cantale.plugin.spawners;

import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Mobs de spawners Cantale. Pas de chevaux, loups, villageois, ni bosses
 * (Wither, Dragon, Warden). {@link #GHAST} et {@link #SHULKER} sont conservés
 * pour la désérialisation BDD / items existants : plus rollables ni donnables
 * (Ghast trop volumineux / lag ; Shulker casserait le sink boutique des coquilles).
 * Chargés → migrés en {@link #MAGMA_CUBE}.
 */
public enum SpawnerMob {
    // ── Rare : fermes de base et hostiles communs ──
    COW(EntityType.COW, SpawnerRarity.RARE, "Vache", false),
    PIG(EntityType.PIG, SpawnerRarity.RARE, "Cochon", false),
    SHEEP(EntityType.SHEEP, SpawnerRarity.RARE, "Mouton", false),
    CHICKEN(EntityType.CHICKEN, SpawnerRarity.RARE, "Poulet", false),
    RABBIT(EntityType.RABBIT, SpawnerRarity.RARE, "Lapin", false),
    MOOSHROOM(EntityType.MOOSHROOM, SpawnerRarity.RARE, "Champimeuh", false),
    SQUID(EntityType.SQUID, SpawnerRarity.RARE, "Poulpe", false),
    BEE(EntityType.BEE, SpawnerRarity.RARE, "Abeille", false),
    ZOMBIE(EntityType.ZOMBIE, SpawnerRarity.RARE, "Zombie", false),
    SKELETON(EntityType.SKELETON, SpawnerRarity.RARE, "Squelette", false),
    SPIDER(EntityType.SPIDER, SpawnerRarity.RARE, "Araignée", false),
    CREEPER(EntityType.CREEPER, SpawnerRarity.RARE, "Creeper", false),
    HUSK(EntityType.HUSK, SpawnerRarity.RARE, "Husk", false),
    DROWNED(EntityType.DROWNED, SpawnerRarity.RARE, "Noyé", false),
    STRAY(EntityType.STRAY, SpawnerRarity.RARE, "Vagabond", false),
    CAVE_SPIDER(EntityType.CAVE_SPIDER, SpawnerRarity.RARE, "Araignée venimeuse", false),
    BOGGED(EntityType.BOGGED, SpawnerRarity.RARE, "Envasé", false),
    SILVERFISH(EntityType.SILVERFISH, SpawnerRarity.RARE, "Poisson d'argent", false),
    ZOMBIE_VILLAGER(EntityType.ZOMBIE_VILLAGER, SpawnerRarity.RARE, "Zombie-villageois", false),

    // ── Épique : meilleurs drops / nether / océan ──
    ENDERMAN(EntityType.ENDERMAN, SpawnerRarity.EPIC, "Enderman", false),
    BLAZE(EntityType.BLAZE, SpawnerRarity.EPIC, "Blaze", false),
    WITCH(EntityType.WITCH, SpawnerRarity.EPIC, "Sorcière", false),
    SLIME(EntityType.SLIME, SpawnerRarity.EPIC, "Slime", false),
    GUARDIAN(EntityType.GUARDIAN, SpawnerRarity.EPIC, "Gardien", false),
    POLAR_BEAR(EntityType.POLAR_BEAR, SpawnerRarity.EPIC, "Ours polaire", false),
    PIGLIN(EntityType.PIGLIN, SpawnerRarity.EPIC, "Piglin", false),
    ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, SpawnerRarity.EPIC, "Piglin zombifié", false),
    HOGLIN(EntityType.HOGLIN, SpawnerRarity.EPIC, "Hoglin", false),
    PHANTOM(EntityType.PHANTOM, SpawnerRarity.EPIC, "Phantom", false),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, SpawnerRarity.EPIC, "Wither squelette", false),
    PILLAGER(EntityType.PILLAGER, SpawnerRarity.EPIC, "Pilleur", false),
    VINDICATOR(EntityType.VINDICATOR, SpawnerRarity.EPIC, "Vindicateur", false),
    STRIDER(EntityType.STRIDER, SpawnerRarity.EPIC, "Arpenteur", false),
    ENDERMITE(EntityType.ENDERMITE, SpawnerRarity.EPIC, "Endermite", false),
    BREEZE(EntityType.BREEZE, SpawnerRarity.EPIC, "Breeze", false),
    GLOW_SQUID(EntityType.GLOW_SQUID, SpawnerRarity.EPIC, "Poulpe lumineux", false),
    GOAT(EntityType.GOAT, SpawnerRarity.EPIC, "Chèvre", false),
    PANDA(EntityType.PANDA, SpawnerRarity.EPIC, "Panda", false),

    // ── Mythique : farms puissantes, cadence souvent ralentie ──
    MAGMA_CUBE(EntityType.MAGMA_CUBE, SpawnerRarity.MYTHIC, "Cube de magma", false),
    IRON_GOLEM(EntityType.IRON_GOLEM, SpawnerRarity.MYTHIC, "Golem de fer", true),
    ELDER_GUARDIAN(EntityType.ELDER_GUARDIAN, SpawnerRarity.MYTHIC, "Grand gardien", true),
    PIGLIN_BRUTE(EntityType.PIGLIN_BRUTE, SpawnerRarity.MYTHIC, "Piglin brute", true),
    ZOGLIN(EntityType.ZOGLIN, SpawnerRarity.MYTHIC, "Zoglin", false),
    RAVAGER(EntityType.RAVAGER, SpawnerRarity.MYTHIC, "Ravageur", true),
    EVOKER(EntityType.EVOKER, SpawnerRarity.MYTHIC, "Évocateur", true),

    // Conservés pour la BDD / items existants — plus obtenus.
    GHAST(EntityType.GHAST, SpawnerRarity.MYTHIC, "Ghast", false, false),
    SHULKER(EntityType.SHULKER, SpawnerRarity.MYTHIC, "Shulker", false, false);

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

    /** Intervalle ×2 (golem, ravageur, grand gardien, évocateurs, piglin brute). */
    public boolean slowSpawn() {
        return slowSpawn;
    }

    /** Hitbox haute : 3 blocs d'air au-dessus des pieds, sinon suffocation. */
    public boolean needsExtraHeadroom() {
        return this == IRON_GOLEM || this == GHAST || this == RAVAGER
                || this == POLAR_BEAR || this == HOGLIN || this == ZOGLIN
                || this == ELDER_GUARDIAN;
    }

    /** Rollable / donnable / posable. {@code false} pour les types retirés encore présents en BDD. */
    public boolean obtainable() {
        return obtainable;
    }

    /**
     * Type posé / spawné. Les mobs retirés (Ghast, Shulker) deviennent cube de magma.
     */
    public SpawnerMob canonicalMob() {
        return obtainable ? this : MAGMA_CUBE;
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
