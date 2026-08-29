package fr.cantale.client.render;

import java.util.Arrays;

import net.minecraft.client.gui.DrawContext;

/**
 * Icônes vectorielles (polygones remplis) pour la carte, la minimap et le HUD in-world.
 * Pas de bitmap 5×5 ni de lettre dans un carré : losange, maison, étoile, drapeau,
 * chevron joueur orienté au yaw.
 */
public final class MarkerIcons {

	public enum Shape {
		DIAMOND, HOUSE, STAR, FLAG, CHEVRON
	}

	private MarkerIcons() {
	}

	public static Shape shapeOf(MapCanvas.MapMarker.Kind kind) {
		return switch (kind) {
			case WAYPOINT -> Shape.DIAMOND;
			case HOME -> Shape.HOUSE;
			case WARP -> Shape.STAR;
			case FACTION_SPAWN -> Shape.FLAG;
		};
	}

	public static void drawMarker(DrawContext context, int cx, int cy, MapCanvas.MapMarker.Kind kind,
								  int rgb, boolean hovered, int radius) {
		draw(context, cx, cy, shapeOf(kind), rgb, hovered, radius);
	}

	public static void draw(DrawContext context, int cx, int cy, Shape shape, int rgb, boolean hovered, int radius) {
		int r = Math.max(4, radius);
		if (hovered) {
			fillCircle(context, cx, cy, r + 3, 0x55FFFFFF);
		}
		int fill = 0xFF000000 | (rgb & 0xFFFFFF);
		int outline = 0xFF0A0A10;
		int shadow = 0x99000000;
		switch (shape) {
			case DIAMOND -> drawDiamond(context, cx, cy, r, fill, outline, shadow);
			case HOUSE -> drawHouse(context, cx, cy, r, fill, outline, shadow);
			case STAR -> drawStar(context, cx, cy, r, fill, outline, shadow);
			case FLAG -> drawFlag(context, cx, cy, r, fill, outline, shadow);
			case CHEVRON -> drawChevron(context, cx, cy, 0f, r, 0xFFFFF3C4, outline, shadow);
		}
	}

	/**
	 * Triangle / chevron du joueur : yaw Minecraft (0 = sud / +Z, 90 = ouest),
	 * carte nord en haut. Contour noir, fill or/blanc, pointe nette.
	 */
	public static void drawPlayer(DrawContext context, int cx, int cy, float yaw, int radius) {
		int r = Math.max(6, radius);
		fillCircle(context, cx, cy, r + 2, 0x66101018);
		drawChevron(context, cx, cy, yaw, r, 0xFFFFF3C4, 0xFF0A0A10, 0xAA000000);
	}

	private static void drawDiamond(DrawContext context, int cx, int cy, int r, int fill, int outline, int shadow) {
		int[] xs = {cx, cx + r, cx, cx - r};
		int[] ys = {cy - r, cy, cy + r, cy};
		fillOutlined(context, xs, ys, fill, outline, shadow);
	}

	private static void drawHouse(DrawContext context, int cx, int cy, int r, int fill, int outline, int shadow) {
		int bodyTop = cy - r / 5;
		int[] roofX = {cx, cx + r + 1, cx - r - 1};
		int[] roofY = {cy - r - 1, bodyTop, bodyTop};
		int[] bodyX = {cx - r + 1, cx + r - 1, cx + r - 1, cx - r + 1};
		int[] bodyY = {bodyTop, bodyTop, cy + r, cy + r};
		offsetFill(context, bodyX, bodyY, 1, 1, shadow);
		fillOutlined(context, roofX, roofY, fill, outline, shadow);
		fillPolygon(context, bodyX, bodyY, fill);
		strokePolygon(context, bodyX, bodyY, outline);
		int doorW = Math.max(2, r / 3);
		context.fill(cx - doorW / 2, cy + r - Math.max(3, r * 2 / 3), cx + (doorW + 1) / 2, cy + r, 0xFF1A1A22);
		context.fill(cx - 1, cy - r / 3, cx + 1, cy, 0x66FFFFFF);
	}

	private static void drawStar(DrawContext context, int cx, int cy, int r, int fill, int outline, int shadow) {
		int[] xs = new int[10];
		int[] ys = new int[10];
		double inner = r * 0.42;
		for (int i = 0; i < 10; i++) {
			double a = Math.toRadians(-90 + i * 36);
			double rad = (i % 2 == 0) ? r : inner;
			xs[i] = cx + (int) Math.round(Math.cos(a) * rad);
			ys[i] = cy + (int) Math.round(Math.sin(a) * rad);
		}
		fillOutlined(context, xs, ys, fill, outline, shadow);
	}

	private static void drawFlag(DrawContext context, int cx, int cy, int r, int fill, int outline, int shadow) {
		int poleX = cx - r / 2;
		int top = cy - r;
		int bot = cy + r;
		context.fill(poleX + 1, top + 1, poleX + 3, bot + 1, shadow);
		int[] fx = {poleX + 2, cx + r + 1, poleX + 2};
		int[] fy = {top, top + r / 2 + 1, top + r + 1};
		fillOutlined(context, fx, fy, fill, outline, shadow);
		context.fill(poleX, top, poleX + 2, bot, 0xFF14141C);
	}

	private static void drawChevron(DrawContext context, int cx, int cy, float yaw, int r,
									int fill, int outline, int shadow) {
		double fx = -Math.sin(Math.toRadians(yaw));
		double fy = Math.cos(Math.toRadians(yaw));
		double px = -fy;
		double py = fx;
		int[] xs = new int[4];
		int[] ys = new int[4];
		point(xs, ys, 0, cx, cy, fx, fy, px, py, r * 1.05, 0);
		point(xs, ys, 1, cx, cy, fx, fy, px, py, -r * 0.62, r * 0.72);
		point(xs, ys, 2, cx, cy, fx, fy, px, py, -r * 0.18, 0);
		point(xs, ys, 3, cx, cy, fx, fy, px, py, -r * 0.62, -r * 0.72);
		fillOutlined(context, xs, ys, fill, outline, shadow);
		int[] innerX = new int[3];
		int[] innerY = new int[3];
		point(innerX, innerY, 0, cx, cy, fx, fy, px, py, r * 0.55, 0);
		point(innerX, innerY, 1, cx, cy, fx, fy, px, py, -r * 0.08, r * 0.28);
		point(innerX, innerY, 2, cx, cy, fx, fy, px, py, -r * 0.08, -r * 0.28);
		fillPolygon(context, innerX, innerY, 0xFFFFFFFF);
	}

	private static void point(int[] xs, int[] ys, int i, int cx, int cy,
							  double fx, double fy, double px, double py, double along, double side) {
		xs[i] = (int) Math.round(cx + fx * along + px * side);
		ys[i] = (int) Math.round(cy + fy * along + py * side);
	}

	private static void fillOutlined(DrawContext context, int[] xs, int[] ys, int fill, int outline, int shadow) {
		offsetFill(context, xs, ys, 1, 1, shadow);
		int[] ox = xs.clone();
		int[] oy = ys.clone();
		expandFromCentroid(ox, oy, 1);
		fillPolygon(context, ox, oy, outline);
		fillPolygon(context, xs, ys, fill);
	}

	private static void offsetFill(DrawContext context, int[] xs, int[] ys, int dx, int dy, int color) {
		int[] nx = new int[xs.length];
		int[] ny = new int[ys.length];
		for (int i = 0; i < xs.length; i++) {
			nx[i] = xs[i] + dx;
			ny[i] = ys[i] + dy;
		}
		fillPolygon(context, nx, ny, color);
	}

	private static void expandFromCentroid(int[] xs, int[] ys, int amount) {
		double cx = 0, cy = 0;
		for (int i = 0; i < xs.length; i++) {
			cx += xs[i];
			cy += ys[i];
		}
		cx /= xs.length;
		cy /= ys.length;
		for (int i = 0; i < xs.length; i++) {
			double dx = xs[i] - cx;
			double dy = ys[i] - cy;
			double len = Math.hypot(dx, dy);
			if (len < 0.001) continue;
			xs[i] += (int) Math.round(dx / len * amount);
			ys[i] += (int) Math.round(dy / len * amount);
		}
	}

	private static void strokePolygon(DrawContext context, int[] xs, int[] ys, int color) {
		int n = xs.length;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			drawLine(context, xs[i], ys[i], xs[j], ys[j], color);
		}
	}

	private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0;
		int y = y0;
		while (true) {
			context.fill(x, y, x + 1, y + 1, color);
			if (x == x1 && y == y1) break;
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
	}

	private static void fillCircle(DrawContext context, int cx, int cy, int r, int color) {
		for (int y = -r; y <= r; y++) {
			int span = (int) Math.round(Math.sqrt(r * r - y * y));
			context.fill(cx - span, cy + y, cx + span + 1, cy + y + 1, color);
		}
	}

	/** Scanline fill, polygone simple. */
	static void fillPolygon(DrawContext context, int[] xs, int[] ys, int color) {
		int n = xs.length;
		if (n < 3) return;
		int minY = ys[0];
		int maxY = ys[0];
		for (int i = 1; i < n; i++) {
			if (ys[i] < minY) minY = ys[i];
			if (ys[i] > maxY) maxY = ys[i];
		}
		int[] nodes = new int[n];
		for (int y = minY; y <= maxY; y++) {
			int count = 0;
			int j = n - 1;
			for (int i = 0; i < n; i++) {
				int yi = ys[i];
				int yj = ys[j];
				if ((yi < y && yj >= y) || (yj < y && yi >= y)) {
					int dy = yj - yi;
					nodes[count++] = xs[i] + (int) Math.round((double) (y - yi) * (xs[j] - xs[i]) / (dy == 0 ? 1 : dy));
				}
				j = i;
			}
			Arrays.sort(nodes, 0, count);
			for (int i = 0; i + 1 < count; i += 2) {
				int x1 = nodes[i];
				int x2 = nodes[i + 1];
				if (x2 < x1) {
					int tmp = x1;
					x1 = x2;
					x2 = tmp;
				}
				if (x2 >= x1) {
					context.fill(x1, y, x2 + 1, y + 1, color);
				}
			}
		}
	}
}
