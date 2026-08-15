package fr.cantale.plugin.anticheat.check;

/**
 * Enum des types de vérifications anti-cheat.
 * Chaque type est associé à une catégorie pour le regroupement et la configuration.
 */
public enum CheckType {

    // ── Mouvement ──
    SPEED("Speed", Category.MOVEMENT),
    FLY("Fly", Category.MOVEMENT),
    NOFALL("NoFall", Category.MOVEMENT),
    STEP("Step", Category.MOVEMENT),
    TIMER("Timer", Category.MOVEMENT),

    // ── Combat ──
    REACH("Reach", Category.COMBAT),
    KILLAURA("KillAura", Category.COMBAT),
    AUTOCLICKER("AutoClicker", Category.COMBAT),
    AIM("Aim", Category.COMBAT),

    // ── Rotation ──
    ROTATION("Rotation", Category.ROTATION),

    // ── Inventaire ──
    AUTO_ARMOR("AutoArmor", Category.INVENTORY),
    CHEST_STEALER("ChestStealer", Category.INVENTORY),
    FAST_USE("FastUse", Category.INVENTORY),

    // ── Monde / casse de blocs ──
    FAST_BREAK("FastBreak", Category.WORLD),

    // ── Packets ──
    BLINK("Blink", Category.PACKET),
    BAD_PACKETS("BadPackets", Category.PACKET),

    // ── Externe (Grim AC) ──
    GRIM("Grim", Category.EXTERNAL);

    private final String displayName;
    private final Category category;

    CheckType(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    /**
     * Catégories de regroupement des checks.
     */
    public enum Category {
        MOVEMENT("Mouvement"),
        COMBAT("Combat"),
        ROTATION("Rotation"),
        INVENTORY("Inventaire"),
        WORLD("Monde"),
        PACKET("Packets"),
        EXTERNAL("Externe (Grim)");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
