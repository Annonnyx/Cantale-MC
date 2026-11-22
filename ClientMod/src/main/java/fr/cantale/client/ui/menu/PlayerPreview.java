package fr.cantale.client.ui.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/** Aperçu du joueur dans un menu. Le skin n'existe sur le client que s'il est chargé. */
final class PlayerPreview {

	private static boolean failed;

	private PlayerPreview() {
	}

	static boolean draw(DrawContext context, MinecraftClient client, int x1, int y1, int x2, int y2,
			int mouseX, int mouseY, String uuid, boolean self) {
		if (failed || client == null) return false;
		LivingEntity entity = null;
		if (self && client.player != null) {
			entity = client.player;
		} else if (uuid != null && !uuid.isBlank() && client.world != null) {
			for (var player : client.world.getPlayers()) {
				if (uuid.equals(String.valueOf(player.getUuid()))) {
					entity = player;
					break;
				}
			}
		}
		if (entity == null) return false;
		try {
			InventoryScreen.drawEntity(context, x1, y1, x2, y2, 36, 0.0625f, mouseX, mouseY, entity);
			return true;
		} catch (RuntimeException e) {
			failed = true;
			return false;
		}
	}

	static void item(DrawContext context, ItemStack stack, int x, int y, float scale) {
		if (stack == null || stack.isEmpty()) return;
		var matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		context.drawItem(stack, 0, 0);
		matrices.popMatrix();
	}

	static int rankFill(String id) {
		return switch (id == null ? "" : id) {
			case "AVENTURIER" -> 0xFF8FB4CC;
			case "VIP" -> 0xFF9BBF8A;
			case "CHEVRE" -> 0xFFE2C56A;
			case "MODERATOR" -> 0xFF5C6EAE;
			case "ADMIN" -> 0xFFC2455A;
			case "OWNER" -> 0xFF8E2436;
			default -> 0xFFD5D8D4;
		};
	}

	static int rankText(String id) {
		return switch (id == null ? "" : id) {
			case "MODERATOR", "ADMIN", "OWNER" -> 0xFFF7F8F6;
			default -> 0xFF2B2F4A;
		};
	}
}
