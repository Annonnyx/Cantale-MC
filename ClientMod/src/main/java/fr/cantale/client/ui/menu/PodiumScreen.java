package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Un classement : le rang à gauche, la valeur à droite. */
public final class PodiumScreen extends AbstractCantaleScreen {

	public PodiumScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> rows = new ArrayList<>();
		for (Slot slot : flowSlots()) {
			if (slot.index == 4) continue;
			rows.add(slot);
		}
		int panelW = Math.min(400, width - 16);
		int rowH = 26;
		int gridH = Math.max(rowH, rows.size() * rowH);
		int view = Math.min(gridH, Math.max(rowH, height - 90));
		int panelH = 36 + (rows.isEmpty() ? 48 : view) + 28;
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		if (rows.isEmpty()) {
			drawCentered(context, "Personne dans ce classement.", x, y + 44, panelW, 14, GuiKit.MUTED);
			paintFooter(context, x, y + panelH - 24, panelW);
			return;
		}

		int top = y + 30;
		maxScroll = Math.max(0, gridH - view);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		context.enableScissor(x + 8, top, x + panelW - 8, top + view);
		try {
			for (int i = 0; i < rows.size(); i++) {
				Slot slot = rows.get(i);
				int by = top + i * rowH - scroll;
				if (by + rowH < top || by > top + view) continue;
				int accent = switch (i) {
					case 0 -> 0xFFC4A15A;
					case 1 -> 0xFF9AA0B0;
					case 2 -> 0xFFB08A62;
					default -> 0;
				};
				if (accent != 0) context.fill(x + 12, by + 4, x + 15, by + rowH - 4, accent);
				drawIcon(context, slot, x + 20, by + 4);
				String name = clip(GuiKit.plain(slot.name), panelW - 150);
				GuiKit.text(context, textRenderer, name, x + 42, by + 8, GuiKit.INK);
				String value = loreLine(slot);
				if (!value.isBlank()) {
					int vw = textRenderer.getWidth(value);
					GuiKit.text(context, textRenderer, value, x + panelW - 16 - vw, by + 8, GuiKit.INK);
				}
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
