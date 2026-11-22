package fr.cantale.plugin.rank;

public enum PlayerRank {
    // Bonus /daily ajouté aux 1 000 Cantox de base (DailyCommand).
    // Départ 10 000, vie 10 000, pouvoir 5 000, PASDIC 50 000, taxe secret 1 000 / 20 min.
    // Totaux /jour : aucun 1 000, Aventurier 1 500, VIP 2 500, Chèvre 3 500.
    // L'ancien bonus Chèvre de 100 000 permettait de vider le shop en une journée.
    //                       display       dailyC  stL  pc  pc2 mL  homes ah
    NONE("Aucun",           0,      0, 27,  0,  0,  5,  3),
    AVENTURIER("Aventurier",500,    0, 27,  0,  1,  8,  5),
    VIP("VIP",              1500,   1, 54,  0,  2, 10, 12),
    CHEVRE("Chèvre",        2500,   1, 54, 54,  3,999, 20),
    // Staff : pas de bonus /daily ni vies mensuelles (même base 1000 Cantox que les joueurs).
    // Modo : AUCUN avantage de grade payant — PC/homes/AH/feed identiques à NONE,
    // outils de modération uniquement. Seuls Admin/Owner gardent les avantages staff.
    MODERATOR("Modo",       0,      0, 27,  0,  0,  5,  3),
    ADMIN("Admin",          0,      0, 54, 54,  0,999, 20),
    OWNER("Owner",          0,      0, 54, 54,  0,999, 20);

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

    public boolean hasNoTpCooldown() { return this == CHEVRE || this == ADMIN || this == OWNER; }
    /**
     * @deprecated Avantage payant retiré (EULA Mojang). Toujours {@code false} :
     * aucun grade n'accorde le vol créatif dans un claim.
     */
    @Deprecated
    public boolean hasUnlimitedFlight() { return false; }
    /**
     * @deprecated Avantage payant retiré (EULA Mojang). Toujours {@code false} :
     * aucun grade ne peut fouiller les conteneurs d'un claim étranger.
     * Les membres de la faction propriétaire gardent l'accès à leurs propres conteneurs.
     */
    @Deprecated
    public boolean canStealInClaims() { return false; }
    public boolean hasSecondChest() { return this == CHEVRE || this == ADMIN || this == OWNER; }
    /** Avantages VIP (/feed, etc.) : grades payants VIP/Chèvre + Admin/Owner — PAS les modos. */
    public boolean isAtLeastVIP() { return this == VIP || this == CHEVRE || this == ADMIN || this == OWNER; }
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
