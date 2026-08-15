package fr.cantale.plugin.ui.map;

import java.util.UUID;

/**
 * État d'une carte du monde ouverte par un joueur (centre, zoom, couches, tuiles rendues).
 * Public pour {@code WorldMapUI} (package ui.inventory).
 */
public final class MapSession {

    public static final int[] ZOOM_LEVELS = {1, 2, 4, 8, 16};
    public static final int TILE_SIZE = 128;

    public final UUID playerId;
    public final String world;
    public int centerX;
    public int centerZ;
    public int zoomIndex;
    public boolean showClaims = true;
    public boolean showMarkers = true;
    public boolean showTerrain = true;

    /** Toutes les tuiles doivent être reconstruites + renvoyées. */
    public boolean dirtyAll = true;
    public final byte[][] tiles;
    public final boolean[] tileDirty;
    public int cursorChunkX = Integer.MIN_VALUE;
    public int cursorChunkZ = Integer.MIN_VALUE;
    public long claimsChecksum = -1;

    public MapSession(UUID playerId, String world, int centerX, int centerZ, int zoomIndex, int tileCount) {
        this.playerId = playerId;
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.zoomIndex = zoomIndex;
        this.tiles = new byte[tileCount][TILE_SIZE * TILE_SIZE];
        this.tileDirty = new boolean[tileCount];
        markAllDirty();
    }

    public void markAllDirty() {
        dirtyAll = true;
        for (int i = 0; i < tileDirty.length; i++) {
            tileDirty[i] = true;
        }
    }

    public int zoom() {
        return ZOOM_LEVELS[Math.max(0, Math.min(zoomIndex, ZOOM_LEVELS.length - 1))];
    }

    /** Blocs couverts par une tuile (128 px × zoom). */
    public int tileSpan() {
        return TILE_SIZE * zoom();
    }

    /** Coordonnée bloc du coin haut-gauche de la tuile. */
    public int tileOriginX(int tileCol, int gridCols) {
        return centerX + (tileCol - gridCols / 2) * tileSpan();
    }

    public int tileOriginZ(int tileRow, int gridRows) {
        return centerZ + (tileRow - gridRows / 2) * tileSpan();
    }

    public void zoomIn() {
        if (zoomIndex > 0) {
            zoomIndex--;
            markAllDirty();
        }
    }

    public void zoomOut() {
        if (zoomIndex < ZOOM_LEVELS.length - 1) {
            zoomIndex++;
            markAllDirty();
        }
    }
}
