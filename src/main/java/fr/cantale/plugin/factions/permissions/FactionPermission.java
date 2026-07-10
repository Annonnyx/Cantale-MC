package fr.cantale.plugin.factions.permissions;

public enum FactionPermission {
    ADD_CLAIMS("addclaims", true),
    REMOVE_CLAIMS("removeclaims", true),
    SET_PERMS("setperms", false),
    FACCEPT("faccept", false),
    FKICK("fkick", false),
    BAN("ban", false),
    UNBAN("unban", false),
    BANK_ADD("bankadd", false),
    BANK_TAKE("banktake", false),
    CULTURES("cultures", true),
    FHOME("fhome", 0),
    FWARP("fwarp", false),
    INFO_BANK("infobank", false),
    SECRET("secret", false);

    private final String name;
    private final Object defaultValue;

    FactionPermission(String name, Object defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return "Cantale." + name;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static FactionPermission fromString(String name) {
        String cleanName = name.startsWith("Cantale.") ? name.substring(8) : name;
        for (FactionPermission perm : values()) {
            if (perm.name.equalsIgnoreCase(cleanName)) {
                return perm;
            }
        }
        return null;
    }
}
