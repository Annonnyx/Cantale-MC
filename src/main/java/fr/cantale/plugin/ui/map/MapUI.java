package fr.cantale.plugin.ui.map;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service « carte du monde » de Cantale.
 *
 * <ul>
 *   <li>{@code /carte} : GUI inventaire de tuiles {@code FILLED_MAP} (terrain + claims +
 *       warps/homes), interactions claim / PASDIC / téléportation ({@code WorldMapUI}).</li>
 *   <li>{@code /carte mini} : minimap tenue en main, centrée sur le joueur, mise à jour live.</li>
 * </ul>
 *
 * Aucune entité : tout passe par des {@link MapView} partagées avec renderer contextuel
 * (contenu différent par joueur). Les IDs de vues sont persistés dans
 * {@code plugins/CantalePlugin/map-views.yml} et réutilisés au reboot (pas de fuite d'IDs).
 * Le GUI n'étant pas l'inventaire du joueur, les tuiles sont poussées via
 * {@code Player#sendMap(MapView)} quand leur contenu change (pas de spam réseau).
 */
public final class MapUI {

    public static final int GRID_COLS = 7;
    public static final int GRID_ROWS = 4;
    public static final int TILE_COUNT = GRID_COLS * GRID_ROWS;

    private static final int REFRESH_TICKS = 10;
    private static final long OVERLAY_REBUILD_MS = 2_000L;
    private static final long TERRAIN_SAVE_TICKS = 20L * 300L;

    private final CantalePlugin plugin;
    private final TerrainCache terrainCache;
    private final NamespacedKey minimapKey;

    private final List<MapView> tileViews = new ArrayList<>();
    private final List<WorldMapRenderer> tileRenderers = new ArrayList<>();
    private MapView minimapView;
    private WorldMapRenderer minimapRenderer;

    private final Map<UUID, MapSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, OverlayCache> overlays = new ConcurrentHashMap<>();
    private final Map<UUID, MinimapState> minimaps = new ConcurrentHashMap<>();

    private volatile long cachedClaimsChecksum = -1;
    private volatile long lastChecksumCompute;

    private BukkitTask refreshTask;
    private BukkitTask saveTask;

    public MapUI(CantalePlugin plugin) {
        this.plugin = plugin;
        this.terrainCache = new TerrainCache(plugin);
        this.minimapKey = new NamespacedKey(plugin, "minimap");
        initViews();
        startTasks();
    }

    // ─── Vues persistées ────────────────────────────────────────────────

    private void initViews() {
        World overworld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (overworld == null) {
            plugin.getLogger().warning("[Map] Aucun monde chargé — carte désactivée.");
            return;
        }
        File file = new File(plugin.getDataFolder(), "map-views.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Integer> savedTiles = yaml.getIntegerList("tiles");
        boolean dirty = false;

        for (int i = 0; i < TILE_COUNT; i++) {
            MapView view = i < savedTiles.size() ? Bukkit.getMap(savedTiles.get(i)) : null;
            if (view == null) {
                view = Bukkit.createMap(overworld);
                dirty = true;
            }
            prepare(view);
            WorldMapRenderer renderer = new WorldMapRenderer(this, i);
            view.addRenderer(renderer);
            tileViews.add(view);
            tileRenderers.add(renderer);
        }

        int minimapId = yaml.getInt("minimap", Integer.MIN_VALUE);
        MapView minimap = minimapId != Integer.MIN_VALUE ? Bukkit.getMap(minimapId) : null;
        if (minimap == null) {
            minimap = Bukkit.createMap(overworld);
            dirty = true;
        }
        prepare(minimap);
        minimapRenderer = new WorldMapRenderer(this, WorldMapRenderer.MINIMAP_TILE);
        minimap.addRenderer(minimapRenderer);
        minimapView = minimap;

        if (dirty) {
            yaml.set("tiles", tileViews.stream().map(MapView::getId).toList());
            yaml.set("minimap", minimapView.getId());
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("[Map] Sauvegarde map-views.yml : " + e.getMessage());
            }
        }
    }

    private static void prepare(MapView view) {
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
    }

    // ─── Sessions GUI ───────────────────────────────────────────────────

    public MapSession openSession(Player player) {
        int defaultZoom = plugin.getConfig().getInt("map.default-zoom", 4);
        int zoomIndex = 2;
        for (int i = 0; i < MapSession.ZOOM_LEVELS.length; i++) {
            if (MapSession.ZOOM_LEVELS[i] == defaultZoom) {
                zoomIndex = i;
                break;
            }
        }
        MapSession session = new MapSession(player.getUniqueId(), player.getWorld().getName(),
                player.getLocation().getBlockX(), player.getLocation().getBlockZ(), zoomIndex, TILE_COUNT);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    /** Ré-enregistre la session si elle a été fermée (ex. retour depuis le dialog PASDIC). */
    public void ensureSession(Player player, MapSession session) {
        if (sessions.putIfAbsent(player.getUniqueId(), session) == null) {
            session.markAllDirty();
        }
    }

    public MapSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void closeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    public MapView tileView(int index) {
        return tileViews.get(index);
    }

    // ─── Rendu tuiles ───────────────────────────────────────────────────

    void renderTile(MapSession session, Player viewer, int tileIndex) {
        World world = Bukkit.getWorld(session.world);
        if (world == null) return;
        int col = tileIndex % GRID_COLS;
        int row = tileIndex / GRID_COLS;
        int originX = session.tileOriginX(col, GRID_COLS);
        int originZ = session.tileOriginZ(row, GRID_ROWS);
        Map<Long, Integer> overlay = session.showClaims ? overlayFor(world.getName(), viewer) : Map.of();
        MapImageBuilder.renderArea(plugin, terrainCache, viewer, world, originX, originZ, session.zoom(),
                session.showTerrain, session.showClaims, session.showMarkers, overlay,
                session.tiles[tileIndex]);
    }

    /** Marque toutes les tuiles de la session à reconstruire + renvoyer. */
    public void invalidate(MapSession session) {
        session.markAllDirty();
        for (WorldMapRenderer renderer : tileRenderers) {
            renderer.bumpVersion();
        }
    }

    // ─── Minimap ────────────────────────────────────────────────────────

    /** True si l'item est une minimap Cantale (tag PDC posé par {@link #giveMinimap}). */
    public boolean isMinimap(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(minimapKey, PersistentDataType.BYTE);
    }

    public void giveMinimap(Player player) {
        if (minimapView == null) {
            player.sendMessage("§8[§6Carte§8] §cLa minimap est désactivée.");
            return;
        }
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(minimapView);
            meta.setDisplayName("§6§lMinimap Cantale");
            meta.setLore(List.of(
                    "§7Tiens-la en main (ou en main secondaire)",
                    "§7pour voir le terrain, les claims et ta position.",
                    "§8Clic droit / F : ouvrir la grande carte",
                    "§8Zoom : 2 blocs/px · Nord en haut"));
            meta.getPersistentDataContainer().set(minimapKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        player.sendMessage("§8[§6Carte§8] §aMinimap ajoutée à ton inventaire !");
    }

    byte[] minimapBuffer(Player player) {
        if (minimapView == null || !plugin.getConfig().getBoolean("map.minimap-enabled", true)) {
            return null;
        }
        MinimapState state = minimaps.computeIfAbsent(player.getUniqueId(), id -> new MinimapState());
        World world = player.getWorld();
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        long overlayVersion = overlayVersion(world.getName(), player);
        if (state.lastChunkX != cx || state.lastChunkZ != cz
                || state.lastOverlayVersion != overlayVersion || !world.getName().equals(state.world)) {
            state.world = world.getName();
            state.lastChunkX = cx;
            state.lastChunkZ = cz;
            state.lastOverlayVersion = overlayVersion;
            int originX = player.getLocation().getBlockX() - (MapSession.TILE_SIZE * WorldMapRenderer.MINIMAP_ZOOM) / 2;
            int originZ = player.getLocation().getBlockZ() - (MapSession.TILE_SIZE * WorldMapRenderer.MINIMAP_ZOOM) / 2;
            Map<Long, Integer> overlay = overlayFor(world.getName(), player);
            MapImageBuilder.renderArea(plugin, terrainCache, player, world, originX, originZ,
                    WorldMapRenderer.MINIMAP_ZOOM, true, true, true, overlay, state.buffer);
            state.version++;
        }
        return state.buffer;
    }

    int minimapVersion(Player player) {
        MinimapState state = minimaps.get(player.getUniqueId());
        return state != null ? state.version : -1;
    }

    // ─── Overlay claims (partagé tuiles + minimap, par monde+faction) ───

    private Map<Long, Integer> overlayFor(String worldName, Player viewer) {
        Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(viewer.getUniqueId());
        String key = worldName + ":" + (viewerFaction != null ? viewerFaction.getId() : -1);
        long checksum = currentClaimsChecksum();
        OverlayCache cache = overlays.get(key);
        long now = System.currentTimeMillis();
        if (cache == null || cache.checksum != checksum || now - cache.builtAt > 30_000L) {
            Map<Long, Integer> map = MapImageBuilder.buildClaimsOverlay(plugin, worldName, viewerFaction);
            cache = new OverlayCache(map, checksum, now);
            overlays.put(key, cache);
        }
        return cache.map;
    }

    /** Checksum des claims, recalculé au plus toutes les {@value #OVERLAY_REBUILD_MS} ms. */
    long currentClaimsChecksum() {
        long now = System.currentTimeMillis();
        if (cachedClaimsChecksum < 0 || now - lastChecksumCompute > OVERLAY_REBUILD_MS) {
            cachedClaimsChecksum = MapImageBuilder.claimsChecksum(plugin);
            lastChecksumCompute = now;
        }
        return cachedClaimsChecksum;
    }

    private long overlayVersion(String worldName, Player viewer) {
        overlayFor(worldName, viewer);
        String key = worldName + ":" + factionIdOf(viewer);
        OverlayCache cache = overlays.get(key);
        return cache != null ? cache.builtAt : 0L;
    }

    private int factionIdOf(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        return faction != null ? faction.getId() : -1;
    }

    // ─── Tâches périodiques ─────────────────────────────────────────────

    private void startTasks() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sessions.isEmpty()) {
                long claimsChecksum = currentClaimsChecksum();
                for (Map.Entry<UUID, MapSession> entry : sessions.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    MapSession session = entry.getValue();
                    if (player == null || !player.isOnline()) continue;

                    if (session.claimsChecksum != claimsChecksum) {
                        session.claimsChecksum = claimsChecksum;
                        session.markAllDirty();
                    }
                    // Curseur joueur : re-render des tuiles ancienne/nouvelle position uniquement
                    int cx = player.getLocation().getBlockX() >> 4;
                    int cz = player.getLocation().getBlockZ() >> 4;
                    if (cx != session.cursorChunkX || cz != session.cursorChunkZ) {
                        markCursorTiles(session, session.cursorChunkX, session.cursorChunkZ);
                        session.cursorChunkX = cx;
                        session.cursorChunkZ = cz;
                        markCursorTiles(session, cx, cz);
                    }
                    for (int i = 0; i < TILE_COUNT; i++) {
                        if (session.tileDirty[i]) {
                            player.sendMap(tileViews.get(i));
                            session.tileDirty[i] = false;
                        }
                    }
                    session.dirtyAll = false;
                }
            }
            overlays.values().removeIf(c -> System.currentTimeMillis() - c.builtAt > 120_000L);
        }, 20L, REFRESH_TICKS);

        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                terrainCache::saveDirty, TERRAIN_SAVE_TICKS, TERRAIN_SAVE_TICKS);
    }

    private void markCursorTiles(MapSession session, int chunkX, int chunkZ) {
        if (chunkX == Integer.MIN_VALUE) return;
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        int span = session.tileSpan() * GRID_COLS;
        int left = session.centerX - span / 2;
        int top = session.centerZ - session.tileSpan() * GRID_ROWS / 2;
        int relX = blockX - left;
        int relZ = blockZ - top;
        if (relX < 0 || relZ < 0) return;
        int col = relX / session.tileSpan();
        int row = relZ / session.tileSpan();
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) return;
        session.tileDirty[row * GRID_COLS + col] = true;
    }

    /** Marque les tuiles contenant un chunk précis (claim/unclaim/PASDIC) et force l'overlay à jour. */
    public void invalidateChunk(MapSession session, int chunkX, int chunkZ) {
        cachedClaimsChecksum = -1;
        session.claimsChecksum = -1;
        session.markAllDirty();
        markCursorTiles(session, chunkX, chunkZ);
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        if (saveTask != null) saveTask.cancel();
        sessions.clear();
        minimaps.clear();
        terrainCache.saveDirty();
    }

    public void cleanup(Player player) {
        sessions.remove(player.getUniqueId());
        minimaps.remove(player.getUniqueId());
    }

    public TerrainCache terrain() {
        return terrainCache;
    }

    private record OverlayCache(Map<Long, Integer> map, long checksum, long builtAt) {}

    private static final class MinimapState {
        final byte[] buffer = new byte[MapSession.TILE_SIZE * MapSession.TILE_SIZE];
        String world;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        long lastOverlayVersion = Long.MIN_VALUE;
        int version;
    }
}
