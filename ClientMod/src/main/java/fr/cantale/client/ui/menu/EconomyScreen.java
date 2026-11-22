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

/** Solde en grand. Le paiement se prépare ici, le montant compris. */
public final class EconomyScreen extends AbstractCantaleScreen {

	private TextFieldWidget amountField;
	private String draft = "";
	private String payingSeen = "";
	private float frame;

	public EconomyScreen(JsonObject doc) {
		super(doc);
	}

	@Override
	protected void init() {
		amountField = new TextFieldWidget(textRenderer, 0, 0, 160, 18, Text.literal("Montant"));
		amountField.setMaxLength(10);
		amountField.setText(draft);
		amountField.setPlaceholder(Text.literal("Montant"));
		amountField.setTextShadow(false);
		amountField.setTextPredicate(value -> value.isEmpty() || value.matches("\\d{0,7}([.,]\\d{0,2})?"));
		amountField.setChangedListener(value -> draft = value == null ? "" : value);
		addDrawableChild(amountField);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		frame = delta;
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void activate(String action) {
		if ("pay".equals(action)) {
			CantaleUiNetwork.sendAction("pay", amountField == null ? draft : amountField.getText().trim());
			return;
		}
		super.activate(action);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (amountField != null && amountField.visible && amountField.isFocused()
				&& (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			activate("pay");
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	protected void paint(DrawContext context, int mouseX, int mouseY) {
		String paying = str("pay");
		if (!paying.equals(payingSeen)) {
			payingSeen = paying;
			draft = "";
			if (amountField != null) amountField.setText("");
		}
		boolean form = !paying.isBlank();
		int panelW = Math.min(380, width - 16);
		int panelH = Math.min(form ? 230 : 214, height - 12);
		int x = (width - panelW) / 2;
		int y = Math.max(6, (height - panelH) / 2);
		GuiKit.drawPanel(context, x, y, panelW, panelH);
		drawTitle(context, x, y + 8, panelW);
		GuiKit.drawSeparator(context, x + 12, y + 22, panelW - 24);

		String figure = grouped(num("balance"));
		int tw = textRenderer.getWidth(figure) * 2;
		var matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x + (panelW - tw) / 2f, y + 36);
		matrices.scale(2f, 2f);
		GuiKit.text(context, textRenderer, figure, 0, 0, GuiKit.INK);
		matrices.popMatrix();
		drawCentered(context, "Cantox", x, y + 58, panelW, 12, GuiKit.MUTED);

		if (amountField != null) {
			amountField.visible = form;
			amountField.active = form;
			if (!form) amountField.setFocused(false);
		}
		if (form) {
			GuiKit.text(context, textRenderer, "Payer " + paying, x + 16, y + 80, GuiKit.INK);
			if (amountField != null) {
				amountField.setX(x + 16);
				amountField.setY(y + 96);
				amountField.setWidth(panelW - 32);
				amountField.render(context, mouseX, mouseY, frame);
			}
			hitAction(context, x + 16, y + 122, panelW - 32, 20, "Envoyer", "pay", true, mouseX, mouseY);
			hitAction(context, x + 16, y + 146, panelW - 32, 18, "Annuler", "cancelpay", false, mouseX, mouseY);
		} else {
			List<Slot> actions = new ArrayList<>();
			for (Slot slot : flowSlots()) {
				if (slot.index == 4) continue;
				actions.add(slot);
			}
			int bw = panelW - 32;
			int by = y + 82;
			for (Slot slot : actions) {
				hitButton(context, x + 16, by, bw, 20, GuiKit.plain(slot.name), slot.index, false, false, mouseX, mouseY);
				by += 24;
			}
			GuiKit.text(context, textRenderer, "Le montant se choisit ici, après le joueur.", x + 16, by + 2, GuiKit.MUTED);
		}
		String notice = str("notice");
		if (!notice.isBlank()) {
			GuiKit.text(context, textRenderer, clip(notice, panelW - 32), x + 16, y + panelH - 40, GuiKit.ROSE);
		}
		paintFooter(context, x, y + panelH - 24, panelW);
	}
}
