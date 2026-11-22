package fr.cantale.client.ui.menu;

import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;

/** Profil, faction, vies, quêtes, classements, stats, wanted : une carte à la taille du texte. */
public final class BoardScreen extends AbstractCantaleScreen {

	public BoardScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> items = flowSlots();
		boolean detailed = false;
		int textW = 96;
		for (Slot slot : items) {
			textW = Math.max(textW, textRenderer.getWidth(GuiKit.plain(slot.name)));
			String detail = firstLore(slot);
			if (!detail.isBlank()) {
				detailed = true;
				textW = Math.max(textW, textRenderer.getWidth(detail));
			}
		}
		int rowH = detailed ? 30 : 22;
		int panelW = Math.min(width - 24, Math.max(240, textW + 52));
		int contentH = Math.max(rowH, items.size() * rowH);
		int viewH = Math.min(contentH, Math.max(rowH, height - 78));
		int panelH = 40 + viewH + 30;
		int x = (width - panelW) / 2;
		int y = Math.max(8, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 10, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 24, panelW - 24);

		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.min(scroll, maxScroll);
		int top = y + 30;
		context.enableScissor(x + 6, top, x + panelW - 6, top + viewH);
		try {
			for (int i = 0; i < items.size(); i++) {
				Slot slot = items.get(i);
				int by = top + i * rowH - scroll;
				if (by + rowH < top || by > top + viewH) continue;
				int bx = x + 8;
				int bw = panelW - 16;
				boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + rowH - 2;
				if (hover) {
					context.fill(bx, by, bx + bw, by + rowH - 4, 0x18C2455A);
					context.fill(bx, by, bx + 2, by + rowH - 4, GuiKit.ROSE);
				}
				int iconY = by + Math.max(0, (rowH - 20) / 2);
				drawIcon(context, slot, bx + 6, iconY);
				int textX = bx + 26;
				String name = clip(GuiKit.plain(slot.name), bw - 34);
				String detail = firstLore(slot);
				if (detail.isBlank()) {
					GuiKit.text(context, textRenderer, name, textX, by + (rowH - 12) / 2, GuiKit.INK);
				} else {
					GuiKit.text(context, textRenderer, name, textX, by + 3, GuiKit.INK);
					GuiKit.text(context, textRenderer, clip(detail, bw - 34), textX, by + 14, GuiKit.MUTED);
				}
				hits.add(new Hit(bx, by, bw, rowH - 2, slot.index, null));
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private String firstLore(Slot slot) {
		for (String line : slot.lore) {
			String plain = GuiKit.plain(line).trim();
			if (!plain.isBlank()) return plain;
		}
		return "";
	}
}
