package fr.cantale.client.ui.menu;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import fr.cantale.client.net.CantaleUiNetwork;
import fr.cantale.client.ui.kit.GuiKit;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Socle des écrans dessinés. Chaque mode a sa classe : le serveur envoie l'état,
 * le clic renvoie un index de case ou un id de bouton.
 */
public abstract class AbstractCantaleScreen extends Screen {

	protected final List<Slot> slots = new ArrayList<>();
	protected final List<Action> actions = new ArrayList<>();
	protected final List<Hit> hits = new ArrayList<>();
	protected String mode = "icons";
	protected String titleText = "";
	protected String hint = "";
	protected JsonObject detail = new JsonObject();
	protected int cols = 9;
	protected int scroll;
	protected int maxScroll;
	private boolean suppressClose;

	protected AbstractCantaleScreen(JsonObject doc) {
		super(Text.empty());
		apply(doc);
	}

	public static void show(JsonObject doc) {
		var client = net.minecraft.client.MinecraftClient.getInstance();
		String mode = doc.has("mode") ? doc.get("mode").getAsString() : "icons";
		if (client.currentScreen instanceof AbstractCantaleScreen current && current.mode.equals(mode)) {
			current.apply(doc);
			return;
		}
		if (client.currentScreen instanceof AbstractCantaleScreen current) {
			current.suppressClose();
		}
		client.setScreen(create(doc));
	}

	public static void hide() {
		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.currentScreen instanceof AbstractCantaleScreen current) {
			current.suppressClose();
			client.setScreen(null);
		}
	}

	public static AbstractCantaleScreen create(JsonObject doc) {
		String mode = doc.has("mode") ? doc.get("mode").getAsString() : "icons";
		return switch (mode) {
			case "home" -> new MainMenuScreen(doc);
			case "board" -> new BoardScreen(doc);
			case "grid", "storage" -> new GridScreen(doc);
			case "crate" -> new CrateScreen(doc);
			case "actions" -> new ActionScreen(doc);
			case "profile" -> new ProfileScreen(doc);
			case "lives" -> new LifeScreen(doc);
			case "stats" -> new StatsScreen(doc);
			case "faction" -> new FactionScreen(doc);
			case "bounties" -> new BountyScreen(doc);
			case "ranks" -> new LeaderboardScreen(doc);
			case "podium" -> new PodiumScreen(doc);
			case "quests" -> new QuestHubScreen(doc);
			case "economy" -> new EconomyScreen(doc);
			default -> new IconScreen(doc);
		};
	}

	public void suppressClose() {
		suppressClose = true;
	}

	public final void apply(JsonObject doc) {
		String nextTitle = doc.has("title") ? doc.get("title").getAsString() : "";
		if (!nextTitle.equals(titleText)) scroll = 0;
		titleText = nextTitle;
		mode = doc.has("mode") ? doc.get("mode").getAsString() : "icons";
		hint = doc.has("hint") ? doc.get("hint").getAsString() : "";
		detail = doc.has("detail") && doc.get("detail").isJsonObject() ? doc.getAsJsonObject("detail") : new JsonObject();
		cols = doc.has("cols") ? Math.max(1, doc.get("cols").getAsInt()) : 9;
		slots.clear();
		actions.clear();
		if (doc.has("slots") && doc.get("slots").isJsonArray()) {
			JsonArray array = doc.getAsJsonArray("slots");
			for (int i = 0; i < array.size(); i++) {
				JsonObject obj = array.get(i).getAsJsonObject();
				Slot slot = new Slot();
				slot.index = obj.has("i") ? obj.get("i").getAsInt() : i;
				slot.item = obj.has("item") ? obj.get("item").getAsString() : "AIR";
				slot.name = obj.has("name") ? obj.get("name").getAsString() : "";
				slot.amount = obj.has("amt") ? obj.get("amt").getAsInt() : 1;
				slot.role = obj.has("role") ? obj.get("role").getAsString() : "content";
				slot.lore = new ArrayList<>();
				if (obj.has("lore") && obj.get("lore").isJsonArray()) {
					for (var line : obj.getAsJsonArray("lore")) slot.lore.add(line.getAsString());
				}
				slots.add(slot);
			}
		}
		if (doc.has("actions") && doc.get("actions").isJsonArray()) {
			for (var element : doc.getAsJsonArray("actions")) {
				JsonObject obj = element.getAsJsonObject();
				Action action = new Action();
				action.id = obj.has("id") ? obj.get("id").getAsString() : "";
				action.label = obj.has("label") ? obj.get("label").getAsString() : action.id;
				action.primary = obj.has("primary") && obj.get("primary").getAsBoolean();
				actions.add(action);
			}
		}
	}

	protected abstract void paint(DrawContext context, int mouseX, int mouseY);

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		hits.clear();
		context.fill(0, 0, width, height, GuiKit.SCRIM);
		paint(context, mouseX, mouseY);
		if (!itemTooltip()) return;
		Slot hovered = slotAt(mouseX, mouseY);
		if (hovered != null && (!hovered.name.isBlank() || !hovered.lore.isEmpty())) {
			List<Text> lines = new ArrayList<>();
			if (!hovered.name.isBlank()) lines.add(GuiKit.legacy(hovered.name));
			for (String line : hovered.lore) {
				if (!GuiKit.plain(line).isBlank()) lines.add(GuiKit.legacy(line));
			}
			GuiKit.tooltip(context, textRenderer, mouseX, mouseY, width, height, lines);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int button = click.button();
		if (button != 0 && button != 1) return super.mouseClicked(click, doubled);
		Hit hit = hitAt(click.x(), click.y());
		if (hit == null) return super.mouseClicked(click, doubled);
		long window = client.getWindow().getHandle();
		boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (hit.action != null) activate(hit.action);
		else if (hit.slot >= 0) CantaleUiNetwork.sendClick(hit.slot, button, shift);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScroll <= 0) return false;
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 16));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		if (!suppressClose) CantaleUiNetwork.sendClose();
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	protected boolean itemTooltip() {
		return switch (mode) {
			case "home", "board", "profile", "lives", "stats", "faction", "bounties", "ranks", "podium", "quests", "economy" -> false;
			default -> true;
		};
	}

	protected void activate(String action) {
		CantaleUiNetwork.sendAction(action);
	}

	protected String str(String key) {
		if (detail == null || !detail.has(key) || detail.get(key).isJsonNull()) return "";
		try {
			return detail.get(key).getAsString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	protected int num(String key) {
		if (detail == null || !detail.has(key) || !detail.get(key).isJsonPrimitive()) return 0;
		try {
			return detail.get(key).getAsInt();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	protected boolean flag(String key) {
		if (detail == null || !detail.has(key) || !detail.get(key).isJsonPrimitive()) return false;
		try {
			return detail.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			return false;
		}
	}

	protected String grouped(int value) {
		return String.format(java.util.Locale.FRANCE, "%,d", value);
	}

	protected Slot find(int index) {
		for (Slot slot : slots) if (slot.index == index) return slot;
		return null;
	}

	protected String loreLine(Slot slot) {
		if (slot == null) return "";
		for (String line : slot.lore) {
			String plain = GuiKit.plain(line).trim();
			if (!plain.isBlank()) return plain;
		}
		return "";
	}

	protected void hitButton(DrawContext context, int x, int y, int w, int h, String label, int slot, boolean primary, boolean danger, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		GuiKit.drawButton(context, x, y, w, h, primary, hover);
		if (danger && !primary) context.fill(x, y, x + 2, y + h, GuiKit.ROSE);
		drawCentered(context, label, x, y, w, h, primary ? 0xFFF7F8F6 : GuiKit.INK);
		hits.add(new Hit(x, y, w, h, slot, null));
	}

	protected void hitAction(DrawContext context, int x, int y, int w, int h, String label, String action, boolean primary, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		GuiKit.drawButton(context, x, y, w, h, primary, hover);
		drawCentered(context, label, x, y, w, h, primary ? 0xFFF7F8F6 : GuiKit.INK);
		hits.add(new Hit(x, y, w, h, -1, action));
	}

	protected List<Slot> flowSlots() {
		List<Slot> out = new ArrayList<>();
		for (Slot slot : slots) {
			if ("back".equals(slot.role) || "close".equals(slot.role) || "refresh".equals(slot.role)) continue;
			if ("pad".equals(slot.role) || "cell".equals(slot.role)) continue;
			out.add(slot);
		}
		return out;
	}

	protected void paintFooter(DrawContext context, int x, int y, int w) {
		List<Slot> nav = new ArrayList<>();
		for (Slot slot : slots) {
			if ("back".equals(slot.role) || "close".equals(slot.role) || "refresh".equals(slot.role)) nav.add(slot);
		}
		if (nav.isEmpty()) return;
		int bw = 78;
		int gap = 6;
		int total = nav.size() * bw + (nav.size() - 1) * gap;
		int sx = x + Math.max(0, (w - total) / 2);
		for (Slot slot : nav) {
			boolean hover = inside(sx, y, bw, 18);
			GuiKit.drawButton(context, sx, y, bw, 18, false, hover);
			String label = GuiKit.plain(slot.name);
			if (label.isBlank()) label = slot.role;
			drawCentered(context, label, sx, y, bw, 18, GuiKit.INK);
			hits.add(new Hit(sx, y, bw, 18, slot.index, null));
			sx += bw + gap;
		}
	}

	protected void drawTitle(DrawContext context, int x, int y, int w) {
		String title = clip(GuiKit.plain(titleText).trim(), w - 24);
		int tw = textRenderer.getWidth(title);
		GuiKit.text(context, textRenderer, title, x + Math.max(0, (w - tw) / 2), y, GuiKit.INK);
	}

	protected void drawIcon(DrawContext context, Slot slot, int x, int y) {
		ItemStack stack = GuiKit.stack(slot.item, 1);
		if (stack != null && !stack.isEmpty()) context.drawItem(stack, x, y);
	}

	protected void drawStack(DrawContext context, Slot slot, int x, int y, boolean hover) {
		GuiKit.drawSlot(context, x, y, hover);
		ItemStack stack = GuiKit.stack(slot.item, slot.amount);
		if (stack != null && !stack.isEmpty()) context.drawItem(stack, x + 1, y + 1);
		if (slot.amount > 1) {
			String n = Integer.toString(slot.amount);
			GuiKit.text(context, textRenderer, n, x + 18 - textRenderer.getWidth(n), y + 10, GuiKit.INK);
		}
	}

	protected void drawCentered(DrawContext context, String label, int x, int y, int w, int h, int color) {
		String shown = clip(label, w - 8);
		int tw = textRenderer.getWidth(shown);
		GuiKit.text(context, textRenderer, shown, x + (w - tw) / 2, y + (h - 8) / 2, color);
	}

	protected String clip(String label, int max) {
		if (label == null) return "";
		if (textRenderer.getWidth(label) <= max) return label;
		while (label.length() > 1 && textRenderer.getWidth(label + "…") > max) {
			label = label.substring(0, label.length() - 1);
		}
		return label + "…";
	}

	protected boolean inside(int x, int y, int w, int h) {
		double mx = client.mouse.getX() * width / client.getWindow().getWidth();
		double my = client.mouse.getY() * height / client.getWindow().getHeight();
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private Slot slotAt(double mx, double my) {
		Hit hit = hitAt(mx, my);
		if (hit == null || hit.slot < 0) return null;
		for (Slot slot : slots) if (slot.index == hit.slot) return slot;
		return null;
	}

	private Hit hitAt(double mx, double my) {
		Hit found = null;
		for (Hit hit : hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) found = hit;
		}
		return found;
	}

	protected static final class Slot {
		int index;
		String item = "AIR";
		String name = "";
		List<String> lore = List.of();
		int amount;
		String role = "content";
	}

	protected static final class Action {
		String id = "";
		String label = "";
		boolean primary;
	}

	protected record Hit(int x, int y, int w, int h, int slot, String action) {
	}
}
