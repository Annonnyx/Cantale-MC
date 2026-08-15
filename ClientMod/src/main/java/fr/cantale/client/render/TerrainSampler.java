package fr.cantale.client.render;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Échantillonne le terrain du monde client en couleurs top-down (style JourneyMap) :
 * pour chaque colonne d'un chunk (16×16), la couleur du bloc de surface
 * (teinte biome quand elle existe, couleur de carte sinon) avec un léger ombrage
 * de relief basé sur la hauteur du voisin ouest.
 *
 * Les couleurs sont mises en cache par chunk ; les chunks proches du joueur sont
 * ré-échantillonnés après {@value #REFRESH_NEAR_MS} ms pour suivre les constructions.
 */
public final class TerrainSampler {

	public static final TerrainSampler INSTANCE = new TerrainSampler();

	/** Couleur des zones non chargées. */
	private static final int UNKNOWN = 0xFF26262E;
	/** Fraîcheur max des chunks proches du joueur (suivi des constructions). */
	private static final long REFRESH_NEAR_MS = 10_000;
	/** Rayon (en chunks) du rafraîchissement autour du joueur. */
	private static final int REFRESH_RADIUS = 2;

	private record ChunkColors(int[] colors, long sampledAt) {
	}

	private final Map<Long, ChunkColors> cache = new ConcurrentHashMap<>();
	private final AtomicLong version = new AtomicLong();

	private ClientWorld lastWorld;
	private int tickCounter;

	private TerrainSampler() {
	}

	/** Incrémenté quand le terrain change : les vues se repeignent. */
	public long version() {
		return version.get();
	}

	/** Couleur ARGB de la colonne (localX, localZ) du chunk (chunkX, chunkZ). */
	public int columnColor(ClientWorld world, int chunkX, int chunkZ, int localX, int localZ) {
		long key = pack(chunkX, chunkZ);
		ChunkColors colors = cache.get(key);
		if (colors == null) {
			colors = sampleChunk(world, chunkX, chunkZ);
			if (colors == null) {
				// Chunk non chargé : inconnu, et non mémorisé (retenté à la prochaine frame)
				return UNKNOWN;
			}
			cache.put(key, colors);
		}
		return colors.colors()[localZ * 16 + localX];
	}

	/** Maintenance périodique : reset au changement de monde + refresh autour du joueur. */
	public void tick(MinecraftClient client) {
		ClientWorld world = client != null ? client.world : null;
		if (world != lastWorld) {
			cache.clear();
			lastWorld = world;
			version.incrementAndGet();
		}
		if (world == null || client.player == null) return;
		if (++tickCounter % 40 != 0) return;

		int playerChunkX = client.player.getBlockX() >> 4;
		int playerChunkZ = client.player.getBlockZ() >> 4;
		long now = System.currentTimeMillis();
		for (int dx = -REFRESH_RADIUS; dx <= REFRESH_RADIUS; dx++) {
			for (int dz = -REFRESH_RADIUS; dz <= REFRESH_RADIUS; dz++) {
				long key = pack(playerChunkX + dx, playerChunkZ + dz);
				ChunkColors existing = cache.get(key);
				if (existing != null && now - existing.sampledAt() > REFRESH_NEAR_MS) {
					ChunkColors fresh = sampleChunk(world, playerChunkX + dx, playerChunkZ + dz);
					if (fresh != null) {
						cache.put(key, fresh);
						version.incrementAndGet();
					}
				}
			}
		}
	}

	/** Échantillonne les 256 colonnes d'un chunk ; {@code null} si le chunk n'est pas chargé. */
	private ChunkColors sampleChunk(ClientWorld world, int chunkX, int chunkZ) {
		WorldChunk chunk;
		try {
			chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		} catch (Exception e) {
			return null;
		}
		if (chunk == null) return null;

		BlockColors blockColors = MinecraftClient.getInstance().getBlockColors();
		int[] heights = new int[256];
		int[] colors = new int[256];
		Arrays.fill(colors, UNKNOWN);

		BlockPos.Mutable pos = new BlockPos.Mutable();
		int bottomY = world.getBottomY();
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;

		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int index = lz * 16 + lx;
				int y;
				try {
					y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, lx, lz);
				} catch (Exception e) {
					continue;
				}
				heights[index] = y;
				if (y <= bottomY) continue;

				pos.set(baseX + lx, y, baseZ + lz);
				BlockState state = chunk.getBlockState(pos);
				if (state == null || state.isAir()) continue;

				int rgb;
				try {
					// Teinte biome (herbe, feuilles, eau…) si un provider existe, sinon couleur carte
					int tint = blockColors.getColor(state, world, pos, 0);
					rgb = tint != -1 ? (tint & 0xFFFFFF) : (state.getMapColor(world, pos).color & 0xFFFFFF);
				} catch (Exception e) {
					continue;
				}
				colors[index] = 0xFF000000 | rgb;
			}
		}

		// Ombrage de relief : plus lumineux si la colonne est plus haute que sa voisine ouest
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int index = lz * 16 + lx;
				if (colors[index] == UNKNOWN) continue;
				int dy = heights[index] - (lx > 0 ? heights[index - 1] : heights[index]);
				if (dy != 0) {
					colors[index] = shade(colors[index], dy);
				}
			}
		}
		return new ChunkColors(colors, System.currentTimeMillis());
	}

	/** Éclaircit (dy &gt; 0) ou assombrit (dy &lt; 0), borné à ±3 niveaux. */
	private static int shade(int argb, int dy) {
		float factor = 1.0f + 0.07f * Math.max(-3, Math.min(3, dy));
		int r = Math.min(255, Math.max(0, (int) (((argb >> 16) & 0xFF) * factor)));
		int g = Math.min(255, Math.max(0, (int) (((argb >> 8) & 0xFF) * factor)));
		int b = Math.min(255, Math.max(0, (int) ((argb & 0xFF) * factor)));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static long pack(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
