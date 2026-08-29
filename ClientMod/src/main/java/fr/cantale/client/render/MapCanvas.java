package fr.cantale.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fr.cantale.client.data.ServerDataStore;
import fr.cantale.client.data.WaypointManager;
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
 * overlays dessinés par-dessus (claims en contours de territoire, warps, homes, /f spawn,
 * flèche du joueur).
 *
 * Sans données serveur (serveur sans le plugin Cantale), seul le terrain s'affiche.
 */
public final class MapCanvas {

	/** Marge de dérive (en px écran) tolérée avant de repeindre la texture (pan fluide). */
	private static final int REBUILD_MARGIN_PX = 16;

	/** Au-delà, pins seuls ; le label n'apparaît qu'au survol. */
	private static final int LABEL_ZOOM_THRESHOLD = 8;

	private static final int HIT_RADIUS_PX = 8;

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

	private static final int CLAIM_FILL_ALPHA = 0x3A;
	private static final int CLAIM_EDGE_ALPHA = 0xE6;

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

	/** Paramètres de la dernière frame (world ↔ écran, y compris le shift de pan). */
	private int lastDrawX;
	private int lastDrawY;
	private int lastZoom = 1;
	private double lastOriginX;
	private double lastOriginZ;

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

	public int lastDrawX() {
		return lastDrawX;
	}

	public int lastDrawY() {
		return lastDrawY;
	}

	public int lastZoom() {
		return lastZoom;
	}

	public double lastOriginX() {
		return lastOriginX;
	}

	public double lastOriginZ() {
		return lastOriginZ;
	}

	public double screenToWorldX(double screenX) {
		return lastOriginX + (screenX - lastDrawX) * lastZoom;
	}

	public double screenToWorldZ(double screenZ) {
		return lastOriginZ + (screenZ - lastDrawY) * lastZoom;
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
		draw(context, x, y, world, serverWorld, centerX, centerZ, zoom, showLabels, playerArrow,
				Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	public void draw(DrawContext context, int x, int y, ClientWorld world, String serverWorld,
					 double centerX, double centerZ, int zoom, boolean showLabels, boolean playerArrow,
					 int hoverX, int hoverY) {
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

		lastDrawX = drawX;
		lastDrawY = drawY;
		lastZoom = zoom;
		lastOriginX = originX;
		lastOriginZ = originZ;

		context.enableScissor(x, y, x + widthPx, y + heightPx);
		try {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, drawX, drawY,
					0.0f, 0.0f, widthPx, heightPx, widthPx, heightPx);
			paintClaims(context, drawX, drawY, serverWorld, originX, originZ, zoom);
			paintMarkers(context, drawX, drawY, serverWorld, originX, originZ, zoom, showLabels, hoverX, hoverY);
			if (playerArrow) {
				paintPlayer(context, drawX, drawY, originX, originZ, zoom);
			}
		} finally {
			context.disableScissor();
		}
	}

	/**
	 * Marqueur le plus proche du curseur, ou {@code null}. Les coordonnées souris sont
	 * en espace écran (même référentiel que {@code viewX}/{@code viewY}).
	 */
	public MapMarker hitTest(double mouseX, double mouseY, String serverWorld,
							 double centerX, double centerZ, int zoom, int viewX, int viewY) {
		MapMarker best = null;
		double bestDist = HIT_RADIUS_PX;
		for (MapMarker marker : listVisibleMarkers(serverWorld)) {
			double sx = viewX + widthPx / 2.0 + (marker.x() - centerX) / zoom;
			double sy = viewY + heightPx / 2.0 + (marker.z() - centerZ) / zoom;
			double dist = Math.hypot(mouseX - sx, mouseY - sy);
			if (dist < bestDist) {
				bestDist = dist;
				best = marker;
			}
		}
		return best;
	}

	/** Marqueurs actuellement affichés (filtres de catégorie + cases individuelles). */
	public static List<MapMarker> listVisibleMarkers(String serverWorld) {
		List<MapMarker> out = new ArrayList<>();
		if (WaypointManager.isShowPublicWarps()) {
			for (ServerDataStore.Marker warp : ServerDataStore.markersIn(ServerDataStore.warps(), serverWorld)) {
				if (!WaypointManager.isMarkerEnabled(WaypointManager.warpKey(warp.name()))) continue;
				out.add(new MapMarker(MapMarker.Kind.WARP, warp.name(),
						warp.event() ? RGB_WARP_EVENT : RGB_WARP,
						warp.x(), warp.y(), warp.z(),
						"warp " + warp.name(), null, WaypointManager.warpKey(warp.name())));
			}
		}
		if (WaypointManager.isShowFactionSpawn()) {
			ServerDataStore.Marker fspawn = ServerDataStore.factionSpawn();
			if (fspawn != null && serverWorld.equals(fspawn.world())
					&& WaypointManager.isMarkerEnabled(WaypointManager.FACTION_SPAWN_KEY)) {
				out.add(new MapMarker(MapMarker.Kind.FACTION_SPAWN, fspawn.name(), RGB_FSPAWN,
						fspawn.x(), fspawn.y(), fspawn.z(),
						"f spawn", null, WaypointManager.FACTION_SPAWN_KEY));
			}
		}
		if (WaypointManager.isShowPrivateHomes()) {
			for (ServerDataStore.Marker home : ServerDataStore.markersIn(ServerDataStore.homes(), serverWorld)) {
				if (!WaypointManager.isMarkerEnabled(WaypointManager.homeKey(home.name()))) continue;
				out.add(new MapMarker(MapMarker.Kind.HOME, home.name(), RGB_HOME,
						home.x(), home.y(), home.z(),
						"home " + home.name(), null, WaypointManager.homeKey(home.name())));
			}
		}
		if (WaypointManager.isShowCustomWaypoints()) {
			for (WaypointManager.CustomWaypoint wp : WaypointManager.getCustomWaypointsInWorld(serverWorld)) {
				out.add(new MapMarker(MapMarker.Kind.WAYPOINT, wp.getName(), wp.getColor(),
						wp.getX(), wp.getY(), wp.getZ(),
						null, wp.getId(), wp.getId()));
			}
		}
		return out;
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
		if (!WaypointManager.isShowClaims()) return;
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

				int cw = x2 - x1;
				int ch = y2 - y1;
				int edge = (cw >= 6 && ch >= 6) ? 2 : 1;

				// Contours uniquement aux changements de zone (voisin différent ou non claimé)
				if (!rel.equals(worldClaims.get(ServerDataStore.pack(chunkX, chunkZ - 1)))) {
					context.fill(x1, y1, x2, y1 + edge, (CLAIM_EDGE_ALPHA << 24) | rgb);
				}
				if (!rel.equals(worldClaims.get(ServerDataStore.pack(chunkX, chunkZ + 1)))) {
					context.fill(x1, y2 - edge, x2, y2, (CLAIM_EDGE_ALPHA << 24) | rgb);
				}
				if (!rel.equals(worldClaims.get(ServerDataStore.pack(chunkX - 1, chunkZ)))) {
					context.fill(x1, y1, x1 + edge, y2, (CLAIM_EDGE_ALPHA << 24) | rgb);
				}
				if (!rel.equals(worldClaims.get(ServerDataStore.pack(chunkX + 1, chunkZ)))) {
					context.fill(x2 - edge, y1, x2, y2, (CLAIM_EDGE_ALPHA << 24) | rgb);
				}
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
							  double originX, double originZ, int zoom, boolean showLabels,
							  int hoverX, int hoverY) {
		List<MapMarker> markers = listVisibleMarkers(serverWorld);
		MapMarker hovered = null;
		double bestDist = HIT_RADIUS_PX;
		for (MapMarker marker : markers) {
			int sx = drawX + (int) Math.round((marker.x() - originX) / zoom);
			int sy = drawY + (int) Math.round((marker.z() - originZ) / zoom);
			double dist = Math.hypot(hoverX - sx, hoverY - sy);
			if (dist < bestDist) {
				bestDist = dist;
				hovered = marker;
			}
		}

		boolean labelsAlways = showLabels && zoom < LABEL_ZOOM_THRESHOLD;
		int iconR = showLabels ? 6 : 5;
		for (MapMarker marker : markers) {
			boolean thisHover = marker == hovered;
			String label = null;
			if (showLabels && (labelsAlways || thisHover)) {
				label = marker.name();
			}
			paintMarker(context, drawX, drawY, originX, originZ, zoom, marker, label, thisHover, iconR);
		}
	}

	private void paintMarker(DrawContext context, int drawX, int drawY, double originX, double originZ,
							 int zoom, MapMarker marker, String label, boolean hovered, int iconR) {
		int sx = drawX + (int) Math.round((marker.x() - originX) / zoom);
		int sy = drawY + (int) Math.round((marker.z() - originZ) / zoom);
		int margin = iconR + 4;
		if (sx < drawX - margin || sx >= drawX + widthPx + margin
				|| sy < drawY - margin || sy >= drawY + heightPx + margin) {
			return;
		}

		MarkerIcons.drawMarker(context, sx, sy, marker.kind(), marker.rgb(), hovered, iconR);

		if (label != null) {
			TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
			int color = 0xFF000000 | marker.rgb();
			int lw = textRenderer.getWidth(label);
			int lx = sx + iconR + 4;
			int ly = sy - 6;
			context.fill(lx - 3, ly - 1, lx + lw + 4, ly + 11, 0xE8101018);
			context.fill(lx - 3, ly - 1, lx - 2, ly + 11, color);
			context.drawTextWithShadow(textRenderer, label, lx, ly + 1, 0xFFF2F2FA);
		}
	}

	// ─── Joueur ──────────────────────────────────────────────────

	private void paintPlayer(DrawContext context, int drawX, int drawY, double originX, double originZ, int zoom) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		int sx = drawX + (int) Math.round((client.player.getX() - originX) / zoom);
		int sy = drawY + (int) Math.round((client.player.getZ() - originZ) / zoom);
		if (sx < drawX - 8 || sx >= drawX + widthPx + 8 || sy < drawY - 8 || sy >= drawY + heightPx + 8) return;

		// Yaw Minecraft : 0 = sud (+Z), 180 = nord (-Z) ; la carte affiche le nord en haut
		int radius = Math.max(6, Math.min(widthPx, heightPx) >= 80 ? 7 : 6);
		MarkerIcons.drawPlayer(context, sx, sy, client.player.getYaw(), radius);
	}

	/** Marqueur affiché sur la carte (hit-test, TP, labels). */
	public record MapMarker(
			Kind kind,
			String name,
			int rgb,
			double x,
			double y,
			double z,
			String command,
			String waypointId,
			String toggleKey
	) {
		public enum Kind {
			WARP, HOME, FACTION_SPAWN, WAYPOINT
		}

		public boolean canTeleport() {
			return command != null && !command.isEmpty();
		}

		public boolean isCustomWaypoint() {
			return kind == Kind.WAYPOINT && waypointId != null;
		}
	}
}
