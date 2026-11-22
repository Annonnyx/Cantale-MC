package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import fr.cantale.client.net.CantaleUiNetwork;
import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Faction : un formulaire quand on n'en a pas, un tableau de bord ensuite. */
public final class FactionScreen extends AbstractCantaleScreen {

	private TextFieldWidget nameField;
	private String draft = "";
	private float frame;

	public FactionScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void init() {
		nameField = new TextFieldWidget(textRenderer, 0, 0, 180, 18, Text.literal("Nom"));
		nameField.setMaxLength(16);
		nameField.setText(draft);
		nameField.setPlaceholder(Text.literal("Nom de la faction"));
		nameField.setTextShadow(false);
		nameField.setTextPredicate(value -> value.isEmpty() || value.matches("[\\p{L}0-9_\\-]{0,16}"));
		nameField.setChangedListener(value -> draft = value == null ? "" : value);
		addDrawableChild(nameField);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		frame = delta;
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void activate(String action) {
		if ("create".equals(action)) {
			CantaleUiNetwork.sendAction("create", nameField == null ? draft : nameField.getText().trim());
			return;
		}
		super.activate(action);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (nameField != null && nameField.visible && nameField.isFocused()
				&& (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			activate("create");
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		boolean form = detail.has("has") ? !flag("has") : find(11) == null;
		if (form) paintForm(context, mouseX, mouseY);
		else paintDashboard(context, mouseX, mouseY);
	}

	private void paintForm(DrawContext context, int mouseX, int mouseY) {
		int panelW = Math.min(380, width - 16);
		int panelH = Math.min(236, height - 12);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		GuiKit.text(context, textRenderer, "Pas de faction", x + 16, y + 34, GuiKit.INK);
		int need = Math.max(1, num("founders"));
		int radius = num("radius") > 0 ? num("radius") : 32;
		String line = "Il faut " + need + (need > 1 ? " joueurs" : " joueur")
				+ " sans faction dans un rayon de " + radius + " blocs.";
		GuiKit.text(context, textRenderer, clip(line, panelW - 32), x + 16, y + 48, GuiKit.MUTED);
		GuiKit.text(context, textRenderer, "Le chunk où tu te tiens devient le premier claim.", x + 16, y + 60, GuiKit.MUTED);

		int fieldX = x + 16;
		int fieldW = panelW - 32;
		if (nameField != null) {
			nameField.setX(fieldX);
			nameField.setY(y + 80);
			nameField.setWidth(fieldW);
			nameField.visible = true;
			nameField.active = true;
			nameField.render(context, mouseX, mouseY, frame);
		}
		hitAction(context, fieldX, y + 106, fieldW, 20, "Créer", "create", true, mouseX, mouseY);
		Slot join = find(24);
		if (join != null) {
			hitButton(context, fieldX, y + 132, fieldW, 20, "Invitations", join.index, false, false, mouseX, mouseY);
		}
		String notice = str("notice");
		if (!notice.isBlank()) {
			GuiKit.text(context, textRenderer, clip(notice, panelW - 32), x + 16, y + 160, GuiKit.ROSE);
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private void paintDashboard(DrawContext context, int mouseX, int mouseY) {
		if (nameField != null) {
			nameField.visible = false;
			nameField.active = false;
			nameField.setFocused(false);
		}
		List<Slot> actions = new ArrayList<>();
		for (Slot slot : flowSlots()) {
			if (slot.index == 4) continue;
			actions.add(slot);
		}
		int buttonH = 18;
		int gap = 4;
		int rows = (actions.size() + 1) / 2;
		int gridH = rows == 0 ? 0 : rows * buttonH + Math.max(0, rows - 1) * gap;
		int panelW = Math.min(460, width - 16);
		int header = 78;
		int panelH = Math.min(height - 12, 36 + header + gridH + 28);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		String name = str("name");
		if (name.isBlank() && find(4) != null) name = GuiKit.plain(find(4).name);
		drawCentered(context, name.isBlank() ? "Faction" : name, x, y + 8, panelW, 12, GuiKit.INK);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		int top = y + 30;
		int inner = panelW - 24;
		int col = (inner - 12) / 4;
		stat(context, x + 12, top, col, "Membres", Integer.toString(num("members")));
		stat(context, x + 16 + col, top, col, "Claims", Integer.toString(num("claims")));
		stat(context, x + 20 + col * 2, top, col, "Banque", grouped(num("bank")));
		stat(context, x + 24 + col * 3, top, inner - (col + 4) * 3, "Pouvoir", Integer.toString(num("power")));

		int gridTop = top + 48;
		int usable = y + panelH - 28 - gridTop;
		maxScroll = Math.max(0, gridH - usable);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int colW = (inner - 6) / 2;
		context.enableScissor(x + 12, gridTop, x + panelW - 12, gridTop + Math.max(usable, 0));
		try {
			for (int i = 0; i < actions.size(); i++) {
				Slot slot = actions.get(i);
				int bx = x + 12 + (i % 2) * (colW + 6);
				int by = gridTop + (i / 2) * (buttonH + gap) - scroll;
				if (by < gridTop || by + buttonH > gridTop + usable) continue;
				boolean danger = "RED_WOOL".equals(slot.item) || "TNT".equals(slot.item);
				hitButton(context, bx, by, colW, buttonH, GuiKit.plain(slot.name), slot.index, false, danger, mouseX, mouseY);
			}
		} finally {
			context.disableScissor();
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}

	private void stat(DrawContext context, int x, int y, int w, String label, String value) {
		if (w < 8) return;
		GuiKit.drawButton(context, x, y, w, 40, false, false);
		GuiKit.text(context, textRenderer, label, x + 6, y + 6, GuiKit.MUTED);
		GuiKit.text(context, textRenderer, clip(value, w - 12), x + 6, y + 20, GuiKit.INK);
	}
}
