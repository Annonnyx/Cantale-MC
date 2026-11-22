package fr.cantale.client.ui;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.data.ServerDataStore;
import fr.cantale.client.data.WaypointManager;
import fr.cantale.client.net.CantaleNetworkHandler;
import fr.cantale.client.render.ClaimColors;
import fr.cantale.client.render.MapCanvas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran carte plein écran (touche M) : terrain, pan au drag, zoom molette vers le
 * curseur, overlays serveur, panneau latéral (filtres, liste, TP avec confirmation)
 * et menu contextuel au clic droit.
 */
public final class MapScreen extends Screen {

	private static final int[] ZOOM_LEVELS = {1, 2, 4, 8, 16};
	private static final int TOP_H = 22;
	private static final int PANEL_W = 220;
	private static final int PANEL_COLLAPSED_W = 18;
	private static final int DRAG_THRESHOLD_PX = 4;
	private static final int FILTER_ROW_H = 16;
	private static final int LIST_ROW_H = 16;
	private static final int HINT_H = 28;

	private static final int COL_BG = 0xFFEEF1EE;
	private static final int COL_TOP = 0xF2EEF1EE;
	private static final int COL_PANEL = 0xF7F4EEE8;
	private static final int COL_PANEL_EDGE = 0xFF2B2F4A;
	private static final int COL_ROW_HOVER = 0x22C2455A;
	private static final int COL_TEXT = 0xFF2B2F4A;
	private static final int COL_MUTED = 0xFF5C6178;
	private static final int COL_BOX = 0xFFEEF1EE;
	private static final int COL_BOX_EDGE = 0xFF2B2F4A;

	private static final int CTX_W = 172;
	private static final int CTX_ITEM_H = 18;
	private static final int PALETTE_CELL = 16;

	private enum Overlay {
		NONE, NAME, CONFIRM_TP, CONFIRM_DELETE, CONTEXT
	}

	private MapCanvas canvas;
	private double centerX;
	private double centerZ;
	private int zoomIndex = 2;

	private boolean dragging;
	private boolean didDrag;
	private double dragAccum;

	private boolean panelCollapsed;
	private int listScroll;
	private int listContentHeight;
	private int listTop;
	private int listBottom;

	private Overlay overlay = Overlay.NONE;
	private TextFieldWidget nameField;
	private ButtonWidget confirmBtn;
	private ButtonWidget cancelBtn;
	private boolean renaming;
	private String renameId;
	private double pendingX;
	private double pendingY;
	private double pendingZ;

	private String pendingTpCommand;
	private String pendingTpName;
	private String pendingDeleteId;
	private String pendingDeleteName;

	private int tipSeq = -1;
	private String tipFaction = "";
	private String tipLeader = "";
	private String tipRel = "";
	private Text tipLineFaction = Text.empty();
	private Text tipLineLeader = Text.empty();
	private Text tipLineRelation = Text.empty();
	private int tipAnchorX;
	private int tipAnchorY;

	private MapCanvas.MapMarker contextMarker;
	private int ctxX;
	private int ctxY;
	private int ctxW;
	private int ctxH;
	private int ctxPaletteTop = -1;
	private final List<CtxItem> ctxItems = new ArrayList<>();

	public MapScreen() {
		super(Text.translatable("screen.cantale.map.title"));
		var player = MinecraftClient.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	@Override
	protected void init() {
		rebuildCanvas();
		if (overlay == Overlay.NAME) {
			addNameOverlayWidgets();
		}
	}

	private void rebuildCanvas() {
		if (canvas != null) {
			canvas.dispose();
		}
		canvas = new MapCanvas("fullscreen_map", mapWidth(), mapHeight());
	}

	@Override
	public void removed() {
		if (canvas != null) {
			canvas.dispose();
			canvas = null;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		context.fill(0, 0, width, height, COL_BG);
	}

	// ─── Géométrie ───────────────────────────────────────────────

	private int panelWidth() {
		return panelCollapsed ? PANEL_COLLAPSED_W : PANEL_W;
	}

	private int mapX() {
		return 0;
	}

	private int mapY() {
		return TOP_H;
	}

	private int mapWidth() {
		return Math.max(16, width - panelWidth());
	}

	private int mapHeight() {
		return Math.max(16, height - TOP_H);
	}

	private int panelX() {
		return width - panelWidth();
	}

	private int currentZoom() {
		return ZOOM_LEVELS[zoomIndex];
	}

	private boolean isOnMap(double mx, double my) {
		return mx >= mapX() && mx < mapX() + mapWidth() && my >= mapY() && my < mapY() + mapHeight();
	}

	private boolean isOnPanel(double mx, double my) {
		return mx >= panelX() && mx < width && my >= TOP_H && my < height;
	}

	private double screenToWorldX(double mx) {
		return centerX + (mx - mapX() - mapWidth() / 2.0) * currentZoom();
	}

	private double screenToWorldZ(double my) {
		return centerZ + (my - mapY() - mapHeight() / 2.0) * currentZoom();
	}

	private void recenterOnPlayer() {
		if (client != null && client.player != null) {
			centerX = client.player.getX();
			centerZ = client.player.getZ();
		}
	}

	private void centerOn(double worldX, double worldZ) {
		centerX = worldX;
		centerZ = worldZ;
	}

	private MapCanvas.MapMarker hitTest(double mx, double my) {
		if (canvas == null || !isOnMap(mx, my)) return null;
		return canvas.hitTest(mx, my, CantaleMod.getServerWorldName(),
				centerX, centerZ, currentZoom(), mapX(), mapY());
	}

	private boolean overlayBlocksMap() {
		return overlay != Overlay.NONE;
	}

	// ─── Entrées ─────────────────────────────────────────────────

	@Override
	public boolean keyPressed(KeyInput input) {
		if (overlay == Overlay.NAME) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				closeNameOverlay();
				return true;
			}
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
				confirmName();
				return true;
			}
		} else if (overlay == Overlay.CONFIRM_TP) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				closeOverlay();
				return true;
			}
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
				executeTeleport();
				return true;
			}
		} else if (overlay == Overlay.CONFIRM_DELETE) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				closeOverlay();
				return true;
			}
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
				executeDelete();
				return true;
			}
		} else if (overlay == Overlay.CONTEXT) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				closeOverlay();
				return true;
			}
		}
		if (overlay == Overlay.NONE && input.key() == GLFW.GLFW_KEY_M) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (overlay == Overlay.NAME) {
			return super.mouseClicked(click, doubled);
		}
		if (overlay == Overlay.CONFIRM_TP) {
			handleConfirmClick(click.x(), click.y(), true);
			return true;
		}
		if (overlay == Overlay.CONFIRM_DELETE) {
			handleConfirmClick(click.x(), click.y(), false);
			return true;
		}
		if (overlay == Overlay.CONTEXT) {
			handleContextClick(click.x(), click.y());
			return true;
		}
		if (isOnPanel(click.x(), click.y())) {
			handlePanelClick(click.x(), click.y());
			return true;
		}
		if (isOnMap(click.x(), click.y())) {
			if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				MapCanvas.MapMarker hit = hitTest(click.x(), click.y());
				if (hit != null) {
					openContextMenu((int) click.x(), (int) click.y(), hit);
				} else {
					openNameOverlay(screenToWorldX(click.x()), screenToWorldZ(click.y()), false, null);
				}
				return true;
			}
			if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				if (doubled) {
					recenterOnPlayer();
					return true;
				}
				dragging = true;
				didDrag = false;
				dragAccum = 0;
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
			dragging = false;
			if (!didDrag && overlay == Overlay.NONE && isOnMap(click.x(), click.y())) {
				MapCanvas.MapMarker hit = hitTest(click.x(), click.y());
				if (hit != null && hit.canTeleport()) {
					requestTeleport(hit.command(), hit.name());
				} else if (hit != null) {
					centerOn(hit.x(), hit.z());
				} else {
					inspectAt(click.x(), click.y());
				}
			}
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (overlayBlocksMap()) return true;
		if (dragging && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragAccum += Math.hypot(offsetX, offsetY);
			if (dragAccum >= DRAG_THRESHOLD_PX) {
				if (!didDrag) {
					ServerDataStore.clearTooltip();
				}
				didDrag = true;
			}
			centerX -= offsetX * currentZoom();
			centerZ -= offsetY * currentZoom();
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (overlayBlocksMap()) {
			return true;
		}
		if (isOnPanel(mouseX, mouseY) && !panelCollapsed) {
			int viewH = Math.max(1, listBottom - listTop);
			int maxScroll = Math.max(0, listContentHeight - viewH);
			listScroll = (int) Math.round(listScroll - verticalAmount * 18);
			if (listScroll < 0) listScroll = 0;
			if (listScroll > maxScroll) listScroll = maxScroll;
			return true;
		}
		if (!isOnMap(mouseX, mouseY) || verticalAmount == 0) {
			return true;
		}
		double worldX = screenToWorldX(mouseX);
		double worldZ = screenToWorldZ(mouseY);
		if (verticalAmount > 0 && zoomIndex > 0) {
			zoomIndex--;
		} else if (verticalAmount < 0 && zoomIndex < ZOOM_LEVELS.length - 1) {
			zoomIndex++;
		}
		centerX = worldX - (mouseX - mapX() - mapWidth() / 2.0) * currentZoom();
		centerZ = worldZ - (mouseY - mapY() - mapHeight() / 2.0) * currentZoom();
		return true;
	}

	private void requestTeleport(String command, String name) {
		if (command == null || command.isBlank()) return;
		pendingTpCommand = command;
		pendingTpName = name != null ? name : command;
		overlay = Overlay.CONFIRM_TP;
	}

	private void executeTeleport() {
		String command = pendingTpCommand;
		closeOverlay();
		if (client == null || client.getNetworkHandler() == null || command == null || command.isBlank()) {
			return;
		}
		client.getNetworkHandler().sendChatCommand(command);
		close();
	}

	private void requestDelete(String id, String name) {
		pendingDeleteId = id;
		pendingDeleteName = name;
		overlay = Overlay.CONFIRM_DELETE;
	}

	private void executeDelete() {
		if (pendingDeleteId != null) {
			WaypointManager.removeCustomWaypoint(pendingDeleteId);
		}
		closeOverlay();
	}

	// ─── Overlay nom ─────────────────────────────────────────────

	private void openNameOverlay(double worldX, double worldZ, boolean rename, String waypointId) {
		pendingX = worldX;
		pendingZ = worldZ;
		pendingY = (client != null && client.player != null) ? client.player.getY() : 64;
		renaming = rename;
		renameId = waypointId;
		if (rename && waypointId != null) {
			WaypointManager.CustomWaypoint wp = WaypointManager.getCustomWaypoint(waypointId);
			if (wp != null) {
				pendingX = wp.getX();
				pendingY = wp.getY();
				pendingZ = wp.getZ();
			}
		}
		overlay = Overlay.NAME;
		addNameOverlayWidgets();
	}

	private void addNameOverlayWidgets() {
		clearNameWidgets();
		int boxW = 240;
		int boxH = 78;
		int bx = (width - boxW) / 2;
		int by = (height - boxH) / 2;
		String preset;
		if (renaming && renameId != null) {
			WaypointManager.CustomWaypoint wp = WaypointManager.getCustomWaypoint(renameId);
			preset = wp != null ? wp.getName() : "";
		} else {
			preset = "WP " + (int) Math.round(pendingX) + "," + (int) Math.round(pendingZ);
		}
		nameField = new TextFieldWidget(textRenderer, bx + 10, by + 28, boxW - 20, 18,
				Text.translatable("screen.cantale.map.waypoint_name"));
		nameField.setMaxLength(32);
		nameField.setText(preset);
		nameField.setPlaceholder(Text.translatable("screen.cantale.map.waypoint_name"));
		addDrawableChild(nameField);
		setFocused(nameField);
		nameField.setFocused(true);

		confirmBtn = ButtonWidget.builder(Text.translatable("screen.cantale.map.confirm"), b -> confirmName())
				.dimensions(bx + 10, by + 52, 105, 18).build();
		cancelBtn = ButtonWidget.builder(Text.translatable("screen.cantale.map.cancel"), b -> closeNameOverlay())
				.dimensions(bx + 125, by + 52, 105, 18).build();
		addDrawableChild(confirmBtn);
		addDrawableChild(cancelBtn);
	}

	private void clearNameWidgets() {
		if (nameField != null) {
			remove(nameField);
			nameField = null;
		}
		if (confirmBtn != null) {
			remove(confirmBtn);
			confirmBtn = null;
		}
		if (cancelBtn != null) {
			remove(cancelBtn);
			cancelBtn = null;
		}
	}

	private void closeNameOverlay() {
		clearNameWidgets();
		renaming = false;
		renameId = null;
		overlay = Overlay.NONE;
		setFocused(null);
	}

	private void closeOverlay() {
		if (overlay == Overlay.NAME) {
			closeNameOverlay();
			return;
		}
		overlay = Overlay.NONE;
		contextMarker = null;
		ctxItems.clear();
		pendingTpCommand = null;
		pendingDeleteId = null;
	}

	private void confirmName() {
		String name = nameField != null ? nameField.getText().trim() : "";
		if (name.isEmpty()) {
			name = "WP " + (int) Math.round(pendingX) + "," + (int) Math.round(pendingZ);
		}
		if (renaming && renameId != null) {
			WaypointManager.setName(renameId, name);
		} else {
			WaypointManager.addCustomWaypoint(name, CantaleMod.getServerWorldName(),
					pendingX, pendingY, pendingZ, WaypointManager.nextWaypointColor());
		}
		closeNameOverlay();
	}

	// ─── Menu contextuel ─────────────────────────────────────────

	private void openContextMenu(int mx, int my, MapCanvas.MapMarker hit) {
		contextMarker = hit;
		ctxItems.clear();
		ctxPaletteTop = -1;
		int y = 6;
		if (hit.isCustomWaypoint()) {
			ctxItems.add(new CtxItem("rename", Text.translatable("screen.cantale.map.rename").getString(), y));
			y += CTX_ITEM_H;
			ctxItems.add(new CtxItem("delete", Text.translatable("screen.cantale.map.delete").getString(), y));
			y += CTX_ITEM_H;
			boolean on = true;
			WaypointManager.CustomWaypoint wp = WaypointManager.getCustomWaypoint(hit.waypointId());
			if (wp != null) on = wp.isEnabled();
			ctxItems.add(new CtxItem("toggle", on
					? Text.translatable("screen.cantale.map.hide").getString()
					: Text.translatable("screen.cantale.map.show").getString(), y));
			y += CTX_ITEM_H;
			ctxItems.add(new CtxItem("center", Text.translatable("screen.cantale.map.center").getString(), y));
			y += CTX_ITEM_H + 2;
			ctxPaletteTop = y + 12;
			y = ctxPaletteTop + PALETTE_CELL * 2 + 8;
		} else {
			ctxItems.add(new CtxItem("teleport", Text.translatable("screen.cantale.map.teleport").getString(), y));
			y += CTX_ITEM_H;
			ctxItems.add(new CtxItem("center", Text.translatable("screen.cantale.map.center").getString(), y));
			y += CTX_ITEM_H + 6;
		}
		ctxW = CTX_W;
		ctxH = y;
		ctxX = mx + 6;
		ctxY = my + 6;
		if (ctxX + ctxW > width - 4) ctxX = mx - ctxW - 6;
		if (ctxY + ctxH > height - 4) ctxY = my - ctxH - 6;
		if (ctxX < 4) ctxX = 4;
		if (ctxY < TOP_H + 4) ctxY = TOP_H + 4;
		overlay = Overlay.CONTEXT;
	}

	private void handleContextClick(double mx, double my) {
		if (mx < ctxX || mx >= ctxX + ctxW || my < ctxY || my >= ctxY + ctxH) {
			closeOverlay();
			return;
		}
		MapCanvas.MapMarker marker = contextMarker;
		for (CtxItem item : ctxItems) {
			int iy = ctxY + item.y;
			if (my >= iy && my < iy + CTX_ITEM_H) {
				runContextAction(item.id, marker);
				return;
			}
		}
		if (ctxPaletteTop >= 0 && marker != null && marker.isCustomWaypoint()) {
			int color = colorAt(mx, my);
			if (color >= 0) {
				WaypointManager.setColor(marker.waypointId(), color);
				closeOverlay();
			}
		}
	}

	private int colorAt(double mx, double my) {
		int top = ctxY + ctxPaletteTop;
		int[] palette = WaypointManager.PALETTE;
		for (int i = 0; i < palette.length; i++) {
			int col = i % 6;
			int row = i / 6;
			int x = ctxX + 8 + col * PALETTE_CELL;
			int y = top + row * PALETTE_CELL;
			if (mx >= x && mx < x + PALETTE_CELL - 2 && my >= y && my < y + PALETTE_CELL - 2) {
				return palette[i];
			}
		}
		return -1;
	}

	private void runContextAction(String id, MapCanvas.MapMarker marker) {
		if (marker == null) {
			closeOverlay();
			return;
		}
		switch (id) {
			case "rename" -> {
				String wpId = marker.waypointId();
				closeOverlay();
				openNameOverlay(marker.x(), marker.z(), true, wpId);
			}
			case "delete" -> {
				String wpId = marker.waypointId();
				String name = marker.name();
				closeOverlay();
				requestDelete(wpId, name);
			}
			case "toggle" -> {
				WaypointManager.toggleCustomWaypoint(marker.waypointId());
				closeOverlay();
			}
			case "teleport" -> {
				String cmd = marker.command();
				String name = marker.name();
				closeOverlay();
				requestTeleport(cmd, name);
			}
			case "center" -> {
				centerOn(marker.x(), marker.z());
				closeOverlay();
			}
			default -> closeOverlay();
		}
	}

	private void handleConfirmClick(double mx, double my, boolean teleport) {
		int[] box = confirmBox();
		int bx = box[0];
		int by = box[1];
		int yesX = bx + 10;
		int noX = bx + 135;
		int btnY = by + 42;
		if (my >= btnY && my <= btnY + 18) {
			if (mx >= yesX && mx <= yesX + 110) {
				if (teleport) executeTeleport();
				else executeDelete();
				return;
			}
			if (mx >= noX && mx <= noX + 110) {
				closeOverlay();
				return;
			}
		}
		if (mx < bx || mx > bx + box[2] || my < by || my > by + box[3]) {
			closeOverlay();
		}
	}

	private int[] confirmBox() {
		int boxW = 260;
		int boxH = 72;
		return new int[]{(width - boxW) / 2, (height - boxH) / 2, boxW, boxH};
	}

	// ─── Panneau : clics ─────────────────────────────────────────

	private void handlePanelClick(double mx, double my) {
		int px = panelX();
		if (panelCollapsed) {
			panelCollapsed = false;
			rebuildCanvas();
			return;
		}
		if (mx >= px + 4 && mx <= px + 18 && my >= TOP_H + 4 && my <= TOP_H + 16) {
			panelCollapsed = true;
			rebuildCanvas();
			return;
		}

		int fy = TOP_H + 22;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowClaims);
		if (fy < 0) return;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowPublicWarps);
		if (fy < 0) return;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowPrivateHomes);
		if (fy < 0) return;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowFactionSpawn);
		if (fy < 0) return;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowCustomWaypoints);
		if (fy < 0) return;
		fy = hitFilter(mx, my, px, fy, WaypointManager::toggleShowInWorld);
		if (fy < 0) return;

		int btnY = fy + 4;
		int btnH = 16;
		int gap = 4;
		int btnW = (PANEL_W - 20 - gap) / 2;
		if (my >= btnY && my <= btnY + btnH) {
			if (mx >= px + 8 && mx <= px + 8 + btnW) {
				recenterOnPlayer();
				return;
			}
			if (mx >= px + 8 + btnW + gap && mx <= px + 8 + btnW + gap + btnW) {
				openNameOverlay(centerX, centerZ, false, null);
				return;
			}
		}

		int localTop = btnY + btnH + 8;
		if (my < localTop || my > listBottom) return;

		List<PanelRow> rows = buildPanelRows();
		int y = localTop - listScroll;
		for (PanelRow row : rows) {
			int rowBottom = y + (row.header() ? 14 : LIST_ROW_H);
			if (!row.header() && my >= y && my < rowBottom && y < listBottom && rowBottom > localTop) {
				handleRowClick(row, mx, px);
				return;
			}
			y = rowBottom;
		}
	}

	private int hitFilter(double mx, double my, int px, int fy, Runnable toggle) {
		if (my >= fy && my < fy + FILTER_ROW_H && mx >= px + 8 && mx <= px + PANEL_W - 8) {
			toggle.run();
			return -1;
		}
		return fy + FILTER_ROW_H;
	}

	private void handleRowClick(PanelRow row, double mx, int px) {
		int checkRight = px + 22;
		int actionLeft = px + PANEL_W - 36;
		if (mx < checkRight) {
			if (row.waypointId() != null) {
				WaypointManager.toggleCustomWaypoint(row.waypointId());
			} else if (row.toggleKey() != null) {
				WaypointManager.toggleServerMarker(row.toggleKey());
			}
			return;
		}
		if (mx >= actionLeft) {
			if (row.delete() && row.waypointId() != null) {
				requestDelete(row.waypointId(), row.label());
			} else if (row.command() != null) {
				requestTeleport(row.command(), row.label());
			}
			return;
		}
		centerOn(row.worldX(), row.worldZ());
	}

	// ─── Rendu ───────────────────────────────────────────────────

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		var minecraft = MinecraftClient.getInstance();
		if (minecraft.world != null && canvas != null) {
			int hoverX = isOnMap(mouseX, mouseY) && overlay == Overlay.NONE ? mouseX : Integer.MIN_VALUE;
			int hoverY = isOnMap(mouseX, mouseY) && overlay == Overlay.NONE ? mouseY : Integer.MIN_VALUE;
			canvas.draw(context, mapX(), mapY(), minecraft.world, CantaleMod.getServerWorldName(),
					centerX, centerZ, currentZoom(), true, true, hoverX, hoverY);
		}

		paintLegend(context);
		paintTopBar(context, mouseX, mouseY);
		paintPanel(context, mouseX, mouseY);

		if (isOnMap(mouseX, mouseY) && overlay == Overlay.NONE) {
			MapCanvas.MapMarker hover = hitTest(mouseX, mouseY);
			if (hover != null) {
				paintMarkerTooltip(context, mouseX, mouseY, hover);
			}
		}
		paintClaimTooltip(context);

		if (overlay == Overlay.NAME) {
			context.fill(0, 0, width, height, 0x88000000);
			int boxW = 240;
			int boxH = 78;
			int bx = (width - boxW) / 2;
			int by = (height - boxH) / 2;
			context.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, COL_BOX_EDGE);
			context.fill(bx, by, bx + boxW, by + boxH, COL_BOX);
			drawLabel(context, Text.translatable("screen.cantale.map.waypoint_name").getString(),
					bx + 10, by + 8, COL_TEXT);
		} else if (overlay == Overlay.CONFIRM_TP || overlay == Overlay.CONFIRM_DELETE) {
			paintConfirm(context, mouseX, mouseY);
		} else if (overlay == Overlay.CONTEXT) {
			paintContextMenu(context, mouseX, mouseY);
		}

		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private void paintConfirm(DrawContext context, int mouseX, int mouseY) {
		context.fill(0, 0, width, height, 0x88000000);
		int[] box = confirmBox();
		int bx = box[0];
		int by = box[1];
		int boxW = box[2];
		int boxH = box[3];
		context.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, COL_BOX_EDGE);
		context.fill(bx, by, bx + boxW, by + boxH, COL_BOX);
		String question = overlay == Overlay.CONFIRM_TP
				? Text.translatable("screen.cantale.map.confirm_tp", pendingTpName).getString()
				: Text.translatable("screen.cantale.map.confirm_delete", pendingDeleteName).getString();
		drawLabel(context, ellipsize(question, boxW - 24), bx + 10, by + 12, COL_TEXT);
		paintMiniButton(context, bx + 10, by + 42, 110, 18, mouseX, mouseY,
				Text.translatable("screen.cantale.map.yes").getString());
		paintMiniButton(context, bx + 135, by + 42, 110, 18, mouseX, mouseY,
				Text.translatable("screen.cantale.map.no").getString());
	}

	private void paintContextMenu(DrawContext context, int mouseX, int mouseY) {
		context.fill(ctxX - 1, ctxY - 1, ctxX + ctxW + 1, ctxY + ctxH + 1, COL_BOX_EDGE);
		context.fill(ctxX, ctxY, ctxX + ctxW, ctxY + ctxH, COL_BOX);
		for (CtxItem item : ctxItems) {
			int iy = ctxY + item.y;
			boolean hover = mouseX >= ctxX && mouseX < ctxX + ctxW && mouseY >= iy && mouseY < iy + CTX_ITEM_H;
			if (hover) {
				context.fill(ctxX + 2, iy, ctxX + ctxW - 2, iy + CTX_ITEM_H, COL_ROW_HOVER);
			}
			context.drawTextWithShadow(textRenderer, item.label, ctxX + 8, iy + 5, COL_TEXT);
		}
		if (ctxPaletteTop >= 0) {
			int top = ctxY + ctxPaletteTop;
			context.drawTextWithShadow(textRenderer,
					Text.translatable("screen.cantale.map.color").getString(),
					ctxX + 8, top - 10, COL_MUTED);
			int[] palette = WaypointManager.PALETTE;
			int selected = contextMarker != null ? contextMarker.rgb() : -1;
			for (int i = 0; i < palette.length; i++) {
				int col = i % 6;
				int row = i / 6;
				int x = ctxX + 8 + col * PALETTE_CELL;
				int y = top + row * PALETTE_CELL;
				int rgb = 0xFF000000 | palette[i];
				context.fill(x, y, x + PALETTE_CELL - 3, y + PALETTE_CELL - 3, 0xFF000000);
				context.fill(x + 1, y + 1, x + PALETTE_CELL - 4, y + PALETTE_CELL - 4, rgb);
				if ((palette[i] & 0xFFFFFF) == (selected & 0xFFFFFF)) {
					context.fill(x, y, x + PALETTE_CELL - 3, y + 1, 0xFFFFFFFF);
					context.fill(x, y + PALETTE_CELL - 4, x + PALETTE_CELL - 3, y + PALETTE_CELL - 3, 0xFFFFFFFF);
				}
			}
		}
	}

	private void paintTopBar(DrawContext context, int mouseX, int mouseY) {
		context.fill(0, 0, width, TOP_H, COL_TOP);
		context.fill(0, TOP_H - 1, width, TOP_H, COL_PANEL_EDGE);

		String cursor = isOnMap(mouseX, mouseY)
				? (int) Math.floor(screenToWorldX(mouseX)) + " / " + (int) Math.floor(screenToWorldZ(mouseY))
				: (int) Math.round(centerX) + " / " + (int) Math.round(centerZ);
		String left = "Cantale  ·  " + cursor + "  ·  zoom " + currentZoom() + " b/px";
		String right = ServerDataStore.isServerSupportsMod()
				? Text.translatable("screen.cantale.map.connected").getString()
				: Text.translatable("screen.cantale.map.offline").getString();
		context.drawTextWithShadow(textRenderer, left, 8, 7, COL_TEXT);
		int rw = textRenderer.getWidth(right);
		int rightX = Math.min(width - rw - panelWidth() - 8, width - rw - 8);
		if (rightX < textRenderer.getWidth(left) + 16) {
			rightX = width - rw - 8;
		}
		context.drawTextWithShadow(textRenderer, right, rightX, 7, 0xFF5C6178);
	}

	private void paintLegend(DrawContext context) {
		if (!ServerDataStore.isServerSupportsMod() || !WaypointManager.isShowClaims()) return;
		String[][] entries = {
				{"OWN", "3FD23F"}, {"ALLY", "3FB4D2"}, {"WAR", "E69B3A"},
				{"ENEMY", "D23F3F"}, {"PASDIC", "B04FD8"}, {"OTHER", "8A8A96"},
		};
		int boxH = 12 * entries.length + 8;
		int boxW = 72;
		int x = 6;
		int y = height - boxH - 6;
		context.fill(x, y, x + boxW, y + boxH, 0xEEEFF1EE);
		int ly = y + 5;
		for (String[] entry : entries) {
			int rgb = Integer.parseInt(entry[1], 16);
			context.fill(x + 5, ly + 1, x + 13, ly + 9, 0xFF000000 | rgb);
			drawLabel(context, entry[0], x + 16, ly, 0xFF2B2F4A);
			ly += 12;
		}
	}

	private void paintPanel(DrawContext context, int mouseX, int mouseY) {
		int px = panelX();
		context.fill(px, TOP_H, width, height, COL_PANEL);
		context.fill(px, TOP_H, px + 1, height, COL_PANEL_EDGE);

		if (panelCollapsed) {
			context.drawTextWithShadow(textRenderer, "»", px + 5, TOP_H + 6, COL_TEXT);
			return;
		}

		context.drawTextWithShadow(textRenderer, "«", px + 6, TOP_H + 6, COL_MUTED);
		drawLabel(context, Text.translatable("screen.cantale.map.markers").getString(),
				px + 22, TOP_H + 6, COL_TEXT);

		int fy = TOP_H + 22;
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.claims").getString(),
				WaypointManager.isShowClaims(), 0x3FD23F);
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.warps").getString(),
				WaypointManager.isShowPublicWarps(), 0x35E0D0);
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.homes").getString(),
				WaypointManager.isShowPrivateHomes(), 0x7CFC3F);
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.fw").getString(),
				WaypointManager.isShowFactionSpawn(), 0xFF6E40);
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.waypoints").getString(),
				WaypointManager.isShowCustomWaypoints(), 0xE040FB);
		fy = paintFilter(context, px, fy, mouseX, mouseY, Text.translatable("screen.cantale.map.inworld").getString(),
				WaypointManager.isShowInWorld(), 0xFFD54F);

		int btnY = fy + 4;
		int btnH = 16;
		int gap = 4;
		int btnW = (PANEL_W - 20 - gap) / 2;
		paintMiniButton(context, px + 8, btnY, btnW, btnH, mouseX, mouseY,
				Text.translatable("screen.cantale.map.recenter").getString());
		paintMiniButton(context, px + 8 + btnW + gap, btnY, btnW, btnH, mouseX, mouseY,
				Text.translatable("screen.cantale.map.add_waypoint").getString());

		listTop = btnY + btnH + 8;
		listBottom = height - HINT_H;
		List<PanelRow> rows = buildPanelRows();
		listContentHeight = 0;
		for (PanelRow row : rows) {
			listContentHeight += row.header() ? 14 : LIST_ROW_H;
		}
		int maxScroll = Math.max(0, listContentHeight - Math.max(1, listBottom - listTop));
		if (listScroll > maxScroll) listScroll = maxScroll;

		context.enableScissor(px, listTop, width, listBottom);
		int y = listTop - listScroll;
		try {
			for (PanelRow row : rows) {
				if (row.header()) {
					if (y + 14 > listTop && y < listBottom) {
						drawLabel(context, row.label(), px + 8, y + 2, 0xFF5C6178);
					}
					y += 14;
					continue;
				}
				if (y + LIST_ROW_H > listTop && y < listBottom) {
					boolean hover = mouseX >= px && mouseX < width && mouseY >= y && mouseY < y + LIST_ROW_H;
					if (hover) {
						context.fill(px + 4, y, width - 4, y + LIST_ROW_H, COL_ROW_HOVER);
					}
					paintCheckbox(context, px + 8, y + 4, row.checked());
					int nameColor = 0xFF000000 | row.color();
					String name = ellipsize(row.label(), PANEL_W - 70);
					context.drawTextWithShadow(textRenderer, name, px + 22, y + 4, nameColor);
					if (row.delete()) {
						context.fill(width - 22, y + 2, width - 8, y + 13, 0xFFC2455A);
						context.drawTextWithShadow(textRenderer, "×", width - 19, y + 3, 0xFFFFFFFF);
					} else if (row.command() != null) {
						context.fill(width - 32, y + 2, width - 8, y + 13, 0xFF2B2F4A);
						context.drawTextWithShadow(textRenderer, Text.translatable("screen.cantale.map.tp").getString(),
								width - 28, y + 3, 0xFFFFFFFF);
					}
				}
				y += LIST_ROW_H;
			}
		} finally {
			context.disableScissor();
		}

		if (listContentHeight > listBottom - listTop && listBottom - listTop > 0) {
			int trackH = listBottom - listTop;
			int thumbH = Math.max(12, trackH * trackH / listContentHeight);
			int thumbY = listTop + (int) ((trackH - thumbH) * (listScroll / (double) maxScroll));
			context.fill(width - 3, listTop, width - 1, listBottom, 0x44101018);
			context.fill(width - 3, thumbY, width - 1, thumbY + thumbH, 0xAA8A8A9A);
		}

		String hint = Text.translatable("screen.cantale.map.hint").getString();
		drawWrappedHint(context, hint, px + 6, height - HINT_H + 4, PANEL_W - 12);
	}

	private int paintFilter(DrawContext context, int px, int y, int mouseX, int mouseY,
							String label, boolean on, int rgb) {
		boolean hover = mouseX >= px + 8 && mouseX <= px + PANEL_W - 8 && mouseY >= y && mouseY < y + FILTER_ROW_H;
		if (hover) {
			context.fill(px + 4, y, width - 4, y + FILTER_ROW_H, COL_ROW_HOVER);
		}
		paintCheckbox(context, px + 8, y + 4, on);
		context.fill(px + 22, y + 5, px + 30, y + 13, 0xFF000000 | rgb);
		context.drawTextWithShadow(textRenderer, label, px + 34, y + 4, COL_TEXT);
		return y + FILTER_ROW_H;
	}

	private void paintMiniButton(DrawContext context, int x, int y, int w, int h,
								 int mouseX, int mouseY, String label) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		context.fill(x, y, x + w, y + h, hover ? 0xFF2A2A3C : 0xFF1A1A28);
		context.fill(x, y, x + w, y + 1, COL_PANEL_EDGE);
		int tw = textRenderer.getWidth(label);
		context.drawTextWithShadow(textRenderer, label, x + Math.max(2, (w - tw) / 2), y + 4, COL_TEXT);
	}

	private void paintCheckbox(DrawContext context, int x, int y, boolean on) {
		context.fill(x, y, x + 8, y + 8, 0xFF2A2A38);
		context.fill(x + 1, y + 1, x + 7, y + 7, 0xFF12121A);
		if (on) {
			context.fill(x + 2, y + 2, x + 6, y + 6, 0xFF3FD23F);
		}
	}

	private void paintMarkerTooltip(DrawContext context, int mouseX, int mouseY, MapCanvas.MapMarker hover) {
		String type = switch (hover.kind()) {
			case WARP -> Text.translatable("screen.cantale.map.tooltip.warp").getString();
			case HOME -> Text.translatable("screen.cantale.map.tooltip.home").getString();
			case FACTION_SPAWN -> Text.translatable("screen.cantale.map.tooltip.fw").getString();
			case WAYPOINT -> Text.translatable("screen.cantale.map.tooltip.waypoint").getString();
		};
		String coords = (int) Math.round(hover.x()) + " / " + (int) Math.round(hover.z());
		List<String> lines = new ArrayList<>();
		lines.add(hover.name());
		lines.add(type);
		lines.add(coords);
		if (hover.canTeleport()) {
			lines.add(Text.translatable("screen.cantale.map.tooltip.tp").getString());
		} else {
			lines.add(Text.translatable("screen.cantale.map.tooltip.rightclick").getString());
		}

		int boxW = 8;
		for (String line : lines) {
			boxW = Math.max(boxW, textRenderer.getWidth(line) + 10);
		}
		int boxH = lines.size() * 11 + 6;
		int tx = mouseX + 14;
		int ty = mouseY + 14;
		if (tx + boxW > mapX() + mapWidth() - 4) tx = mouseX - boxW - 8;
		if (ty + boxH > height - 4) ty = mouseY - boxH - 8;
		context.fill(tx, ty, tx + boxW, ty + boxH, 0xF0EEF1EE);
		context.fill(tx, ty, tx + 2, ty + boxH, 0xFF000000 | hover.rgb());
		int ly = ty + 4;
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? (0xFF000000 | hover.rgb()) : COL_MUTED;
			context.drawTextWithShadow(textRenderer, lines.get(i), tx + 6, ly, color);
			ly += 11;
		}
	}

	private void drawLabel(DrawContext context, String text, int x, int y, int color) {
		int w = textRenderer.getWidth(text);
		context.fill(x - 2, y - 1, x + w + 2, y + 10, 0xEEEFF1EE);
		context.drawTextWithShadow(textRenderer, text, x, y, color);
	}

	private void drawWrappedHint(DrawContext context, String hint, int x, int y, int maxW) {
		if (textRenderer.getWidth(hint) <= maxW) {
			context.drawTextWithShadow(textRenderer, hint, x, y, COL_MUTED);
			return;
		}
		String[] words = hint.split(" ");
		StringBuilder line = new StringBuilder();
		int ly = y;
		for (String word : words) {
			String trial = line.isEmpty() ? word : line + " " + word;
			if (textRenderer.getWidth(trial) > maxW && !line.isEmpty()) {
				context.drawTextWithShadow(textRenderer, line.toString(), x, ly, COL_MUTED);
				ly += 9;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(trial);
			}
		}
		if (!line.isEmpty()) {
			context.drawTextWithShadow(textRenderer, line.toString(), x, ly, COL_MUTED);
		}
	}

	private String ellipsize(String text, int maxWidthPx) {
		if (textRenderer.getWidth(text) <= maxWidthPx) return text;
		String ellipsis = "…";
		int max = maxWidthPx - textRenderer.getWidth(ellipsis);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (textRenderer.getWidth(sb.toString() + text.charAt(i)) > max) break;
			sb.append(text.charAt(i));
		}
		return sb + ellipsis;
	}

	private List<PanelRow> buildPanelRows() {
		List<PanelRow> rows = new ArrayList<>();
		String world = CantaleMod.getServerWorldName();

		List<ServerDataStore.Marker> homes = ServerDataStore.markersIn(ServerDataStore.homes(), world);
		if (!homes.isEmpty()) {
			rows.add(PanelRow.header(Text.translatable("screen.cantale.map.section.homes").getString()));
			for (ServerDataStore.Marker home : homes) {
				String key = WaypointManager.homeKey(home.name());
				rows.add(new PanelRow(false, home.name(), 0x7CFC3F, WaypointManager.isMarkerEnabled(key),
						key, null, "home " + home.name(), false, home.x(), home.z()));
			}
		}

		List<ServerDataStore.Marker> warps = ServerDataStore.markersIn(ServerDataStore.warps(), world);
		if (!warps.isEmpty()) {
			rows.add(PanelRow.header(Text.translatable("screen.cantale.map.section.warps").getString()));
			for (ServerDataStore.Marker warp : warps) {
				String key = WaypointManager.warpKey(warp.name());
				rows.add(new PanelRow(false, warp.name(), warp.event() ? 0xFFD24A : 0x35E0D0,
						WaypointManager.isMarkerEnabled(key),
						key, null, "warp " + warp.name(), false, warp.x(), warp.z()));
			}
		}

		ServerDataStore.Marker fspawn = ServerDataStore.factionSpawn();
		if (fspawn != null && world.equals(fspawn.world())) {
			rows.add(PanelRow.header(Text.translatable("screen.cantale.map.section.fw").getString()));
			rows.add(new PanelRow(false, fspawn.name(), 0xFF6E40,
					WaypointManager.isMarkerEnabled(WaypointManager.FACTION_SPAWN_KEY),
					WaypointManager.FACTION_SPAWN_KEY, null, "f spawn", false, fspawn.x(), fspawn.z()));
		}

		List<WaypointManager.CustomWaypoint> wps = WaypointManager.listCustomWaypointsInWorld(world, false);
		if (!wps.isEmpty()) {
			rows.add(PanelRow.header(Text.translatable("screen.cantale.map.section.waypoints").getString()));
			for (WaypointManager.CustomWaypoint wp : wps) {
				rows.add(new PanelRow(false, wp.getName(), wp.getColor(), wp.isEnabled(),
						null, wp.getId(), null, true, wp.getX(), wp.getZ()));
			}
		}
		return rows;
	}

	/**
	 * Un clic immobile, hors marqueur, demande au serveur la fiche du claim.
	 */
	private void inspectAt(double mouseX, double mouseY) {
		double worldX = screenToWorldX(mouseX);
		double worldZ = screenToWorldZ(mouseY);
		int chunkX = (int) Math.floor(worldX / 16.0);
		int chunkZ = (int) Math.floor(worldZ / 16.0);
		String world = CantaleMod.getServerWorldName();
		ServerDataStore.ClaimCell cell = ServerDataStore.cellAt(world, chunkX, chunkZ);
		if (cell == null) {
			ServerDataStore.clearTooltip();
			return;
		}
		ServerDataStore.FactionCard card = ServerDataStore.faction(cell.factionId());
		String faction = card != null ? card.name() : "";
		String leader = card != null ? card.leader() : "";
		tipAnchorX = (int) mouseX;
		tipAnchorY = (int) mouseY;
		int seq = ServerDataStore.openTooltip(world, chunkX, chunkZ, cell.factionId(), cell.rel(), faction, leader);
		CantaleNetworkHandler.sendInspect(MinecraftClient.getInstance(), world, chunkX, chunkZ, seq);
	}

	private void paintClaimTooltip(DrawContext context) {
		ServerDataStore.ClaimTooltip tip = ServerDataStore.tooltip();
		if (tip == null) return;
		if (tip.seq() != tipSeq || !tip.faction().equals(tipFaction)
				|| !tip.leader().equals(tipLeader) || !tip.rel().equals(tipRel)) {
			tipSeq = tip.seq();
			tipFaction = tip.faction();
			tipLeader = tip.leader();
			tipRel = tip.rel();
			tipLineFaction = tipFaction.isEmpty()
					? Text.translatable("cantale.map.unknown_faction")
					: Text.literal(tipFaction);
			Text leader = tipLeader.isEmpty()
					? Text.translatable("cantale.map.unknown_leader")
					: Text.literal(tipLeader);
			tipLineLeader = Text.translatable("cantale.map.leader", leader);
			tipLineRelation = Text.translatable("cantale.map.relation",
					Text.translatable(ClaimColors.translationKey(tipRel)));
		}

		int textW = Math.max(textRenderer.getWidth(tipLineFaction),
				Math.max(textRenderer.getWidth(tipLineLeader), textRenderer.getWidth(tipLineRelation)));
		int boxW = textW + 16;
		int boxH = 40;
		int tx = Math.min(Math.max(2, tipAnchorX + 12), Math.max(2, width - boxW - 2));
		int ty = Math.min(Math.max(TOP_H + 2, tipAnchorY + 12), Math.max(TOP_H + 2, height - boxH - 2));
		int rgb = ClaimColors.rgb(tipRel);

		context.fill(tx, ty, tx + boxW, ty + boxH, 0xF014141C);
		context.fill(tx, ty, tx + 2, ty + boxH, 0xFF000000 | rgb);
		context.drawTextWithShadow(textRenderer, tipLineFaction, tx + 8, ty + 4, 0xFFF4F4FA);
		context.drawTextWithShadow(textRenderer, tipLineLeader, tx + 8, ty + 15, 0xFFD0D0DC);
		context.drawTextWithShadow(textRenderer, tipLineRelation, tx + 8, ty + 26, 0xFF000000 | rgb);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private record CtxItem(String id, String label, int y) {
	}

	private record PanelRow(
			boolean header,
			String label,
			int color,
			boolean checked,
			String toggleKey,
			String waypointId,
			String command,
			boolean delete,
			double worldX,
			double worldZ
	) {
		static PanelRow header(String label) {
			return new PanelRow(true, label, 0, false, null, null, null, false, 0, 0);
		}
	}
}
