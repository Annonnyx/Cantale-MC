package fr.cantale.client.ui;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.data.ServerDataStore;
import fr.cantale.client.render.ClaimColors;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * HUD de survie Cantale : cœurs de vies restantes, barre de combat tag lissée entre
 * deux paquets serveur, boussole de cap et territoire (faction / claim / wilderness).
 *
 * <p>Aucune lecture disque. Les textes dynamiques ne sont reconstruits que lorsque
 * la valeur affichée change (vies, seconde de combat, chunk ou index de factions).
 */
public final class StatusHud {

	private static final Identifier LAYER_ID = Identifier.of("cantale", "status");

	/** Silhouette 7×6, partagée par tous les cœurs (pas d'allocation au rendu). */
	private static final byte[] HEART = {
			0, 1, 1, 0, 1, 1, 0,
			1, 1, 1, 1, 1, 1, 1,
			1, 1, 1, 1, 1, 1, 1,
			0, 1, 1, 1, 1, 1, 0,
			0, 0, 1, 1, 1, 0, 0,
			0, 0, 0, 1, 0, 0, 0
	};
	private static final int HEART_W = 7;
	private static final int HEART_H = 6;
	private static final int HEART_STEP = 8;

	private static final String[] FACING = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private static final int HEART_FULL = 0xFFE23B4A;
	private static final int HEART_EMPTY = 0xFF4A2A32;
	private static final int HEART_UNLIMITED = 0xFFFFD24A;
	private static final int PANEL = 0xCC14141C;

	private static Text livesLine = Text.empty();
	private static int livesKey = Integer.MIN_VALUE;
	private static boolean livesUnlimited;

	private static Text combatLine = Text.empty();
	private static int combatSecond = -1;
	private static String combatOpponent = "";

	private static Text territoryLine = Text.empty();
	private static int territoryRgb = ClaimColors.WILDERNESS;
	private static long territoryPack = Long.MIN_VALUE;
	private static long territoryMeta = Long.MIN_VALUE;
	private static String territoryWorld = "";

	private StatusHud() {
	}

	public static void init() {
		HudElementRegistry.addLast(LAYER_ID, (context, tickCounter) -> render(context));
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		if (!ServerDataStore.isServerSupportsMod()) return;
		if (client.currentScreen != null) return;
		if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

		TextRenderer text = client.textRenderer;
		ServerDataStore.HudState hud = ServerDataStore.hud();

		int combatShownMs = 0;
		boolean showCombat = false;
		if (hud != null && hud.combatRemainingMs() > 0 && hud.combatMaxMs() > 0) {
			long elapsed = System.currentTimeMillis() - hud.receivedAtMs();
			combatShownMs = (int) Math.max(0L, hud.combatRemainingMs() - elapsed);
			if (combatShownMs > hud.combatMaxMs()) combatShownMs = hud.combatMaxMs();
			showCombat = combatShownMs > 0;
			if (showCombat) refreshCombatLine(combatShownMs, hud.opponent());
		}

		if (hud != null) refreshLivesLine(hud);

		String world = ServerDataStore.currentServerWorld(client.world);
		int chunkX = client.player.getBlockX() >> 4;
		int chunkZ = client.player.getBlockZ() >> 4;
		refreshTerritory(world, chunkX, chunkZ);

		int facing = facingIndex(client.player.getYaw());
		String facingLabel = FACING[facing];

		int pad = 4;
		int livesWidth = 0;
		if (hud != null) {
			int heartSlots = hud.unlimited() ? 1 : Math.max(1, Math.min(10, hud.maxLives()));
			livesWidth = heartSlots * HEART_STEP + 4 + text.getWidth(livesLine);
		}
		int combatWidth = showCombat ? Math.max(108, text.getWidth(combatLine)) : 0;
		int territoryWidth = 20 + text.getWidth(territoryLine);
		int contentWidth = Math.max(livesWidth, Math.max(combatWidth, territoryWidth));
		int panelW = contentWidth + pad * 2;
		int panelH = pad + (hud != null ? 10 : 0) + (showCombat ? 16 : 0) + 12 + pad;

		int x = 6;
		int y = 6;
		context.fill(x, y, x + panelW, y + panelH, PANEL);
		context.fill(x, y, x + 2, y + panelH, 0xFF000000 | territoryRgb);

		int cursor = y + pad;
		int left = x + pad + 2;
		if (hud != null) {
			drawLives(context, left, cursor, hud);
			int heartSlots = hud.unlimited() ? 1 : Math.max(1, Math.min(10, hud.maxLives()));
			context.drawTextWithShadow(text, livesLine, left + heartSlots * HEART_STEP + 4,
					cursor, 0xFFF2F2F6);
			cursor += 10;
		}
		if (showCombat && hud != null) {
			context.drawTextWithShadow(text, combatLine, left, cursor, 0xFFFFC2A8);
			int barY = cursor + 9;
			int fill = (int) ((long) combatShownMs * 108 / hud.combatMaxMs());
			if (fill < 1) fill = 1;
			if (fill > 108) fill = 108;
			context.fill(left, barY, left + 108, barY + 3, 0xFF2A1A1A);
			context.fill(left, barY, left + fill, barY + 3, combatColor(combatShownMs, hud.combatMaxMs()));
			cursor += 16;
		}

		int compassX = left;
		int compassY = cursor;
		context.fill(compassX, compassY, compassX + 16, compassY + 11, 0xFF000000 | territoryRgb);
		context.fill(compassX + 1, compassY + 1, compassX + 15, compassY + 10, 0xFF16161E);
		int facingWidth = text.getWidth(facingLabel);
		context.drawTextWithShadow(text, facingLabel, compassX + 8 - facingWidth / 2, compassY + 2, 0xFFFFFFFF);
		context.drawTextWithShadow(text, territoryLine, compassX + 20, compassY + 2, 0xFF000000 | territoryRgb);
	}

	private static void drawLives(DrawContext context, int x, int y, ServerDataStore.HudState hud) {
		if (hud.unlimited()) {
			drawHeart(context, x, y, HEART_UNLIMITED);
			return;
		}
		int slots = Math.max(1, Math.min(10, hud.maxLives()));
		int filled = Math.min(slots, Math.max(0, hud.lives()));
		for (int i = 0; i < slots; i++) {
			drawHeart(context, x + i * HEART_STEP, y, i < filled ? HEART_FULL : HEART_EMPTY);
		}
	}

	/** Cœur par courses horizontales : quelques {@code fill}, aucun tableau temporaire. */
	private static void drawHeart(DrawContext context, int x, int y, int argb) {
		for (int row = 0; row < HEART_H; row++) {
			int rowStart = row * HEART_W;
			int run = -1;
			for (int col = 0; col <= HEART_W; col++) {
				boolean on = col < HEART_W && HEART[rowStart + col] != 0;
				if (on && run < 0) run = col;
				if (!on && run >= 0) {
					context.fill(x + run, y + row, x + col, y + row + 1, argb);
					run = -1;
				}
			}
		}
	}

	private static void refreshLivesLine(ServerDataStore.HudState hud) {
		if (hud.unlimited()) {
			if (!livesUnlimited) {
				livesUnlimited = true;
				livesKey = Integer.MIN_VALUE;
				livesLine = Text.translatable("cantale.hud.lives_unlimited");
			}
			return;
		}
		if (livesUnlimited || livesKey != hud.lives()) {
			livesUnlimited = false;
			livesKey = hud.lives();
			String count = Integer.toString(hud.lives());
			livesLine = Text.translatable(hud.lives() == 1 ? "cantale.hud.lives_one" : "cantale.hud.lives_count", count);
		}
	}

	private static void refreshCombatLine(int shownMs, String opponent) {
		int second = (shownMs + 999) / 1000;
		String opp = opponent == null ? "" : opponent;
		if (second == combatSecond && opp.equals(combatOpponent)) return;
		combatSecond = second;
		combatOpponent = opp;
		String seconds = Integer.toString(second);
		combatLine = opp.isEmpty()
				? Text.translatable("cantale.hud.combat", seconds)
				: Text.translatable("cantale.hud.combat_opponent", seconds, opp);
	}

	private static void refreshTerritory(String world, int chunkX, int chunkZ) {
		long meta = ServerDataStore.metaVersion();
		long packed = ServerDataStore.pack(chunkX, chunkZ);
		if (packed == territoryPack && meta == territoryMeta && world.equals(territoryWorld)) return;
		territoryPack = packed;
		territoryMeta = meta;
		territoryWorld = world;

		ServerDataStore.ClaimCell cell = ServerDataStore.cellAt(world, chunkX, chunkZ);
		if (cell == null) {
			territoryRgb = ClaimColors.WILDERNESS;
			territoryLine = Text.translatable("cantale.territory.wilderness");
			return;
		}
		territoryRgb = ClaimColors.rgb(cell.rel());
		ServerDataStore.FactionCard card = ServerDataStore.faction(cell.factionId());
		String name = card != null ? card.name() : "";
		if ("OWN".equals(cell.rel())) {
			MutableText line = Text.translatable("cantale.territory.faction");
			if (!name.isEmpty()) line.append(Text.literal(" · " + name));
			territoryLine = line;
			return;
		}
		MutableText line = Text.translatable("cantale.territory.claim");
		if (!name.isEmpty()) line.append(Text.literal(" · " + name));
		line.append(Text.literal(" · ")).append(Text.translatable(ClaimColors.translationKey(cell.rel())));
		territoryLine = line;
	}

	private static int combatColor(int shownMs, int maxMs) {
		int ratio = (int) Math.min(255L, shownMs * 255L / maxMs);
		int green = 36 + ratio * 130 / 255;
		return 0xFF000000 | (220 << 16) | (green << 8) | 0x28;
	}

	/** Même découpage 8 directions que la flèche de la carte (nord en haut). */
	private static int facingIndex(float yaw) {
		return (int) Math.floor(((yaw % 360 + 360 + 180 + 22.5) % 360) / 45.0) & 7;
	}
}
