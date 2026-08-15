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
	private static final int MARGIN = 6;
	/** Zoom fixe : 4 blocs par pixel. */
	private static final int ZOOM = 4;

	private static final Identifier LAYER_ID = Identifier.of("cantale", "minimap");

	private static volatile boolean enabled;
	private static MapCanvas canvas;

	private MinimapHud() {
	}

	public static void init() {
		HudElementRegistry.addLast(LAYER_ID, (context, tickCounter) -> {
			if (!enabled) return;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;
			if (client.currentScreen instanceof MapScreen) return; // plein écran ouvert : pas de doublon
			if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return; // F3 visible : on s'efface

			if (canvas == null) {
				canvas = new MapCanvas("minimap", SIZE, SIZE);
			}

			int x = context.getScaledWindowWidth() - SIZE - MARGIN;
			int y = MARGIN;

			// Cadre sombre + bordure claire
			context.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xCC14141C);
			canvas.draw(context, x, y, client.world, CantaleMod.getServerWorldName(),
					client.player.getX(), client.player.getZ(), ZOOM, false, true);

			String coords = (int) client.player.getX() + " " + (int) client.player.getZ();
			int textWidth = client.textRenderer.getWidth(coords);
			context.fill(x + SIZE / 2 - textWidth / 2 - 2, y + SIZE + 3,
					x + SIZE / 2 + textWidth / 2 + 2, y + SIZE + 13, 0xCC14141C);
			context.drawTextWithShadow(client.textRenderer, coords,
					x + SIZE / 2 - textWidth / 2, y + SIZE + 5, 0xFFE8E8F0);
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
