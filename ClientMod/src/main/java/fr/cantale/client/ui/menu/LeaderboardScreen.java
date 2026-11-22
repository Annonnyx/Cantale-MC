package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Choix du classement : une tuile par catégorie. */
public final class LeaderboardScreen extends AbstractCantaleScreen {

	public LeaderboardScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> tiles = new ArrayList<>();
		for (Slot slot : flowSlots()) {
			if (slot.index == 4) continue;
			tiles.add(slot);
		}
		int panelW = Math.min(420, width - 16);
		int rows = (tiles.size() + 1) / 2;
		int panelH = Math.min(height - 12, 40 + rows * 58 + 28);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		int top = y + 32;
		int colW = (panelW - 30) / 2;
		for (int i = 0; i < tiles.size(); i++) {
			Slot slot = tiles.get(i);
			boolean lastOdd = i == tiles.size() - 1 && tiles.size() % 2 == 1;
			int bw = lastOdd ? panelW - 24 : colW;
			int bx = lastOdd ? x + 12 : x + 12 + (i % 2) * (colW + 6);
			int by = top + (i / 2) * 58;
			boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 52;
			GuiKit.drawButton(context, bx, by, bw, 52, false, hover);
			if (hover) context.fill(bx, by, bx + 2, by + 52, GuiKit.ROSE);
			drawIcon(context, slot, bx + 10, by + 18);
			GuiKit.text(context, textRenderer, clip(GuiKit.plain(slot.name), bw - 40), bx + 32, by + 12, GuiKit.INK);
			String detail = loreLine(slot);
			if (!detail.isBlank()) {
				GuiKit.text(context, textRenderer, clip(detail, bw - 40), bx + 32, by + 26, GuiKit.MUTED);
			}
			hits.add(new Hit(bx, by, bw, 52, slot.index, null));
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
