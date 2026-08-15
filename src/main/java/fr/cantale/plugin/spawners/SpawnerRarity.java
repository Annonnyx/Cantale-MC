package fr.cantale.plugin.spawners;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum SpawnerRarity {
    RARE("Rare", NamedTextColor.AQUA),
    EPIC("Épique", NamedTextColor.LIGHT_PURPLE),
    MYTHIC("Mythique", NamedTextColor.GOLD);

    private final String frenchName;
    private final TextColor color;

    SpawnerRarity(String frenchName, TextColor color) {
        this.frenchName = frenchName;
        this.color = color;
    }

    public String frenchName() {
        return frenchName;
    }

    public TextColor color() {
        return color;
    }
}
