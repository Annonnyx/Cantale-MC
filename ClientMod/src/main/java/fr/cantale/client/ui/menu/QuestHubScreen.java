package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Deux chemins : solo, ou faction. */
public final class QuestHubScreen extends AbstractCantaleScreen {

	public QuestHubScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> paths = new ArrayList<>(flowSlots());
		int panelW = Math.min(460, width - 16);
		int panelH = Math.min(210, height - 12);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		if (paths.isEmpty()) {
			paintFooter(context, x, y + panelH - 24, panelW);
			return;
		}
		int gap = 8;
		int cardW = (panelW - 24 - gap * (paths.size() - 1)) / paths.size();
		int cardH = panelH - 64;
		for (int i = 0; i < paths.size(); i++) {
			Slot slot = paths.get(i);
			int bx = x + 12 + i * (cardW + gap);
			int by = y + 32;
			boolean hover = mouseX >= bx && mouseX < bx + cardW && mouseY >= by && mouseY < by + cardH;
			GuiKit.drawButton(context, bx, by, cardW, cardH, false, hover);
			if (hover) context.fill(bx, by, bx + 3, by + cardH, GuiKit.ROSE);
			drawIcon(context, slot, bx + 12, by + 14);
			GuiKit.text(context, textRenderer, clip(GuiKit.plain(slot.name), cardW - 40), bx + 34, by + 18, GuiKit.INK);
			int lineY = by + 40;
			int shown = 0;
			for (String line : slot.lore) {
				String plain = GuiKit.plain(line).trim();
				if (plain.isBlank() || plain.startsWith("Clic")) continue;
				GuiKit.text(context, textRenderer, clip(plain, cardW - 24), bx + 12, lineY, GuiKit.MUTED);
				lineY += 12;
				if (++shown >= 4) break;
			}
			hits.add(new Hit(bx, by, cardW, cardH, slot.index, null));
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
