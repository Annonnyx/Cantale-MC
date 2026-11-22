package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Primes : une carte par cible, ou un état vide. */
public final class BountyScreen extends AbstractCantaleScreen {

	public BountyScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		List<Slot> cards = new ArrayList<>();
		for (Slot slot : flowSlots()) {
			if (slot.index == 4) continue;
			cards.add(slot);
		}
		String only = cards.size() == 1 ? GuiKit.plain(cards.get(0).name) : "";
		boolean empty = cards.isEmpty() || only.contains("Aucune") || only.contains("Erreur");
		int panelW = Math.min(empty ? 340 : 460, width - 16);
		int cardH = 58;
		int rows = empty ? 1 : (cards.size() + 1) / 2;
		int gridH = rows * (cardH + 6);
		int panelH = Math.min(height - 12, 36 + (empty ? 70 : gridH) + 28);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		if (empty) {
			String message = only.isBlank() ? "Aucune prime active" : only;
			drawCentered(context, message, x, y + 48, panelW, 16, GuiKit.INK);
			drawCentered(context, "Les primes apparaissent ici.", x, y + 66, panelW, 12, GuiKit.MUTED);
			paintFooter(context, x, y + panelH - 24, panelW);
			return;
		}

		int top = y + 32;
		int usable = panelH - 32 - 28;
		maxScroll = Math.max(0, gridH - usable);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int colW = (panelW - 30) / 2;
		context.enableScissor(x + 8, top, x + panelW - 8, top + usable);
		try {
			for (int i = 0; i < cards.size(); i++) {
				Slot slot = cards.get(i);
				int bx = x + 12 + (i % 2) * (colW + 6);
				int by = top + (i / 2) * (cardH + 6) - scroll;
				if (by + cardH < top || by > top + usable) continue;
				GuiKit.drawButton(context, bx, by, colW, cardH, false, false);
				ItemStack stack = GuiKit.stack(slot.item, 1);
				PlayerPreview.item(context, stack, bx + 8, by + 12, 2f);
				int textX = bx + 46;
				GuiKit.text(context, textRenderer, clip(GuiKit.plain(slot.name), colW - 56), textX, by + 10, GuiKit.INK);
				int lineY = by + 24;
				int shown = 0;
				for (String line : slot.lore) {
					String plain = GuiKit.plain(line).trim();
					if (plain.isBlank()) continue;
					int color = plain.toLowerCase(java.util.Locale.ROOT).contains("prime") ? 0xFF8A6418 : GuiKit.MUTED;
					GuiKit.text(context, textRenderer, clip(plain, colW - 56), textX, lineY, color);
					lineY += 11;
					if (++shown >= 2) break;
				}
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
