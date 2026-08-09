package fr.cantale.plugin.rank;

public enum PlayerRank {
    //                       display       dailyC  stL  pc  pc2 mL  homes ah
    NONE("Aucun",           0,      0, 27,  0,  0,  3,  3),
    AVENTURIER("Aventurier",2500,   0, 27,  0,  1,  5,  5),
    VIP("VIP",              10000,  1, 54,  0,  2, 10, 12),
    CHEVRE("Chèvre",        100000, 1, 54, 54,  3,999, 20),
    MODERATOR("Modo", 100000, 1, 54, 54,  3,999, 20),
    ADMIN("Admin",          100000, 1, 54, 54,  3,999, 20),
    OWNER("Owner",          100000, 1, 54, 54,  3,999, 20);

    private final String displayName;
    private final int dailyCantox;
    private final int monthlyStatioLytras;
    private final int pcSlots;
    private final int pc2Slots;          // Second private chest slots (0 = none)
    private final int monthlyLives;      // VIE items given per month
    private final int maxHomes;
    private final int maxAhListings;

    PlayerRank(String displayName, int dailyCantox, int monthlyStatioLytras, int pcSlots, int pc2Slots, int monthlyLives, int maxHomes, int maxAhListings) {
        this.displayName = displayName;
        this.dailyCantox = dailyCantox;
        this.monthlyStatioLytras = monthlyStatioLytras;
        this.pcSlots = pcSlots;
        this.pc2Slots = pc2Slots;
        this.monthlyLives = monthlyLives;
        this.maxHomes = maxHomes;
        this.maxAhListings = maxAhListings;
    }

    public String getDisplayName() { return displayName; }
    public int getDailyCantox() { return dailyCantox; }
    public int getMonthlyStatioLytras() { return monthlyStatioLytras; }
    public int getPcSlots() { return pcSlots; }
    public int getPc2Slots() { return pc2Slots; }
    public int getMonthlyLives() { return monthlyLives; }
    public int getMaxHomes() { return maxHomes; }
    public int getMaxAhListings() { return maxAhListings; }

    public boolean hasNoTpCooldown() { return this == CHEVRE || this == MODERATOR || this == ADMIN || this == OWNER; }
    public boolean hasUnlimitedFlight() { return this == CHEVRE || this == MODERATOR || this == ADMIN || this == OWNER; }
    public boolean hasSecondChest() { return this == CHEVRE || this == MODERATOR || this == ADMIN || this == OWNER; }
    public boolean isAtLeastVIP() { return this == VIP || this == CHEVRE || this == MODERATOR || this == ADMIN || this == OWNER; }
    public boolean isAtLeastAventurier() { return this != NONE; }
    public boolean hasMonthlyLives() { return this.monthlyLives > 0; }
    public boolean isStaff() { return this == MODERATOR || this == ADMIN || this == OWNER; }

    public static PlayerRank fromString(String name) {
        if (name == null) return NONE;
        return switch (name.toUpperCase()) {
            case "AVENTURIER", "ADVENTURER" -> AVENTURIER;
            case "VIP" -> VIP;
            case "CHEVRE", "GOAT", "HEROS", "HERO" -> CHEVRE;
            case "MODERATOR", "MODERATEUR", "MOD", "MODO" -> MODERATOR;
            case "ADMIN", "ADMINISTRATEUR" -> ADMIN;
            case "OWNER", "FONDATEUR", "FONDA" -> OWNER;
            case "PLAYER", "JOUEUR", "NONE" -> NONE;
            default -> NONE;
        };
    }
}
