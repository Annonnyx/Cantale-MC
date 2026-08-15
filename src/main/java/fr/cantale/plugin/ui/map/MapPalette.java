package fr.cantale.plugin.ui.map;

import java.awt.Color;

/**
 * Conversion {@link Color} → byte palette Minecraft pour {@code MapCanvas.setPixel}.
 * Délègue à {@link org.bukkit.map.MapPalette#matchColor(Color)}.
 */
@SuppressWarnings("deprecation")
public final class MapPalette {

    public static final byte TRANSPARENT = 0;

    private MapPalette() {
    }

    public static byte match(Color color) {
        return org.bukkit.map.MapPalette.matchColor(color);
    }

    public static byte match(int rgb) {
        return org.bukkit.map.MapPalette.matchColor(new Color(rgb));
    }
}
