package fr.cantale.client.render;

import java.util.Map;

import fr.cantale.client.data.ServerDataStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

/**
 * Vue carte réutilisable (écran plein format et minimap HUD) : terrain rendu dans une
 * texture re-peinte uniquement quand la vue bouge ou que les données changent, puis
 * overlays dessinés par-dessus (claims colorés par relation, warps, homes, /f spawn,
 * flèche du joueur).
 *
 * Sans données serveur (serveur sans le plugin Cantale), seul le terrain s'affiche.
 */
public final class MapCanvas {

	/** Marge de dérive (en px écran) tolérée avant de repeindre la texture (pan fluide). */
	private static final int REBUILD_MARGIN_PX = 16;

	// Couleurs des claims par relation (cohérentes avec la map in-game du serveur)
	private static final int RGB_OWN = 0x3FD23F;
	private static final int RGB_ALLY = 0x3FB4D2;
	private static final int RGB_WAR = 0xE69B3A;
	private static final int RGB_ENEMY = 0xD23F3F;
	private static final int RGB_PASDIC = 0xB04FD8;
	private static final int RGB_OTHER = 0x8A8A96;

	private static final int RGB_WARP = 0x35E0D0;
	private static final int RGB_WARP_EVENT = 0xFFD24A;
	private static final int RGB_HOME = 0x7CFC3F;
	private static final int RGB_FSPAWN = 0xFF6E40;

	private static final int CLAIM_FILL_ALPHA = 0x5A;
	private static final int CLAIM_EDGE_ALPHA = 0xC0;

	// Flèche du joueur en 8 directions (5×5, 1 = pixel blanc), 0 = nord en haut
	private static final int[][] ARROW_N = {
			{0, 0, 1, 0, 0}, {0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {0, 0, 1, 0, 0}, {0, 0, 1, 0, 0}};
	private static final int[][] ARROW_NE = {
			{0, 0, 1, 1, 1}, {0, 0, 0, 1, 1}, {0, 1, 0, 1, 0}, {0, 0, 1, 0, 0}, {1, 1, 0, 0, 0}};
	private static final int[][] ARROW_E = {
			{0, 0, 1, 0, 0}, {0, 0, 1, 1, 0}, {1, 1, 1, 1, 1}, {0, 0, 1, 1, 0}, {0, 0, 1, 0, 0}};
	private static final int[][] ARROW_SE = {
			{1, 1, 0, 0, 0}, {0, 0, 1, 0, 0}, {0, 1, 0, 1, 0}, {0, 0, 0, 1, 1}, {0, 0, 1, 1, 1}};
	private static final int[][] ARROW_S = {
			{0, 0, 1, 0, 0}, {0, 0, 1, 0, 0}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}, {0, 0, 1, 0, 0}};
	private static final int[][] ARROW_SW = {
			{0, 0, 0, 1, 1}, {0, 0, 1, 0, 0}, {0, 1, 0, 1, 0}, {1, 1, 0, 0, 0}, {1, 1, 1, 0, 0}};
	private static final int[][] ARROW_W = {
			{0, 0, 1, 0, 0}, {0, 1, 1, 0, 0}, {1, 1, 1, 1, 1}, {0, 1, 1, 0, 0}, {0, 0, 1, 0, 0}};
	private static final int[][] ARROW_NW = {
			{1, 1, 1, 0, 0}, {1, 1, 0, 0, 0}, {0, 1, 0, 1, 0}, {0, 0, 1, 0, 0}, {0, 0, 0, 1, 1}};
	private static final int[][][] ARROWS = {ARROW_N, ARROW_NE, ARROW_E, ARROW_SE, ARROW_S, ARROW_SW, ARROW_W, ARROW_NW};

	private final int widthPx;
	private final int heightPx;
	private final Identifier textureId;
	private NativeImageBackedTexture texture;

	// Instantané des paramètres de la dernière peinture (évite de repeindre chaque frame)
	private double renderedCenterX;
	private double renderedCenterZ;
	private int renderedZoom = -1;
	private String renderedWorldId = null;
	private long renderedDataVersion = -1;
	private long renderedTerrainVersion = -1;
	private boolean renderedOnce;

	public MapCanvas(String name, int widthPx, int heightPx) {
		this.widthPx = widthPx;
		this.heightPx = heightPx;
		this.textureId = Identifier.of("cantale", name);
	}

	public int widthPx() {
		return widthPx;
	}

	public int heightPx() {
		return heightPx;
	}

	/** Libère la texture (changement de taille d'écran, fermeture de la vue). */
	public void dispose() {
		if (texture != null) {
			try {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
			} catch (Exception ignored) {
				// Texture déjà détruite : sans gravité
			}
			texture = null;
			renderedOnce = false;
		}
	}

	private void ensureTexture() {
		if (texture == null) {
			texture = new NativeImageBackedTexture(() -> "cantale_" + textureId.getPath(), widthPx, heightPx, true);
			MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
		}
	}

	/**
	 * Dessine la carte à ({@code x}, {@code y}) : terrain + overlays.
	 *
	 * @param serverWorld  monde serveur courant (claims/marqueurs), ex. « world »
	 * @param zoom         blocs par pixel (1, 2, 4, 8, 16)
	 * @param showLabels   affiche les noms des marqueurs (plein écran uniquement)
	 * @param playerArrow  affiche la flèche du joueur
	 */
	public void draw(DrawContext context, int x, int y, ClientWorld world, String serverWorld,
					 double centerX, double centerZ, int zoom, boolean showLabels, boolean playerArrow) {
		ensureTexture();

		String worldId = world.getRegistryKey().getValue().toString();
		long dataVersion = ServerDataStore.dataVersion();
		long terrainVersion = TerrainSampler.INSTANCE.version();
		boolean rebuild = !renderedOnce
				|| zoom != renderedZoom
				|| !worldId.equals(renderedWorldId)
				|| dataVersion != renderedDataVersion
				|| terrainVersion != renderedTerrainVersion
				|| Math.abs(centerX - renderedCenterX) >= (double) REBUILD_MARGIN_PX * zoom
				|| Math.abs(centerZ - renderedCenterZ) >= (double) REBUILD_MARGIN_PX * zoom;
		if (rebuild) {
			paintTerrain(world, centerX, centerZ, zoom);
			texture.upload();
			renderedCenterX = centerX;
			renderedCenterZ = centerZ;
			renderedZoom = zoom;
			renderedWorldId = worldId;
			renderedDataVersion = dataVersion;
			renderedTerrainVersion = terrainVersion;
			renderedOnce = true;
		}

		// Décalage entre la texture (peinte pour renderedCenter) et la vue demandée
		int shiftX = (int) Math.round((renderedCenterX - centerX) / zoom);
		int shiftZ = (int) Math.round((renderedCenterZ - centerZ) / zoom);
		int drawX = x + shiftX;
		int drawY = y + shiftZ;
		double originX = renderedCenterX - widthPx / 2.0 * zoom;
		double originZ = renderedCenterZ - heightPx / 2.0 * zoom;

		context.enableScissor(x, y, x + widthPx, y + heightPx);
		try {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, drawX, drawY,
					0.0f, 0.0f, widthPx, heightPx, widthPx, heightPx);
			paintClaims(context, drawX, drawY, serverWorld, originX, originZ, zoom);
			paintMarkers(context, drawX, drawY, serverWorld, originX, originZ, zoom, showLabels);
			if (playerArrow) {
				paintPlayer(context, drawX, drawY, originX, originZ, zoom);
			}
		} finally {
			context.disableScissor();
		}
	}

	// ─── Terrain ─────────────────────────────────────────────────

	private void paintTerrain(ClientWorld world, double centerX, double centerZ, int zoom) {
		NativeImage image = texture.getImage();
		if (image == null) return;
		double originX = centerX - widthPx / 2.0 * zoom;
		double originZ = centerZ - heightPx / 2.0 * zoom;
		for (int py = 0; py < heightPx; py++) {
			int wz = (int) Math.floor(originZ + py * zoom);
			int chunkZ = wz >> 4;
			int lz = wz & 15;
			for (int px = 0; px < widthPx; px++) {
				int wx = (int) Math.floor(originX + px * zoom);
				image.setColorArgb(px, py,
						TerrainSampler.INSTANCE.columnColor(world, wx >> 4, chunkZ, wx & 15, lz));
			}
		}
	}

	// ─── Claims ──────────────────────────────────────────────────

	private void paintClaims(DrawContext context, int drawX, int drawY, String serverWorld,
							 double originX, double originZ, int zoom) {
		Map<Long, String> worldClaims = ServerDataStore.claimsOf(serverWorld);
		if (worldClaims.isEmpty()) return;

		int minChunkX = (int) Math.floor(originX / 16.0);
		int minChunkZ = (int) Math.floor(originZ / 16.0);
		int maxChunkX = (int) Math.floor((originX + widthPx * zoom) / 16.0);
		int maxChunkZ = (int) Math.floor((originZ + heightPx * zoom) / 16.0);

		for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				String rel = worldClaims.get(ServerDataStore.pack(chunkX, chunkZ));
				if (rel == null) continue;
				int rgb = colorForRelation(rel);

				int x1 = drawX + (int) Math.floor((chunkX * 16.0 - originX) / zoom);
				int y1 = drawY + (int) Math.floor((chunkZ * 16.0 - originZ) / zoom);
				int x2 = drawX + (int) Math.ceil(((chunkX * 16.0 + 16) - originX) / zoom);
				int y2 = drawY + (int) Math.ceil(((chunkZ * 16.0 + 16) - originZ) / zoom);
				if (x2 <= x1) x2 = x1 + 1;
				if (y2 <= y1) y2 = y1 + 1;

				context.fill(x1, y1, x2, y2, (CLAIM_FILL_ALPHA << 24) | rgb);
				// Bordures haut/gauche plus marquées pour distinguer les territoires
				context.fill(x1, y1, x2, y1 + 1, (CLAIM_EDGE_ALPHA << 24) | rgb);
				context.fill(x1, y1, x1 + 1, y2, (CLAIM_EDGE_ALPHA << 24) | rgb);
			}
		}
	}

	private static int colorForRelation(String rel) {
		return switch (rel) {
			case "OWN" -> RGB_OWN;
			case "ALLY" -> RGB_ALLY;
			case "WAR" -> RGB_WAR;
			case "ENEMY" -> RGB_ENEMY;
			case "PASDIC" -> RGB_PASDIC;
			default -> RGB_OTHER;
		};
	}

	// ─── Marqueurs ───────────────────────────────────────────────

	private void paintMarkers(DrawContext context, int drawX, int drawY, String serverWorld,
							  double originX, double originZ, int zoom, boolean showLabels) {
		for (ServerDataStore.Marker warp : ServerDataStore.markersIn(ServerDataStore.warps(), serverWorld)) {
			paintMarker(context, drawX, drawY, originX, originZ, zoom, warp.x(), warp.z(),
					warp.event() ? RGB_WARP_EVENT : RGB_WARP, "W", showLabels ? warp.name() : null);
		}
		for (ServerDataStore.Marker home : ServerDataStore.markersIn(ServerDataStore.homes(), serverWorld)) {
			paintMarker(context, drawX, drawY, originX, originZ, zoom, home.x(), home.z(),
					RGB_HOME, "H", showLabels ? home.name() : null);
		}
		ServerDataStore.Marker fspawn = ServerDataStore.factionSpawn();
		if (fspawn != null && serverWorld.equals(fspawn.world())) {
			paintMarker(context, drawX, drawY, originX, originZ, zoom, fspawn.x(), fspawn.z(),
					RGB_FSPAWN, "S", showLabels ? fspawn.name() : null);
		}
	}

	private void paintMarker(DrawContext context, int drawX, int drawY, double originX, double originZ,
							 int zoom, double worldX, double worldZ, int rgb, String letter, String label) {
		int sx = drawX + (int) Math.round((worldX - originX) / zoom);
		int sy = drawY + (int) Math.round((worldZ - originZ) / zoom);
		if (sx < drawX - 4 || sx >= drawX + widthPx + 4 || sy < drawY - 4 || sy >= drawY + heightPx + 4) return;

		context.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF101018);
		context.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF000000 | rgb);
		if (letter != null) {
			TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
			context.drawTextWithShadow(textRenderer, letter, sx + 4, sy - 4, 0xFF000000 | rgb);
			if (label != null) {
				context.drawTextWithShadow(textRenderer, label,
						sx + 4 + textRenderer.getWidth(letter) + 3, sy - 4, 0xFFE8E8F0);
			}
		}
	}

	// ─── Joueur ──────────────────────────────────────────────────

	private void paintPlayer(DrawContext context, int drawX, int drawY, double originX, double originZ, int zoom) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		int sx = drawX + (int) Math.round((client.player.getX() - originX) / zoom);
		int sy = drawY + (int) Math.round((client.player.getZ() - originZ) / zoom);
		if (sx < drawX - 4 || sx >= drawX + widthPx + 4 || sy < drawY - 4 || sy >= drawY + heightPx + 4) return;

		// Yaw Minecraft : 0 = sud (+Z), 180 = nord (-Z) ; la carte affiche le nord en haut
		float yaw = client.player.getYaw();
		int direction = (int) Math.floor(((yaw % 360 + 360 + 180 + 22.5) % 360) / 45.0) & 7;
		int[][] arrow = ARROWS[direction];

		// Ombre sombre 7×7 puis flèche blanche 5×5
		context.fill(sx - 4, sy - 4, sx + 3, sy + 3, 0x99101018);
		for (int dy = 0; dy < 5; dy++) {
			for (int dx = 0; dx < 5; dx++) {
				if (arrow[dy][dx] != 0) {
					context.fill(sx - 2 + dx, sy - 2 + dy, sx - 1 + dx, sy - 1 + dy, 0xFFFFFFFF);
				}
			}
		}
	}
}
