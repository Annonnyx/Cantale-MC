package fr.cantale.plugin.ui.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renderer contextuel : une seule {@link MapView} partagée, contenu différent par joueur.
 *
 * <p>Mode {@code TILE} : une tuile de la grille du GUI carte (contenu = session du viewer).
 * Mode {@code MINIMAP} : carte tenue en main, centrée sur le joueur (zoom 2).
 */
@SuppressWarnings("deprecation")
public final class WorldMapRenderer extends MapRenderer {

    static final int MINIMAP_TILE = -1;
    /** Blocs par pixel de la minimap. */
    static final int MINIMAP_ZOOM = 2;

    private final MapUI service;
    private final int tileIndex;
    private final boolean minimap;

    /** Dernière version blitée par joueur (évite de repeindre 16K px sans changement). */
    private final Map<Player, Integer> blittedVersion = new WeakHashMap<>();
    private int contentVersion = 0;

    public WorldMapRenderer(MapUI service, int tileIndex) {
        super(true);
        this.service = service;
        this.tileIndex = tileIndex;
        this.minimap = tileIndex == MINIMAP_TILE;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (minimap) {
            renderMinimap(canvas, player);
            return;
        }
        MapSession session = service.getSession(player.getUniqueId());
        if (session == null || tileIndex < 0 || tileIndex >= session.tiles.length) {
            if (blittedVersion.get(player) == null || blittedVersion.get(player) != -1) {
                fill(canvas, (byte) 17); // noir
                blittedVersion.put(player, -1);
            }
            return;
        }
        if (!session.tileDirty[tileIndex]
                && Integer.valueOf(contentVersion).equals(blittedVersion.get(player))) {
            return;
        }
        if (session.tileDirty[tileIndex]) {
            service.renderTile(session, player, tileIndex);
            session.tileDirty[tileIndex] = false;
        }
        blit(canvas, session.tiles[tileIndex]);
        blittedVersion.put(player, contentVersion);
    }

    private void renderMinimap(MapCanvas canvas, Player player) {
        byte[] data = service.minimapBuffer(player);
        if (data == null) {
            fill(canvas, (byte) 17);
            blittedVersion.put(player, -1);
            return;
        }
        int version = service.minimapVersion(player);
        if (Integer.valueOf(version).equals(blittedVersion.get(player))) {
            return;
        }
        blit(canvas, data);
        blittedVersion.put(player, version);
    }

    /** Invalide le cache de blit (appelé quand le contenu peut changer). */
    void bumpVersion() {
        contentVersion++;
    }

    private void blit(MapCanvas canvas, byte[] data) {
        for (int y = 0; y < MapSession.TILE_SIZE; y++) {
            int row = y * MapSession.TILE_SIZE;
            for (int x = 0; x < MapSession.TILE_SIZE; x++) {
                canvas.setPixel(x, y, data[row + x]);
            }
        }
    }

    private void fill(MapCanvas canvas, byte color) {
        for (int y = 0; y < MapSession.TILE_SIZE; y++) {
            for (int x = 0; x < MapSession.TILE_SIZE; x++) {
                canvas.setPixel(x, y, color);
            }
        }
    }
}
