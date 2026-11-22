package fr.cantale.client.ui.menu;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Dépôt de récolte : deux boutons, pas un coffre où poser des items. */
public final class ActionScreen extends AbstractCantaleScreen {

	public ActionScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		int panelW = 280;
		int panelH = 48 + actions.size() * 28 + (hint.isBlank() ? 16 : 32);
		int x = (width - panelW) / 2;
		int y = (height - panelH) / 2;
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 12, panelW);
		GuiKit.drawSeparator(context, x + 16, y + 28, panelW - 32);
		int by = y + 40;
		if (!hint.isBlank()) {
			drawCentered(context, hint, x + 8, by, panelW - 16, 16, GuiKit.MUTED);
			by += 18;
		}
		for (Action action : actions) {
			int bx = x + 24;
			int bw = panelW - 48;
			boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 20;
			GuiKit.drawButton(context, bx, by, bw, 20, action.primary, hover);
			int color = action.primary ? 0xFFFFFFFF : GuiKit.INK;
			drawCentered(context, action.label, bx, by, bw, 20, color);
			hits.add(new Hit(bx, by, bw, 20, -1, action.id));
			by += 26;
		}
		maxScroll = 0;
	}
}
