package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Profil : grade, skin, cosmétiques, puis les accès du joueur. */
public final class ProfileScreen extends AbstractCantaleScreen {

	public ProfileScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		boolean self = !detail.has("self") || flag("self");
		List<Slot> actions = new ArrayList<>();
		Slot boss = null;
		if (self) {
			for (Slot slot : flowSlots()) {
				if (slot.index == 4) continue;
				if (slot.index == 27) {
					boss = slot;
					continue;
				}
				actions.add(slot);
			}
		}
		int buttonH = 18;
		int gap = 4;
		int rows = (actions.size() + 1) / 2;
		int gridH = rows == 0 ? 0 : rows * buttonH + Math.max(0, rows - 1) * gap;
		int bossH = boss == null ? 0 : buttonH + gap;
		int body = Math.max(188, 44 + gridH + bossH);
		int panelW = Math.min(468, width - 16);
		int panelH = Math.min(height - 12, 36 + body + 28);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		int top = y + 30;
		int leftW = 156;
		int left = x + 12;
		int cardH = panelH - 58;
		GuiKit.drawButton(context, left, top, leftW, cardH, false, false);

		String rank = str("rank");
		if (rank.isBlank()) rank = "Sans grade";
		String rankId = str("rankId");
		int chipW = Math.min(leftW - 16, textRenderer.getWidth(rank) + 16);
		int chipX = left + (leftW - chipW) / 2;
		context.fill(chipX, top + 8, chipX + chipW, top + 22, PlayerPreview.rankFill(rankId));
		drawCentered(context, rank, chipX, top + 8, chipW, 14, PlayerPreview.rankText(rankId));

		int px1 = left + 16;
		int py1 = top + 28;
		int px2 = left + leftW - 16;
		int py2 = top + cardH - 62;
		boolean drawn = PlayerPreview.draw(context, client, px1, py1, px2, py2, mouseX, mouseY, str("uuid"), flag("self"));
		if (!drawn) {
			Slot head = find(4);
			ItemStack stack = head == null ? null : GuiKit.stack(head.item, 1);
			PlayerPreview.item(context, stack, left + (leftW - 48) / 2, py1 + 8, 3f);
		}
		String name = str("name");
		if (name.isBlank() && find(4) != null) name = GuiKit.plain(find(4).name);
		drawCentered(context, name, left + 4, py2 + 4, leftW - 8, 12, GuiKit.INK);

		int wellW = (leftW - 20) / 3;
		int wellY = top + cardH - 36;
		well(context, left + 6, wellY, wellW, "Titre", str("title"));
		well(context, left + 8 + wellW, wellY, wellW, "Aura", str("aura"));
		well(context, left + 10 + wellW * 2, wellY, wellW, "Cape", str("cape"));

		int right = left + leftW + 10;
		int rightW = x + panelW - 12 - right;
		int factW = Math.max(40, (rightW - 8) / 3);
		tile(context, right, top, factW, "Vies", Integer.toString(num("lives")));
		tile(context, right + factW + 4, top, factW, "Cantox", grouped(num("cantox")));
		String faction = str("faction");
		tile(context, right + (factW + 4) * 2, top, rightW - (factW + 4) * 2, "Faction", faction.isBlank() ? "Aucune" : faction);

		int gridTop = top + 40;
		int usable = cardH - 40 - bossH;
		maxScroll = Math.max(0, gridH - usable);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int colW = (rightW - 4) / 2;
		context.enableScissor(right, gridTop, right + rightW, gridTop + Math.max(usable, 0));
		try {
			for (int i = 0; i < actions.size(); i++) {
				Slot slot = actions.get(i);
				int bx = right + (i % 2) * (colW + 4);
				int by = gridTop + (i / 2) * (buttonH + gap) - scroll;
				if (by < gridTop || by + buttonH > gridTop + usable) continue;
				boolean danger = "RED_WOOL".equals(slot.item) || "TNT".equals(slot.item);
				hitButton(context, bx, by, colW, buttonH, GuiKit.plain(slot.name), slot.index, false, danger, mouseX, mouseY);
			}
		} finally {
			context.disableScissor();
		}
		if (boss != null) {
			String label = flag("bossbar") ? "BossBar activée" : "BossBar coupée";
			hitButton(context, right, top + cardH - buttonH, rightW, buttonH, label, boss.index, false, false, mouseX, mouseY);
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private void tile(DrawContext context, int x, int y, int w, String label, String value) {
		if (w < 8) return;
		GuiKit.drawButton(context, x, y, w, 32, false, false);
		GuiKit.text(context, textRenderer, label, x + 6, y + 5, GuiKit.MUTED);
		GuiKit.text(context, textRenderer, clip(value, w - 12), x + 6, y + 17, GuiKit.INK);
	}

	private void well(DrawContext context, int x, int y, int w, String kind, String value) {
		boolean empty = value == null || value.isBlank();
		GuiKit.drawButton(context, x, y, w, 30, false, false);
		GuiKit.text(context, textRenderer, kind, x + 3, y + 4, GuiKit.MUTED);
		GuiKit.text(context, textRenderer, clip(empty ? "Libre" : value, w - 6), x + 3, y + 16, empty ? GuiKit.MUTED : GuiKit.INK);
	}
}
