package fr.cantale.client.ui.kit;

import java.util.Locale;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

/** Widgets et couleurs des menus Cantale. Papier, encre, rose d'action. */
public final class GuiKit {

	public static final int PAPER = 0xFFEEF1EE;
	public static final int PAPER_DEEP = 0xFFD5D8D4;
	public static final int CARD = 0xFFF7F8F6;
	public static final int LINE = 0xFFD0D4D0;
	public static final int INK = 0xFF2B2F4A;
	public static final int MUTED = 0xFF5C6178;
	public static final int ROSE = 0xFFC2455A;
	public static final int SCRIM = 0x880E1218;

	public static final Identifier PANEL = Identifier.of("cantale", "textures/gui/panel.png");
	public static final Identifier BUTTON = Identifier.of("cantale", "textures/gui/button.png");
	public static final Identifier BUTTON_PRIMARY = Identifier.of("cantale", "textures/gui/button_primary.png");
	public static final Identifier SLOT = Identifier.of("cantale", "textures/gui/slot.png");

	private GuiKit() {
	}

	/** Carte unie. Le cadre n'est dessiné qu'une fois, pas répété en carreaux. */
	public static void drawPanel(DrawContext context, int x, int y, int w, int h) {
		context.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x40000000);
		context.fill(x, y, x + w, y + h, PAPER);
		outline(context, x, y, w, h, INK);
	}

	public static void drawButton(DrawContext context, int x, int y, int w, int h, boolean primary, boolean hover) {
		int fill = primary ? ROSE : (hover ? 0xFFF8EEEE : CARD);
		int edge = primary ? 0xFF8E2436 : (hover ? ROSE : LINE);
		context.fill(x, y, x + w, y + h, fill);
		outline(context, x, y, w, h, edge);
		if (!primary && hover) {
			context.fill(x, y, x + 2, y + h, ROSE);
		}
	}

	public static void drawSlot(DrawContext context, int x, int y, boolean hover) {
		context.fill(x, y, x + 18, y + 18, hover ? 0xFFF8EEEE : 0xFFE4E7E4);
		outline(context, x, y, 18, 18, hover ? ROSE : LINE);
	}

	public static void drawSeparator(DrawContext context, int x, int y, int w) {
		context.fill(x, y, x + w, y + 1, LINE);
	}

	public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
		context.drawText(renderer, value, x, y, color, false);
	}

	public static void text(DrawContext context, TextRenderer renderer, Text value, int x, int y, int color) {
		context.drawText(renderer, value, x, y, color, false);
	}

	private static void outline(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	public static ItemStack stack(String name, int amount) {
		if (name == null || name.isBlank() || "AIR".equalsIgnoreCase(name)) return null;
		Identifier id = Identifier.of("minecraft", name.toLowerCase(Locale.ROOT));
		Item item = Registries.ITEM.get(id);
		if (item == null || item == Items.AIR) return null;
		int count = Math.max(1, Math.min(99, amount));
		return new ItemStack(item, count);
	}

	public static String plain(String legacy) {
		if (legacy == null) return "";
		return legacy.replaceAll("(?i)§.", "");
	}

	public static Text legacy(String raw) {
		MutableText out = Text.empty();
		if (raw == null || raw.isEmpty()) return out;
		int color = INK & 0xFFFFFF;
		boolean bold = false;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§' && i + 1 < raw.length()) {
				flush(out, run, color, bold);
				char code = Character.toLowerCase(raw.charAt(++i));
				if (code == 'r') {
					color = INK & 0xFFFFFF;
					bold = false;
				} else if (code == 'l') {
					bold = true;
				} else {
					Integer next = colorOf(code);
					if (next != null) color = next;
				}
			} else {
				run.append(c);
			}
		}
		flush(out, run, color, bold);
		return out;
	}

	public static void tooltip(DrawContext context, TextRenderer text, int mouseX, int mouseY,
			int screenW, int screenH, java.util.List<Text> lines) {
		if (lines == null || lines.isEmpty()) return;
		int w = 0;
		for (Text line : lines) w = Math.max(w, text.getWidth(line));
		w += 8;
		int h = lines.size() * 11 + 6;
		int x = mouseX + 12;
		int y = mouseY + 12;
		if (x + w > screenW - 4) x = Math.max(4, mouseX - w - 8);
		if (y + h > screenH - 4) y = Math.max(4, mouseY - h - 8);
		context.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x30000000);
		context.fill(x, y, x + w, y + h, CARD);
		outline(context, x, y, w, h, INK);
		int ly = y + 4;
		for (Text line : lines) {
			context.drawText(text, line, x + 4, ly, INK, false);
			ly += 11;
		}
	}

	private static void flush(MutableText out, StringBuilder run, int color, boolean bold) {
		if (run.isEmpty()) return;
		Style style = Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(bold);
		out.append(Text.literal(run.toString()).setStyle(style));
		run.setLength(0);
	}

	private static Integer colorOf(char code) {
		return switch (code) {
			case '0', 'f' -> 0x2B2F4A;
			case '1' -> 0x2F3F8F;
			case '2' -> 0x2E6B45;
			case '3' -> 0x2F6F78;
			case '4' -> 0x8E2F3A;
			case '5' -> 0x5E3E78;
			case '6', 'e' -> 0x8A6418;
			case '7', '8' -> 0x5C6178;
			case '9' -> 0x3A4E9A;
			case 'a' -> 0x2E7D4F;
			case 'b' -> 0x2F6F8F;
			case 'c' -> 0xC2455A;
			case 'd' -> 0x7A5A96;
			default -> null;
		};
	}
}
