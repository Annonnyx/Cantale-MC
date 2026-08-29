package fr.cantale.client.ui;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.render.MapCanvas;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Minimap HUD en haut à droite (touche N pour basculer) : même rendu que l'écran carte,
 * centré sur le joueur, zoom fixe. Désactivée quand l'écran carte plein format est ouvert
 * ou quand le F3 (écran de debug) est affiché.
 */
public final class MinimapHud {

	/** Taille de la minimap en pixels (gui-scaled). */
	private static final int SIZE = 120;
	/** Marge par rapport au bord de l'écran. */
	private static final int MARGIN = 8;
	/** Zoom fixe : 4 blocs par pixel. */
	private static final int ZOOM = 4;

	private static final Identifier LAYER_ID = Identifier.of("cantale", "minimap");

	private static volatile boolean enabled = true;
	private static MapCanvas canvas;

	private MinimapHud() {
	}

	public static void init() {
		HudElementRegistry.addLast(LAYER_ID, (context, tickCounter) -> {
			if (!enabled) return;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;
			if (client.currentScreen instanceof MapScreen) return;
			if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;
			if (client.options.hudHidden) return;

			if (canvas == null) {
				canvas = new MapCanvas("minimap", SIZE, SIZE);
			}

			int x = context.getScaledWindowWidth() - SIZE - MARGIN;
			int y = MARGIN + 10;

			context.fill(x - 4, y - 4, x + SIZE + 4, y + SIZE + 4, 0xFF08080E);
			context.fill(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xFFC9A44A);
			context.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xFF2A2418);
			context.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFF12121A);

			canvas.draw(context, x, y, client.world, CantaleMod.getServerWorldName(),
					client.player.getX(), client.player.getZ(), ZOOM, false, true);

			var tr = client.textRenderer;
			int nW = tr.getWidth("N");
			context.drawTextWithShadow(tr, "N", x + SIZE / 2 - nW / 2, y - 10, 0xFFE53935);

			String coords = (int) client.player.getX() + " " + (int) client.player.getZ();
			int textWidth = tr.getWidth(coords);
			int cy = y + SIZE + 13;
			context.fill(x + SIZE / 2 - textWidth / 2 - 3, cy,
					x + SIZE / 2 + textWidth / 2 + 3, cy + 11, 0xCC101018);
			context.drawTextWithShadow(tr, coords,
					x + SIZE / 2 - textWidth / 2, cy + 2, 0xFFE8E8F0);
		});
	}

	/** Bascule l'affichage de la minimap (touche N). */
	public static void toggle() {
		enabled = !enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}
}
