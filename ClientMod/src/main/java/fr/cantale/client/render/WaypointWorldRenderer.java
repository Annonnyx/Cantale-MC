package fr.cantale.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.data.WaypointManager;
import fr.cantale.client.ui.MapScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Waypoints in-world façon JourneyMap :
 * <ul>
 *   <li>beam / colonne verticale translucide (WorldRenderEvents 1.21.11) ;</li>
 *   <li>HUD icône + nom + distance, clamp au bord si hors FOV (HudElementRegistry).</li>
 * </ul>
 * Respecte les filtres de catégorie, les cases individuelles, et le toggle
 * « Beams / In-world ».
 */
public final class WaypointWorldRenderer {

	private static final Identifier HUD_LAYER = Identifier.of("cantale", "waypoint_hud");
	private static final float BEAM_HALF = 0.22f;
	private static final float BEAM_TOP = 256f;
	private static final double MAX_BEAM_DIST = 512;
	private static final int HUD_MARGIN = 18;
	private static final int HUD_MAX = 32;
	private static final int LABEL_CLEARANCE_PX = 22;

	private static RenderLayer beamLayer;

	private WaypointWorldRenderer() {
	}

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WaypointWorldRenderer::drawBeams);
		HudElementRegistry.addLast(HUD_LAYER, (context, tickCounter) -> drawHud(context));
	}

	private static RenderLayer beamLayer() {
		if (beamLayer == null) {
			RenderPipeline pipeline = RenderPipelines.register(
					RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
							.withLocation(Identifier.of("cantale", "pipeline/waypoint_beam"))
							.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
							.withCull(false)
							.build());
			beamLayer = RenderLayer.of("cantale_waypoint_beam",
					RenderSetup.builder(pipeline).translucent().expectedBufferSize(65536).build());
		}
		return beamLayer;
	}

	private static void drawBeams(WorldRenderContext context) {
		if (!WaypointManager.isShowInWorld()) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		if (client.currentScreen instanceof MapScreen) return;

		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) return;
		Vec3d cam = context.worldState().cameraRenderState.pos;

		VertexConsumer buffer;
		try {
			buffer = consumers.getBuffer(beamLayer());
		} catch (Exception e) {
			CantaleMod.LOGGER.debug("[Cantale] Beam layer indisponible : {}", e.toString());
			return;
		}

		List<MapCanvas.MapMarker> markers = MapCanvas.listVisibleMarkers(CantaleMod.getServerWorldName());
		for (MapCanvas.MapMarker marker : markers) {
			double dx = marker.x() - cam.x;
			double dy = marker.y() - cam.y;
			double dz = marker.z() - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > MAX_BEAM_DIST) continue;
			float fade = (float) MathHelper.clamp(1.0 - dist / MAX_BEAM_DIST, 0.12, 0.55);
			int rgb = marker.rgb();
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			int aBase = (int) (fade * 180);
			int aTop = Math.max(12, aBase / 5);
			float x = (float) dx;
			float y0 = (float) dy;
			float z = (float) dz;
			float y1 = y0 + BEAM_TOP - (float) marker.y();
			if (y1 < y0 + 8) y1 = y0 + 64;
			emitCrossBeam(buffer, x, y0, z, y1, BEAM_HALF, r, g, b, aBase, aTop);
		}
	}

	private static void emitCrossBeam(VertexConsumer buffer, float x, float y0, float z, float y1,
									  float half, int r, int g, int b, int a0, int a1) {
		quad(buffer, x - half, y0, z, x + half, y0, z, x + half, y1, z, x - half, y1, z, r, g, b, a0, a1);
		quad(buffer, x, y0, z - half, x, y0, z + half, x, y1, z + half, x, y1, z - half, r, g, b, a0, a1);
	}

	private static void quad(VertexConsumer buffer,
							 float x1, float y1, float z1,
							 float x2, float y2, float z2,
							 float x3, float y3, float z3,
							 float x4, float y4, float z4,
							 int r, int g, int b, int aBottom, int aTop) {
		buffer.vertex(x1, y1, z1).color(r, g, b, aBottom);
		buffer.vertex(x2, y2, z2).color(r, g, b, aBottom);
		buffer.vertex(x3, y3, z3).color(r, g, b, aTop);
		buffer.vertex(x4, y4, z4).color(r, g, b, aTop);
	}

	private static void drawHud(DrawContext context) {
		if (!WaypointManager.isShowInWorld()) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.gameRenderer == null) return;
		if (client.currentScreen instanceof MapScreen) return;
		if (client.options.hudHidden) return;
		if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();
		Vec3d eye = client.player.getEyePos();
		List<HudMark> marks = new ArrayList<>();
		for (MapCanvas.MapMarker marker : MapCanvas.listVisibleMarkers(CantaleMod.getServerWorldName())) {
			Vec3d world = new Vec3d(marker.x() + 0.5, marker.y() + 1.2, marker.z() + 0.5);
			double dist = eye.distanceTo(world);
			if (dist < 1.5) continue;
			Projected p = project(client, world, sw, sh);
			if (p == null) continue;
			marks.add(new HudMark(marker, p, dist));
		}
		marks.sort(Comparator.comparingDouble(HudMark::dist));
		if (marks.size() > HUD_MAX) {
			marks = new ArrayList<>(marks.subList(0, HUD_MAX));
		}

		TextRenderer tr = client.textRenderer;
		List<float[]> occupied = new ArrayList<>();
		for (HudMark mark : marks) {
			int ix = Math.round(mark.proj.x);
			int iy = Math.round(mark.proj.y);
			MarkerIcons.drawMarker(context, ix, iy, mark.marker.kind(), mark.marker.rgb(), false,
					mark.proj.onScreen ? 6 : 5);

			if (!mark.proj.onScreen) continue;
			boolean overlap = false;
			for (float[] box : occupied) {
				if (Math.hypot(ix - box[0], iy - box[1]) < LABEL_CLEARANCE_PX) {
					overlap = true;
					break;
				}
			}
			if (overlap) continue;
			occupied.add(new float[]{ix, iy});

			String name = mark.marker.name();
			String distText = ((int) Math.round(mark.dist)) + " m";
			int nameW = tr.getWidth(name);
			int distW = tr.getWidth(distText);
			int boxW = Math.max(nameW, distW) + 10;
			int boxH = 22;
			int bx = ix + 8;
			int by = iy - 10;
			if (bx + boxW > sw - 4) bx = ix - boxW - 8;
			if (by < 4) by = iy + 8;
			if (by + boxH > sh - 4) by = sh - boxH - 4;
			context.fill(bx, by, bx + boxW, by + boxH, 0xE0101018);
			context.fill(bx, by, bx + 2, by + boxH, 0xFF000000 | mark.marker.rgb());
			context.drawTextWithShadow(tr, name, bx + 5, by + 2, 0xFF000000 | mark.marker.rgb());
			context.drawTextWithShadow(tr, distText, bx + 5, by + 12, 0xFFC8C8D4);
		}
	}

	private static Projected project(MinecraftClient client, Vec3d world, int sw, int sh) {
		Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
		Vec3d rel = world.subtract(cam);
		Quaternionf inv = client.gameRenderer.getCamera().getRotation().conjugate(new Quaternionf());
		Vector3f view = inv.transform(rel.toVector3f());
		boolean behind = view.z >= 0.0f;

		Vec3d ndc = client.gameRenderer.project(world);
		double ndcX = ndc.x;
		double ndcY = ndc.y;
		if (behind) {
			ndcX = -ndcX;
			ndcY = -ndcY;
		}
		double sx = (ndcX * 0.5 + 0.5) * sw;
		double sy = (1.0 - (ndcY * 0.5 + 0.5)) * sh;
		boolean onScreen = !behind
				&& sx >= HUD_MARGIN && sx < sw - HUD_MARGIN
				&& sy >= HUD_MARGIN && sy < sh - HUD_MARGIN;
		if (!onScreen) {
			float[] clamped = clampToEdge(sx, sy, sw, sh, HUD_MARGIN);
			sx = clamped[0];
			sy = clamped[1];
		}
		return new Projected((float) sx, (float) sy, onScreen);
	}

	private static float[] clampToEdge(double sx, double sy, int sw, int sh, int margin) {
		double cx = sw / 2.0;
		double cy = sh / 2.0;
		double dx = sx - cx;
		double dy = sy - cy;
		if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
			return new float[]{(float) cx, (float) margin};
		}
		double left = margin;
		double right = sw - margin;
		double top = margin;
		double bottom = sh - margin;
		double tx = dx > 0 ? (right - cx) / dx : (left - cx) / dx;
		double ty = dy > 0 ? (bottom - cy) / dy : (top - cy) / dy;
		if (dx == 0) tx = Double.POSITIVE_INFINITY;
		if (dy == 0) ty = Double.POSITIVE_INFINITY;
		double t = Math.min(tx, ty);
		if (t < 0) t = Math.max(tx, ty);
		return new float[]{(float) (cx + dx * t), (float) (cy + dy * t)};
	}

	private record Projected(float x, float y, boolean onScreen) {
	}

	private record HudMark(MapCanvas.MapMarker marker, Projected proj, double dist) {
	}
}
