package fr.cantale.client.ui.menu;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Ouverture d'une caisse : une case au centre, le serveur fait défiler la récompense. */
public final class CrateScreen extends AbstractCantaleScreen {

	public CrateScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		int panelW = 180;
		int panelH = 120;
		int x = (width - panelW) / 2;
		int y = (height - panelH) / 2;
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 12, panelW);
		GuiKit.drawSeparator(context, x + 16, y + 28, panelW - 32);
		Slot shown = null;
		for (Slot slot : slots) {
			if ("cell".equals(slot.role) || "pad".equals(slot.role)) continue;
			if (slot.index == 13 || shown == null) shown = slot;
		}
		int sx = x + (panelW - 18) / 2;
		int sy = y + 48;
		if (shown != null) {
			drawStack(context, shown, sx, sy, false);
			String name = GuiKit.plain(shown.name);
			if (!name.isBlank()) drawCentered(context, name, x + 8, sy + 24, panelW - 16, 16, GuiKit.INK);
		} else {
			GuiKit.drawSlot(context, sx, sy, false);
		}
		maxScroll = 0;
	}
}
