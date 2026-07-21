package fr.cantale.plugin.crate;

public enum CrateType {
    VOTE("Cadeau du Roi", "§7Cadeau du Roi", "§7Un présent royal à déballer..."),
    RARE("Trésor Public", "§9Trésor Public", "§9Une clef mystérieuse ouvrant un coffre ancien."),
    EPIC("Médaille Épique", "§5Médaille du Tournoi", "§5Trophée glorieux à échanger contre une récompense."),
    MYTHIC("Pièce Mythique", "§dPièce Mythique", "§dUne pièce légendaire à lancer dans la fontaine."),
    LEGENDARY("Ticket Légendaire", "§6Ticket Légendaire", "§6Contacte le staff sur Discord pour recevoir l'item de ton choix.");

    private final String displayName;
    private final String itemName;
    private final String defaultLore;

    CrateType(String displayName, String itemName, String defaultLore) {
        this.displayName = displayName;
        this.itemName = itemName;
        this.defaultLore = defaultLore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDefaultLore() {
        return defaultLore;
    }
}
