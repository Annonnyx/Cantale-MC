package fr.cantale.plugin.factions;

public enum FactionRank {
    RECRUIT("Recrue", 1),
    MEMBER("Membre", 2),
    VETERAN("Veteran", 3),
    OFFICER("Officier", 4),
    LEADER("Leader", 5);

    private final String displayName;
    private final int level;

    FactionRank(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(FactionRank other) {
        return this.level >= other.level;
    }

    public boolean isLessThan(FactionRank other) {
        return this.level < other.level;
    }

    public static FactionRank fromString(String name) {
        for (FactionRank rank : values()) {
            if (rank.name().equalsIgnoreCase(name)) {
                return rank;
            }
        }
        return RECRUIT;
    }
}
