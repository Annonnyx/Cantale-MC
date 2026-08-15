package fr.cantale.plugin.ui.map;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.teleport.Home;
import fr.cantale.plugin.teleport.Warp;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compose une zone 128×128 px (terrain + claims + marqueurs) en bytes palette Minecraft.
 * Utilisé par les tuiles du GUI carte et par la minimap tenue en main.
 */
@SuppressWarnings("deprecation")
final class MapImageBuilder {

    // Couleurs claims (relation avec le viewer)
    private static final int RGB_OWN = 0x3FD23F;
    private static final int RGB_ALLY = 0x3FB4D2;
    private static final int RGB_WAR = 0xD23F3F;
    private static final int RGB_OTHER = 0xD28A3F;
    private static final int RGB_PASDIC = 0xB04FD8;

    // Marqueurs
    private static final int RGB_WARP = 0x35E0D0;
    private static final int RGB_WARP_EVENT = 0xFFD24A;
    private static final int RGB_HOME = 0x7CFC3F;
    private static final int RGB_FSPAWN = 0xFF6E40;
    private static final int RGB_SELF = 0xFFFFFF;

    private static final int RGB_UNKNOWN = 0x24242C;
    private static final int RGB_GRID_BG = 0x2E2E38;
    private static final int RGB_GRID_LINE = 0x44444F;

    private static final int TILE = MapSession.TILE_SIZE;

    private MapImageBuilder() {
    }

    /**
     * @param originX/originZ coin haut-gauche de la zone (blocs monde)
     * @param zoom            blocs par pixel (1, 2, 4, 8, 16)
     * @param out             buffer 128×128 (bytes palette) à remplir
     */
    static void renderArea(CantalePlugin plugin, TerrainCache terrain, Player viewer, World world,
                           int originX, int originZ, int zoom,
                           boolean showTerrain, boolean showClaims, boolean showMarkers,
                           Map<Long, Integer> claimsOverlay, byte[] out) {
        int[] rgbBuf = new int[TILE * TILE];

        // 1) Terrain (ou grille de secours)
        for (int py = 0; py < TILE; py++) {
            int wz = originZ + py * zoom;
            for (int px = 0; px < TILE; px++) {
                int wx = originX + px * zoom;
                int rgb;
                if (showTerrain) {
                    Color c = terrain.colorAt(world, wx, wz);
                    rgb = c != null ? c.getRGB() : RGB_UNKNOWN;
                } else {
                    boolean gridLine = zoom <= 16 && ((wx & 15) < zoom || (wz & 15) < zoom);
                    rgb = gridLine ? RGB_GRID_LINE : RGB_GRID_BG;
                }
                rgbBuf[py * TILE + px] = rgb;
            }
        }

        // 2) Claims (semi-transparents pour laisser voir le terrain)
        if (showClaims && claimsOverlay != null && !claimsOverlay.isEmpty()) {
            for (int py = 0; py < TILE; py++) {
                int wz = originZ + py * zoom;
                int cz = wz >> 4;
                for (int px = 0; px < TILE; px++) {
                    int wx = originX + px * zoom;
                    int cx = wx >> 4;
                    Integer claimRgb = claimsOverlay.get(packChunk(cx, cz));
                    if (claimRgb == null) continue;

                    int base = rgbBuf[py * TILE + px];
                    boolean edge = (wx & 15) < zoom || (wz & 15) < zoom;
                    if (edge) {
                        Integer west = claimsOverlay.get(packChunk(cx - 1, cz));
                        Integer north = claimsOverlay.get(packChunk(cx, cz - 1));
                        if (!claimRgb.equals(west) || !claimRgb.equals(north)) {
                            base = darker(base);
                        }
                    }
                    rgbBuf[py * TILE + px] = blend(claimRgb, base, 0.58);
                }
            }
        }

        // 3) Marqueurs (warps / homes / f spawn) + position du joueur
        if (showMarkers) {
            drawMarkers(plugin, viewer, world, originX, originZ, zoom, rgbBuf);
        }
        drawSelf(viewer, world, originX, originZ, zoom, rgbBuf);

        // 4) Conversion palette
        for (int i = 0; i < rgbBuf.length; i++) {
            out[i] = MapPalette.match(rgbBuf[i]);
        }
    }

    private static void drawMarkers(CantalePlugin plugin, Player viewer, World world,
                                    int originX, int originZ, int zoom, int[] rgbBuf) {
        int span = TILE * zoom;
        List<Warp> warps = plugin.getWarpManager() != null ? plugin.getWarpManager().getWarps() : List.of();
        for (Warp warp : warps) {
            if (!warp.isActive() || warp.getLocation() == null || warp.getLocation().getWorld() == null) continue;
            if (!warp.getLocation().getWorld().equals(world)) continue;
            int bx = warp.getLocation().getBlockX();
            int bz = warp.getLocation().getBlockZ();
            if (bx < originX || bx >= originX + span || bz < originZ || bz >= originZ + span) continue;
            drawDiamond(rgbBuf, (bx - originX) / zoom, (bz - originZ) / zoom,
                    warp.isEvent() ? RGB_WARP_EVENT : RGB_WARP);
        }

        Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(viewer.getUniqueId());
        if (viewerFaction != null && viewerFaction.getSpawnX() != null && viewerFaction.getSpawnZ() != null
                && world.getName().equals(viewerFaction.getSpawnWorld())) {
            int bx = viewerFaction.getSpawnX().intValue();
            int bz = viewerFaction.getSpawnZ().intValue();
            if (bx >= originX && bx < originX + span && bz >= originZ && bz < originZ + span) {
                drawSquare(rgbBuf, (bx - originX) / zoom, (bz - originZ) / zoom, RGB_FSPAWN);
            }
        }

        if (plugin.getHomeManager() != null) {
            for (Home home : plugin.getHomeManager().ensureHomesLoaded(viewer.getUniqueId()).values()) {
                if (home.getLocation() == null || home.getLocation().getWorld() == null) continue;
                if (!home.getLocation().getWorld().equals(world)) continue;
                int bx = home.getLocation().getBlockX();
                int bz = home.getLocation().getBlockZ();
                if (bx < originX || bx >= originX + span || bz < originZ || bz >= originZ + span) continue;
                drawSquare(rgbBuf, (bx - originX) / zoom, (bz - originZ) / zoom, RGB_HOME);
            }
        }
    }

    private static void drawSelf(Player viewer, World world, int originX, int originZ, int zoom, int[] rgbBuf) {
        if (!viewer.getWorld().equals(world)) return;
        int span = TILE * zoom;
        int bx = viewer.getLocation().getBlockX();
        int bz = viewer.getLocation().getBlockZ();
        if (bx < originX || bx >= originX + span || bz < originZ || bz >= originZ + span) return;
        int cx = (bx - originX) / zoom;
        int cz = (bz - originZ) / zoom;
        // Flèche nord blanche (5×5)
        int[][] arrow = {
                {0, -2}, {-1, -1}, {0, -1}, {1, -1},
                {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0},
                {-1, 1}, {0, 1}, {1, 1}, {0, 2}
        };
        for (int[] d : arrow) {
            put(rgbBuf, cx + d[0], cz + d[1], RGB_SELF);
        }
    }

    private static void drawDiamond(int[] buf, int cx, int cz, int rgb) {
        for (int d = -2; d <= 2; d++) {
            int w = 2 - Math.abs(d);
            for (int dx = -w; dx <= w; dx++) {
                put(buf, cx + dx, cz + d, rgb);
            }
        }
    }

    private static void drawSquare(int[] buf, int cx, int cz, int rgb) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(buf, cx + dx, cz + dz, rgb);
            }
        }
    }

    private static void put(int[] buf, int x, int y, int rgb) {
        if (x < 0 || x >= TILE || y < 0 || y >= TILE) return;
        buf[y * TILE + x] = 0xFF000000 | rgb;
    }

    static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int blend(int overlay, int base, double alpha) {
        int or = (overlay >> 16) & 0xFF;
        int og = (overlay >> 8) & 0xFF;
        int ob = overlay & 0xFF;
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        int r = (int) (or * alpha + br * (1 - alpha));
        int g = (int) (og * alpha + bg * (1 - alpha));
        int b = (int) (ob * alpha + bb * (1 - alpha));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darker(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 2 / 3;
        int g = ((rgb >> 8) & 0xFF) * 2 / 3;
        int b = (rgb & 0xFF) * 2 / 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Snapshot des claims d'un monde pour un viewer (factions secrètes masquées sauf la sienne).
     * Valeur = couleur RGB de relation ; PASDIC = violet quelle que soit la relation.
     */
    static Map<Long, Integer> buildClaimsOverlay(CantalePlugin plugin, String worldName, Faction viewerFaction) {
        Map<Long, Integer> overlay = new HashMap<>();
        FactionManager fm = plugin.getFactionManager();
        DiplomacyManager diplomacy = plugin.getDiplomatieManager();
        for (Faction faction : fm.getAllFactions()) {
            boolean own = viewerFaction != null && faction.getId() == viewerFaction.getId();
            if (faction.isSecret() && !own) continue;
            int rgb;
            if (own) rgb = RGB_OWN;
            else if (viewerFaction != null && diplomacy != null
                    && diplomacy.isAllied(viewerFaction.getId(), faction.getId())) rgb = RGB_ALLY;
            else if (viewerFaction != null && diplomacy != null
                    && diplomacy.isAtWar(viewerFaction.getId(), faction.getId())) rgb = RGB_WAR;
            else rgb = RGB_OTHER;
            for (Claim claim : faction.getClaims()) {
                if (!claim.getWorld().equals(worldName)) continue;
                overlay.put(packChunk(claim.getChunkX(), claim.getChunkZ()),
                        claim.isPasdic() ? RGB_PASDIC : rgb);
            }
        }
        return overlay;
    }

    /** Checksum cheap des claims (changement → overlay à reconstruire). */
    static long claimsChecksum(CantalePlugin plugin) {
        long sum = 0;
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            sum += faction.getClaims().size();
            for (Claim claim : faction.getClaims()) {
                if (claim.isPasdic()) sum += 1_000_003L;
            }
        }
        return sum;
    }
}
