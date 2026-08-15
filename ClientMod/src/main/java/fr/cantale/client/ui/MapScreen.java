package fr.cantale.client.ui;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.data.ServerDataStore;
import fr.cantale.client.render.MapCanvas;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Écran carte plein écran (touche M) : terrain façon JourneyMap, pan au drag souris,
 * zoom à la molette, overlays serveur (claims colorés, warps, homes, /f spawn).
 * Centré sur le joueur à l'ouverture.
 */
public final class MapScreen extends Screen {

	/** Niveaux de zoom en blocs par pixel (1 = le plus proche). */
	private static final int[] ZOOM_LEVELS = {1, 2, 4, 8, 16};

	private MapCanvas canvas;
	private double centerX;
	private double centerZ;
	private int zoomIndex = 2; // 4 blocs/px par défaut
	private boolean dragging;

	public MapScreen() {
		super(Text.translatable("screen.cantale.map.title"));
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	@Override
	protected void init() {
		// L'écran est ré-initialisé à chaque redimensionnement : on recrée la texture à la bonne taille
		if (canvas != null) {
			canvas.dispose();
		}
		canvas = new MapCanvas("fullscreen_map", width, height);
	}

	@Override
	public void removed() {
		if (canvas != null) {
			canvas.dispose();
			canvas = null;
		}
	}

	// ─── Entrées ─────────────────────────────────────────────────

	@Override
	public boolean keyPressed(KeyInput input) {
		// M referme la carte en plus d'Échap
		if (input.key() == GLFW.GLFW_KEY_M) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging) {
			int zoom = ZOOM_LEVELS[zoomIndex];
			centerX -= offsetX * zoom;
			centerZ -= offsetY * zoom;
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount > 0 && zoomIndex > 0) {
			zoomIndex--; // molette haut : zoom avant
		} else if (verticalAmount < 0 && zoomIndex < ZOOM_LEVELS.length - 1) {
			zoomIndex++; // molette bas : zoom arrière
		}
		return true;
	}

	// ─── Rendu ───────────────────────────────────────────────────

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Fond sombre opaque (pas de flou vanilla : on ne passe pas par renderBackground)
		context.fill(0, 0, width, height, 0xFF14141C);

		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.world != null && canvas != null) {
			canvas.draw(context, 0, 0, client.world, CantaleMod.getServerWorldName(),
					centerX, centerZ, ZOOM_LEVELS[zoomIndex], true, true);
		}

		paintTopBar(context);
		paintLegend(context);
	}

	private void paintTopBar(DrawContext context) {
		context.fill(0, 0, width, 14, 0xCC14141C);
		String left = "Cantale — zoom " + ZOOM_LEVELS[zoomIndex] + " blocs/px — centre "
				+ (int) Math.round(centerX) + " / " + (int) Math.round(centerZ);
		String right = ServerDataStore.isServerSupportsMod() ? "Données serveur : oui" : "Terrain seul (pas de données serveur)";
		context.drawTextWithShadow(textRenderer, left, 4, 3, 0xFFE8E8F0);
		context.drawTextWithShadow(textRenderer, right, width - textRenderer.getWidth(right) - 4, 3, 0xFFA0A0B0);
	}

	private void paintLegend(DrawContext context) {
		if (!ServerDataStore.isServerSupportsMod()) return;
		// Légende des couleurs de claims, en bas à gauche
		String[][] entries = {
				{"OWN", "3FD23F"}, {"ALLY", "3FB4D2"}, {"WAR", "E69B3A"},
				{"ENEMY", "D23F3F"}, {"PASDIC", "B04FD8"}, {"OTHER", "8A8A96"},
		};
		int y = height - 14 * entries.length - 6;
		context.fill(0, y - 4, 78, height, 0x9914141C);
		for (String[] entry : entries) {
			int rgb = Integer.parseInt(entry[1], 16);
			context.fill(6, y + 1, 12, y + 7, 0xFF000000 | rgb);
			context.drawTextWithShadow(textRenderer, entry[0], 16, y, 0xFFD8D8E0);
			y += 14;
		}
	}

	@Override
	public boolean shouldPause() {
		return false; // La carte reste interactive en jeu (style JourneyMap)
	}
}
