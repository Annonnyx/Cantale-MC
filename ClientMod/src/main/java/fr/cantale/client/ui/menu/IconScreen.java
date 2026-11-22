package fr.cantale.client.ui.menu;

import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Shop, hôtel des ventes, sélections : cases posées, le clic est une intention. */
public final class IconScreen extends AbstractCantaleScreen {

	public IconScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> items = flowSlots();
		boolean labeled = items.size() <= 14;
		int cell = labeled ? 54 : 22;
		int gap = 4;
		int columns = labeled ? Math.min(4, Math.max(1, items.size())) : Math.min(9, Math.max(1, items.size()));
		if (!labeled) columns = Math.min(9, Math.max(1, (width - 48) / (cell + gap)));
		int rows = Math.max(1, (items.size() + columns - 1) / columns);
		int gridW = columns * cell + (columns - 1) * gap;
		int contentH = rows * (cell + gap);
		int viewH = Math.min(contentH, Math.max(cell, height - 78));
		int panelW = Math.max(220, gridW + 28);
		int panelH = 40 + viewH + 30;
		int x = (width - panelW) / 2;
		int y = Math.max(12, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 10, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 24, panelW - 24);

		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.min(scroll, maxScroll);
		int left = x + (panelW - gridW) / 2;
		int top = y + 30;
		context.enableScissor(x + 6, top, x + panelW - 6, top + viewH);
		try {
			for (int i = 0; i < items.size(); i++) {
				Slot slot = items.get(i);
				int col = i % columns;
				int row = i / columns;
				int bx = left + col * (cell + gap);
				int by = top + row * (cell + gap) - scroll;
				if (by + cell < top || by > top + viewH) continue;
				boolean hover = mouseX >= bx && mouseX < bx + cell && mouseY >= by && mouseY < by + cell;
				if (labeled) {
					GuiKit.drawButton(context, bx, by, cell, cell - 4, false, hover);
					drawStack(context, slot, bx + (cell - 18) / 2, by + 4, false);
					drawCentered(context, GuiKit.plain(slot.name), bx, by + 24, cell, 16, GuiKit.INK);
					hits.add(new Hit(bx, by, cell, cell - 4, slot.index, null));
				} else {
					drawStack(context, slot, bx, by, hover);
					hits.add(new Hit(bx, by, 18, 18, slot.index, null));
				}
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
