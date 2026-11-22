package fr.cantale.client.ui.menu;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Vies : le nombre, les cœurs, et les deux actions qui changent vraiment quelque chose. */
public final class LifeScreen extends AbstractCantaleScreen {

	public LifeScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		int lives = Math.max(0, num("lives"));
		int panelW = Math.min(360, width - 16);
		int panelH = Math.min(228, height - 12);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		String figure = Integer.toString(lives);
		int tw = textRenderer.getWidth(figure) * 2;
		big(context, figure, x + (panelW - tw) / 2, y + 34, GuiKit.INK);
		GuiKit.text(context, textRenderer, lives > 1 ? "vies" : "vie", x + (panelW + tw) / 2 + 6, y + 42, GuiKit.MUTED);

		int shown = Math.min(lives, 10);
		int heartsW = shown * 16;
		hearts(context, x + (panelW - heartsW) / 2, y + 62, shown);
		if (lives > 10) {
			GuiKit.text(context, textRenderer, "+" + (lives - 10), x + (panelW + heartsW) / 2 + 4, y + 66, GuiKit.ROSE);
		}

		String rule = "Il en faut au moins deux pour en sacrifier ou en donner une.";
		drawCentered(context, clip(rule, panelW - 28), x, y + 84, panelW, 12, GuiKit.MUTED);

		Slot drop = find(11);
		Slot give = find(13);
		int bw = panelW - 48;
		int bx = x + 24;
		if (drop != null) {
			hitButton(context, bx, y + 104, bw, 20, GuiKit.plain(drop.name), drop.index, false, true, mouseX, mouseY);
		}
		if (give != null) {
			hitButton(context, bx, y + 128, bw, 20, GuiKit.plain(give.name), give.index, true, false, mouseX, mouseY);
		}
		drawCentered(context, "L'item Vie se prend en main, puis un clic droit.", x, y + 154, panelW, 12, GuiKit.MUTED);
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private void big(DrawContext context, String value, int x, int y, int color) {
		var matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(2f, 2f);
		GuiKit.text(context, textRenderer, value, 0, 0, color);
		matrices.popMatrix();
	}

	private void hearts(DrawContext context, int x, int y, int count) {
		int[] rows = {0b0110110, 0b1111111, 0b1111111, 0b0111110, 0b0011100, 0b0001000};
		for (int n = 0; n < count; n++) {
			int ox = x + n * 16;
			for (int row = 0; row < rows.length; row++) {
				for (int col = 0; col < 7; col++) {
					if ((rows[row] & (1 << (6 - col))) == 0) continue;
					context.fill(ox + col * 2, y + row * 2, ox + col * 2 + 2, y + row * 2 + 2, GuiKit.ROSE);
				}
			}
		}
	}
}
