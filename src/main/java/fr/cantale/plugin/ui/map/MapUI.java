package fr.cantale.plugin.ui.map;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MapUI {

    private final CantalePlugin plugin;
    private final Map<UUID, Integer> activeMaps;

    public MapUI(CantalePlugin plugin) {
        this.plugin = plugin;
        this.activeMaps = new HashMap<>();
    }

    public void openClaimMap(Player player) {
        MapView mapView = Bukkit.createMap(player.getWorld());
        mapView.getRenderers().clear();
        mapView.addRenderer(new ClaimMapRenderer(plugin, player));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(mapView);
            mapItem.setItemMeta(meta);
        }

        activeMaps.put(player.getUniqueId(), mapView.getId());
        player.getInventory().addItem(mapItem);
        player.sendMessage("§8[§6Cantale§8] §7Carte des claims ajoutée à votre inventaire !");
    }

    public void cleanup(Player player) {
        activeMaps.remove(player.getUniqueId());
    }

    public static class ClaimMapRenderer extends MapRenderer {

        private final CantalePlugin plugin;
        private final Player player;
        private boolean rendered = false;

        public ClaimMapRenderer(CantalePlugin plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player p) {
            if (rendered) return;
            rendered = true;

            // Draw background
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixel(x, y, MapPalette.TRANSPARENT);
                }
            }

            // Draw title
            drawString(canvas, "CLAIMS MAP", 30, 5, MapPalette.DARK_BROWN);

            // Draw player position marker
            int centerX = 64;
            int centerY = 64;
            canvas.setPixel(centerX, centerY, MapPalette.RED);
            canvas.setPixel(centerX - 1, centerY, MapPalette.RED);
            canvas.setPixel(centerX + 1, centerY, MapPalette.RED);
            canvas.setPixel(centerX, centerY - 1, MapPalette.RED);
            canvas.setPixel(centerX, centerY + 1, MapPalette.RED);

            // Draw grid (chunks)
            for (int x = 10; x < 118; x += 10) {
                for (int y = 20; y < 118; y++) {
                    canvas.setPixel(x, y, MapPalette.GRAY);
                }
            }
            for (int y = 20; y < 118; y += 10) {
                for (int x = 10; x < 118; x++) {
                    canvas.setPixel(x, y, MapPalette.GRAY);
                }
            }

            // TODO: Color claimed chunks based on faction ownership
            drawString(canvas, "Chunk actuel", 5, 120, MapPalette.DARK_GREEN);
        }

        private void drawString(MapCanvas canvas, String text, int x, int y, byte color) {
            // Simple text rendering using Minecraft map font
            // For now, draw individual pixels as letters would require a font map
            // This is a placeholder - real implementation needs a font texture
        }
    }
}
