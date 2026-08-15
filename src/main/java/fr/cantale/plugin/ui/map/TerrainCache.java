package fr.cantale.plugin.ui.map;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rendu terrain pour la carte : 16×16 couleurs par chunk (1 entrée = colonne de blocs),
 * calculé depuis un {@link ChunkSnapshot} (async-safe) puis mis en cache.
 *
 * <p>Cache mémoire par région (32×32 chunks) + persistance disque
 * ({@code plugins/CantalePlugin/mapcache/<monde>/<rX>,<rZ>.bin}) pour survivre aux reboots.
 * Un chunk expiré ({@code map.terrain-cache-days}) ou jamais généré est re-rendu à la demande.
 */
public final class TerrainCache {

    /** Taille d'une région de cache en chunks. */
    private static final int REGION_CHUNKS = 32;
    /** Taille de la grille de couleurs par chunk (16×16 colonnes). */
    public static final int CHUNK_RES = 16;

    private final CantalePlugin plugin;
    private final Path cacheDir;
    private final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> pendingChunks = new ConcurrentHashMap<>();
    private volatile long cacheMaxAgeMs;

    public TerrainCache(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cacheDir = plugin.getDataFolder().toPath().resolve("mapcache");
        reloadSettings();
    }

    public void reloadSettings() {
        long days = Math.max(1, plugin.getConfig().getLong("map.terrain-cache-days", 7));
        this.cacheMaxAgeMs = days * 24L * 3600L * 1000L;
    }

    /**
     * Couleur terrain d'une colonne, ou {@code null} si pas encore rendue
     * (un rendu async est alors déclenché — le pixel sera correct au prochain refresh).
     */
    public Color colorAt(World world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        RegionData region = region(world, chunkX, chunkZ);
        CachedChunk cached = region.chunks.get(chunkKey(chunkX, chunkZ));
        if (cached != null && System.currentTimeMillis() - cached.renderedAt < cacheMaxAgeMs) {
            int rgb = cached.colors[(blockZ & 15) * CHUNK_RES + (blockX & 15)];
            return rgb == 0 ? null : new Color(rgb);
        }
        scheduleRender(world, chunkX, chunkZ, region);
        return null;
    }

    private void scheduleRender(World world, int chunkX, int chunkZ, RegionData region) {
        String key = world.getName() + ":" + chunkX + ":" + chunkZ;
        CompletableFuture<Void> inFlight = pendingChunks.computeIfAbsent(key, k -> {
            CompletableFuture<Void> done = new CompletableFuture<>();
            world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, error) -> {
                try {
                    if (error == null && chunk != null) {
                        renderChunk(world, chunk, region);
                    } else if (error == null) {
                        // Chunk jamais généré : mémoriser noir pour ne pas re-poller en boucle
                        CachedChunk empty = new CachedChunk(new int[CHUNK_RES * CHUNK_RES], System.currentTimeMillis());
                        region.chunks.put(chunkKey(chunkX, chunkZ), empty);
                        region.dirty = true;
                    }
                } finally {
                    done.complete(null);
                    pendingChunks.remove(k, done);
                }
            });
            return done;
        });
        inFlight.exceptionally(t -> null);
    }

    private void renderChunk(World world, Chunk chunk, RegionData region) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);
        int[] colors = new int[CHUNK_RES * CHUNK_RES];
        int seaLevel = world.getSeaLevel();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int z = 0; z < CHUNK_RES; z++) {
            for (int x = 0; x < CHUNK_RES; x++) {
                int y = snapshot.getHighestBlockYAt(x, z);
                Material type = y <= world.getMinHeight() ? Material.AIR : snapshot.getBlockType(x, y, z);
                Color base = baseColor(type);

                double shade = 1.0;
                if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                    // Profondeur : on descend tant que c'est de l'eau (max 8)
                    int depth = 0;
                    int yy = y - 1;
                    while (depth < 8 && yy > world.getMinHeight()
                            && snapshot.getBlockType(x, yy, z) == Material.WATER) {
                        depth++;
                        yy--;
                    }
                    shade = Math.max(0.55, 1.0 - depth * 0.06);
                } else if (type != Material.AIR) {
                    // Hauteur vs niveau de la mer + pente (voisin nord-ouest)
                    shade = 0.82 + 0.18 * clamp((y - (seaLevel - 16)) / 140.0);
                    if (x > 0 && z > 0) {
                        int northWest = snapshot.getHighestBlockYAt(x - 1, z - 1);
                        if (y > northWest) shade = Math.min(1.15, shade + 0.08);
                        else if (y < northWest) shade = Math.max(0.7, shade - 0.10);
                    }
                }
                colors[z * CHUNK_RES + x] = applyShade(base, shade).getRGB();
            }
        }
        region.chunks.put(chunkKey(chunk.getX(), chunk.getZ()),
                new CachedChunk(colors, System.currentTimeMillis()));
        region.dirty = true;
    }

    // ─── Palette terrain ────────────────────────────────────────────────

    static Color baseColor(Material type) {
        return switch (type) {
            case WATER, BUBBLE_COLUMN -> new Color(0x3F, 0x76, 0xE4);
            case ICE, FROSTED_ICE, PACKED_ICE -> new Color(0x91, 0xB4, 0xFF);
            case BLUE_ICE -> new Color(0x74, 0xA8, 0xFF);
            case SNOW, SNOW_BLOCK, POWDER_SNOW -> Color.WHITE;
            case SAND, SUSPICIOUS_SAND, SANDSTONE, SMOOTH_SANDSTONE, CUT_SANDSTONE,
                 CHISELED_SANDSTONE, TORCHFLOWER_CROP -> new Color(0xF7, 0xE9, 0xA3);
            case RED_SAND, RED_SANDSTONE -> new Color(0xD8, 0x7E, 0x33);
            case GRAVEL, SUSPICIOUS_GRAVEL -> new Color(0x9A, 0x96, 0x8B);
            case STONE, ANDESITE, DIORITE, GRANITE, COBBLESTONE, MOSSY_COBBLESTONE,
                 STONE_BRICKS, SMOOTH_STONE, TUFF, CALCITE -> new Color(0x8F, 0x8F, 0x8F);
            case DEEPSLATE, COBBLED_DEEPSLATE, DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE,
                 DEEPSLATE_GOLD_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_COPPER_ORE,
                 DEEPSLATE_REDSTONE_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_EMERALD_ORE -> new Color(0x4A, 0x4A, 0x4F);
            case GRASS_BLOCK -> new Color(0x7C, 0xB5, 0x3F);
            case PODZOL -> new Color(0x6A, 0x4B, 0x2A);
            case MYCELIUM -> new Color(0x6F, 0x65, 0x61);
            case DIRT, COARSE_DIRT, ROOTED_DIRT, FARMLAND, DIRT_PATH, MUD -> new Color(0x8A, 0x60, 0x3F);
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES, ACACIA_LEAVES,
                 DARK_OAK_LEAVES, MANGROVE_LEAVES, AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES -> new Color(0x3F, 0x76, 0x2A);
            case CHERRY_LEAVES -> new Color(0xF0, 0x9F, 0xC3);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG, CHERRY_LOG, OAK_WOOD, SPRUCE_WOOD, BIRCH_WOOD, JUNGLE_WOOD,
                 ACACIA_WOOD, DARK_OAK_WOOD, MANGROVE_WOOD, CHERRY_WOOD -> new Color(0x6E, 0x55, 0x33);
            case NETHERRACK -> new Color(0x6F, 0x2A, 0x2A);
            case NETHER_BRICKS, RED_NETHER_BRICKS -> new Color(0x3A, 0x17, 0x1C);
            case BASALT, POLISHED_BASALT, BLACKSTONE, SMOOTH_BASALT -> new Color(0x2B, 0x28, 0x30);
            case SOUL_SAND, SOUL_SOIL -> new Color(0x52, 0x3F, 0x33);
            case LAVA, LAVA_CAULDRON -> new Color(0xE2, 0x5A, 0x12);
            case MAGMA_BLOCK -> new Color(0xB5, 0x3F, 0x14);
            case END_STONE, END_STONE_BRICKS -> new Color(0xDB, 0xDE, 0xA5);
            case OBSIDIAN, CRYING_OBSIDIAN -> new Color(0x1A, 0x10, 0x23);
            case BEDROCK -> new Color(0x33, 0x33, 0x33);
            case COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE, COPPER_ORE,
                 REDSTONE_ORE, LAPIS_ORE, NETHER_QUARTZ_ORE, NETHER_GOLD_ORE, ANCIENT_DEBRIS -> new Color(0x7A, 0x7A, 0x7A);
            case CLAY -> new Color(0x9F, 0xA4, 0xB1);
            case MOSS_BLOCK -> new Color(0x5A, 0x7A, 0x2E);
            default -> defaultColor(type);
        };
    }

    private static Color defaultColor(Material type) {
        if (type.isAir()) return new Color(0x1E, 0x1E, 0x24);
        String name = type.name();
        if (name.contains("LEAVES")) return new Color(0x3F, 0x76, 0x2A);
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("STEM") || name.contains("HYPHAE")) {
            return new Color(0x6E, 0x55, 0x33);
        }
        if (name.contains("PLANKS")) return new Color(0x9A, 0x7B, 0x4F);
        if (name.contains("GLASS") || name.contains("ICE")) return new Color(0xC8, 0xE0, 0xE8);
        if (name.contains("WOOL") || name.contains("CONCRETE") || name.contains("TERRACOTTA")
                || name.contains("GLAZED")) return new Color(0xB0, 0xA8, 0x9A);
        if (name.contains("WATER")) return new Color(0x3F, 0x76, 0xE4);
        if (name.contains("LAVA")) return new Color(0xE2, 0x5A, 0x12);
        if (name.contains("CORAL")) return new Color(0xD0, 0x6A, 0x8E);
        if (type.isSolid()) return new Color(0x8A, 0x82, 0x78);
        // Plantes / rails / décorations : on laisse transparaître le sol
        return new Color(0x6B, 0x66, 0x5E);
    }

    private static Color applyShade(Color base, double factor) {
        return new Color(
                clamp255((int) (base.getRed() * factor)),
                clamp255((int) (base.getGreen() * factor)),
                clamp255((int) (base.getBlue() * factor)));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ─── Persistance disque ─────────────────────────────────────────────

    private RegionData region(World world, int chunkX, int chunkZ) {
        String key = world.getName() + ":" + (chunkX >> 5) + ":" + (chunkZ >> 5);
        return regions.computeIfAbsent(key, k -> loadRegion(world, chunkX >> 5, chunkZ >> 5));
    }

    private RegionData loadRegion(World world, int regionX, int regionZ) {
        RegionData data = new RegionData(world.getName(), regionX, regionZ);
        Path file = regionFile(world.getName(), regionX, regionZ);
        if (!Files.isRegularFile(file)) return data;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                long renderedAt = in.readLong();
                int[] colors = new int[CHUNK_RES * CHUNK_RES];
                for (int p = 0; p < colors.length; p++) {
                    colors[p] = in.readInt();
                }
                data.chunks.put(chunkKey(cx, cz), new CachedChunk(colors, renderedAt));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Map] Cache région illisible (" + file.getFileName() + ") : " + e.getMessage());
        }
        return data;
    }

    /** Écrit les régions modifiées (async — appeler hors thread principal). */
    public void saveDirty() {
        List<RegionData> dirty = new ArrayList<>();
        for (RegionData region : regions.values()) {
            if (region.dirty) dirty.add(region);
        }
        if (dirty.isEmpty()) return;
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            for (RegionData region : dirty) {
                writeRegion(region);
            }
        });
    }

    private void writeRegion(RegionData region) {
        Path file = regionFile(region.world, region.regionX, region.regionZ);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(region.chunks.size());
                for (Map.Entry<String, CachedChunk> entry : region.chunks.entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    out.writeInt(Integer.parseInt(parts[0]));
                    out.writeInt(Integer.parseInt(parts[1]));
                    out.writeLong(entry.getValue().renderedAt);
                    for (int rgb : entry.getValue().colors) {
                        out.writeInt(rgb);
                    }
                }
            }
            region.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("[Map] Écriture cache " + file.getFileName() + " : " + e.getMessage());
        }
    }

    private Path regionFile(String world, int regionX, int regionZ) {
        return cacheDir.resolve(world).resolve(regionX + "," + regionZ + ".bin");
    }

    private static String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    private record CachedChunk(int[] colors, long renderedAt) {}

    private static final class RegionData {
        final String world;
        final int regionX;
        final int regionZ;
        final Map<String, CachedChunk> chunks = new ConcurrentHashMap<>();
        volatile boolean dirty;

        RegionData(String world, int regionX, int regionZ) {
            this.world = world;
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
    }
}
