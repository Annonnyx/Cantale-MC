package fr.cantale.client.ui.menu;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Statistiques : le joueur d'un côté, les chiffres de l'autre. */
public final class StatsScreen extends AbstractCantaleScreen {

	public StatsScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		int panelW = Math.min(440, width - 16);
		int panelH = Math.min(214, height - 12);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		int left = x + 12;
		int top = y + 32;
		int leftW = 112;
		int cardH = panelH - 64;
		GuiKit.drawButton(context, left, top, leftW, cardH, false, false);
		String rank = str("rank");
		if (rank.isBlank()) rank = "Sans grade";
		int chipW = Math.min(leftW - 12, textRenderer.getWidth(rank) + 12);
		int chipX = left + (leftW - chipW) / 2;
		context.fill(chipX, top + 6, chipX + chipW, top + 18, PlayerPreview.rankFill(str("rankId")));
		drawCentered(context, rank, chipX, top + 6, chipW, 12, PlayerPreview.rankText(str("rankId")));
		boolean drawn = PlayerPreview.draw(context, client, left + 8, top + 24, left + leftW - 8, top + cardH - 16,
				mouseX, mouseY, str("uuid"), true);
		if (!drawn) {
			Slot head = find(4);
			ItemStack stack = head == null ? null : GuiKit.stack(head.item, 1);
			PlayerPreview.item(context, stack, left + (leftW - 32) / 2, top + 36, 2f);
		}

		int right = left + leftW + 10;
		int rightW = x + panelW - 12 - right;
		int col = (rightW - 6) / 2;
		number(context, right, top, col, 46, "Kills", Integer.toString(num("kills")), true);
		number(context, right + col + 6, top, col, 46, "Morts", Integer.toString(num("deaths")), true);
		number(context, right, top + 52, col, 46, "Série", Integer.toString(num("streak")), true);
		number(context, right + col + 6, top + 52, col, 46, "K/D", str("kdr").isBlank() ? "0" : str("kdr"), true);
		number(context, right, top + 104, rightW, 36, "Temps de jeu", str("playtime").isBlank() ? "0s" : str("playtime"), false);
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private void number(DrawContext context, int x, int y, int w, int h, String label, String value, boolean large) {
		GuiKit.drawButton(context, x, y, w, h, false, false);
		GuiKit.text(context, textRenderer, label, x + 8, y + 6, GuiKit.MUTED);
		if (!large) {
			GuiKit.text(context, textRenderer, clip(value, w - 16), x + 8, y + 18, GuiKit.INK);
			return;
		}
		var matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x + 8, y + 20);
		matrices.scale(1.6f, 1.6f);
		GuiKit.text(context, textRenderer, value, 0, 0, GuiKit.INK);
		matrices.popMatrix();
	}
}
