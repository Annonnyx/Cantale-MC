package fr.cantale.client.ui.menu;

import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Menu principal : carte compacte, boutons unis, deux ou trois colonnes. */
public final class MainMenuScreen extends AbstractCantaleScreen {

	public MainMenuScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> items = flowSlots();
		int cols = items.size() > 12 && width >= 460 ? 3 : 2;
		int bw = cols == 3 ? 142 : 164;
		int bh = 20;
		int gap = 4;
		int rows = Math.max(1, (items.size() + cols - 1) / cols);
		int panelW = 20 + cols * bw + (cols - 1) * gap;
		int rowStep = bh + gap;
		int contentH = rows * rowStep;
		int maxView = Math.max(rowStep, height - 78);
		int viewH = Math.min(contentH, maxView);
		int panelH = 40 + viewH + 30;
		int x = (width - panelW) / 2;
		int y = Math.max(8, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 10, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 24, panelW - 24);

		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.min(scroll, maxScroll);
		int left = x + 10;
		int top = y + 30;
		context.enableScissor(x + 6, top, x + panelW - 6, top + viewH);
		try {
			for (int i = 0; i < items.size(); i++) {
				Slot slot = items.get(i);
				int col = i % cols;
				int row = i / cols;
				int bx = left + col * (bw + gap);
				int by = top + row * rowStep - scroll;
				if (by + bh < top || by > top + viewH) continue;
				boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
				GuiKit.drawButton(context, bx, by, bw, bh, false, hover);
				drawIcon(context, slot, bx + 4, by + 2);
				GuiKit.text(context, textRenderer, clip(GuiKit.plain(slot.name), bw - 28),
						bx + 24, by + 6, GuiKit.INK);
				hits.add(new Hit(bx, by, bw, bh, slot.index, null));
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
