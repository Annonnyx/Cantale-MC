package fr.cantale.plugin.events;

public enum FactionEventType {
    HARVEST("Récolte", "Dépose tes récoltes avec /recolte"),
    MINING("Minage", "Mine des minerais pour ta faction"),
    PVP("PVP", "Élimine des joueurs ennemis pour ta faction");

    private final String displayName;
    private final String description;

    FactionEventType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
