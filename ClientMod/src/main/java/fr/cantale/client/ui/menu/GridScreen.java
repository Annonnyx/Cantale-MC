package fr.cantale.client.ui.menu;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Échange et coffres : les cases gardent leur place (colonne, ligne).
 * Le clic envoie l'index, le plugin déplace le vrai objet.
 */
public final class GridScreen extends AbstractCantaleScreen {

	public GridScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		int maxIndex = 0;
		for (Slot slot : slots) maxIndex = Math.max(maxIndex, slot.index);
		int rows = maxIndex / cols + 1;
		int cell = 20;
		boolean trade = "grid".equals(mode);
		int gap = trade ? 10 : 0;
		int gridW = cols * cell + (trade ? gap : 0);
		int gridH = rows * cell;
		int panelW = gridW + 24;
		int panelH = gridH + 58;
		int x = (width - panelW) / 2;
		int y = Math.max(8, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 10, y + 22, panelW - 20);

		int originX = x + 12;
		int originY = y + 28;
		for (Slot slot : slots) {
			if ("pad".equals(slot.role)) continue;
			int col = slot.index % cols;
			int row = slot.index / cols;
			int extra = trade && col > 4 ? gap : 0;
			int sx = originX + col * cell + extra;
			int sy = originY + row * cell;
			boolean hover = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
			boolean confirm = GuiKit.plain(slot.name).toLowerCase(java.util.Locale.ROOT).contains("confirmer");
			drawStack(context, slot, sx, sy, hover || confirm);
			hits.add(new Hit(sx, sy, 18, 18, slot.index, null));
		}
		String key = "grid".equals(mode) ? "gui.cantale.trade_hint" : "gui.cantale.storage_hint";
		Text note = Text.translatable(key);
		int noteW = textRenderer.getWidth(note);
		GuiKit.text(context, textRenderer, note, x + Math.max(8, (panelW - noteW) / 2), y + panelH - 14, GuiKit.MUTED);
		maxScroll = 0;
	}
}
