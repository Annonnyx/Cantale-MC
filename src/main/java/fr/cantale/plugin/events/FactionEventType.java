package fr.cantale.plugin.events;

import org.bukkit.Material;

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

    /** Titre joueur : « Récolte de blé », « Minage de diamant », ou « PVP ». */
    public String getTargetedDisplayName(Material target) {
        if (this == PVP || target == null) {
            return displayName;
        }
        String label = EventMaterialLabels.french(target);
        return this == HARVEST ? "Récolte de " + label : "Minage de " + label;
    }

    public String getTargetedDescription(Material target) {
        if (target == null) {
            return description;
        }
        String label = EventMaterialLabels.french(target);
        return switch (this) {
            case HARVEST -> "Dépose du " + label + " avec /recolte";
            case MINING -> "Mine du " + label + " pour ta faction";
            case PVP -> description;
        };
    }
}
