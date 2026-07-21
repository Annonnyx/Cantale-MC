package fr.cantale.plugin.privatechest;

public enum ChestType {
    PC1("pc1", "§6Coffre Privé §7#1"),
    PC2("pc2", "§6Coffre Privé §7#2"),
    PC3("pc3", "§6Coffre Privé §7#3"),
    EC("ec", "§5Ender Chest");

    private final String key;
    private final String title;

    ChestType(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public static ChestType fromKey(String key) {
        if (key == null) return null;
        for (ChestType type : values()) {
            if (type.key.equalsIgnoreCase(key)) return type;
        }
        return null;
    }
}
