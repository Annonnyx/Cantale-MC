package fr.cantale.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import fr.cantale.client.CantaleMod;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les waypoints personnels du joueur (sauvegardés localement dans
 * {@code config/cantale_waypoints.json}) ainsi que les filtres d'affichage
 * (Claims, Warps, Homes, spawn de faction, Waypoints personnels) et les
 * cases individuelles des marqueurs serveur.
 */
public final class WaypointManager {

	/** Clé stable du marqueur /f spawn (indépendante du nom de faction). */
	public static final String FACTION_SPAWN_KEY = "fw";

	public static class CustomWaypoint {
		private String id;
		private String name;
		private String world;
		private double x;
		private double y;
		private double z;
		private int color;
		private boolean enabled = true;

		public CustomWaypoint() {
		}

		public CustomWaypoint(String id, String name, String world, double x, double y, double z, int color) {
			this.id = id;
			this.name = name;
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.enabled = true;
		}

		public String getId() { return id; }
		public String getName() { return name; }
		public String getWorld() { return world; }
		public double getX() { return x; }
		public double getY() { return y; }
		public double getZ() { return z; }
		public int getColor() { return color; }
		public boolean isEnabled() { return enabled; }
		public void setName(String name) { this.name = name; }
		public void setColor(int color) { this.color = color; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type WAYPOINT_LIST_TYPE = new TypeToken<List<CustomWaypoint>>() {}.getType();
	private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

	/** Palette persistée (choix clic-droit) : 12 couleurs distinctes. */
	public static final int[] PALETTE = {
			0xE040FB, 0x40C4FF, 0xFFD54F, 0x69F0AE, 0xFF8A65, 0x82B1FF,
			0xFF5252, 0xF5F5F5, 0xFFAB40, 0x7C4DFF, 0x18FFFF, 0xB2FF59
	};

	private static final List<CustomWaypoint> customWaypoints = Collections.synchronizedList(new ArrayList<>());
	private static final Set<String> disabledServerMarkers = ConcurrentHashMap.newKeySet();

	private static volatile boolean showClaims = true;
	private static volatile boolean showPublicWarps = true;
	private static volatile boolean showPrivateHomes = true;
	private static volatile boolean showCustomWaypoints = true;
	private static volatile boolean showFactionSpawn = true;
	private static volatile boolean showInWorld = true;
	private static volatile int nextColorIndex = 0;

	static {
		load();
	}

	private WaypointManager() {
	}

	public static String warpKey(String name) {
		return "warp:" + name;
	}

	public static String homeKey(String name) {
		return "home:" + name;
	}

	public static File getConfigFile() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.runDirectory == null) {
			return null;
		}
		File configDir = new File(client.runDirectory, "config");
		if (!configDir.exists()) {
			configDir.mkdirs();
		}
		return new File(configDir, "cantale_waypoints.json");
	}

	public static void load() {
		File file = getConfigFile();
		if (file == null || !file.exists()) return;
		try (FileReader reader = new FileReader(file)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || root.isJsonNull()) return;
			if (root.isJsonArray()) {
				List<CustomWaypoint> loaded = GSON.fromJson(root, WAYPOINT_LIST_TYPE);
				replaceWaypoints(loaded);
				return;
			}
			if (!root.isJsonObject()) return;
			JsonObject obj = root.getAsJsonObject();
			JsonObject filters = obj.has("filters") && obj.get("filters").isJsonObject()
					? obj.getAsJsonObject("filters")
					: obj;
			showClaims = bool(filters, "claims", "showClaims", true);
			showPublicWarps = bool(filters, "warps", "showPublicWarps", true);
			showPrivateHomes = bool(filters, "homes", "showPrivateHomes", true);
			showCustomWaypoints = bool(filters, "waypoints", "showCustomWaypoints", true);
			showFactionSpawn = bool(filters, "factionSpawn", "showFactionSpawn", true);
			showInWorld = bool(filters, "inWorld", "showInWorld", true);
			if (obj.has("nextColorIndex") && obj.get("nextColorIndex").isJsonPrimitive()) {
				nextColorIndex = Math.floorMod(obj.get("nextColorIndex").getAsInt(), PALETTE.length);
			}

			if (obj.has("waypoints")) {
				replaceWaypoints(GSON.fromJson(obj.get("waypoints"), WAYPOINT_LIST_TYPE));
			}
			disabledServerMarkers.clear();
			if (obj.has("disabledServerMarkers")) {
				List<String> disabled = GSON.fromJson(obj.get("disabledServerMarkers"), STRING_LIST_TYPE);
				if (disabled != null) {
					disabledServerMarkers.addAll(disabled);
				}
			}
		} catch (Exception e) {
			CantaleMod.LOGGER.warn("[Cantale] Impossible de charger cantale_waypoints.json: {}", e.getMessage());
		}
	}

	public static void save() {
		File file = getConfigFile();
		if (file == null) return;
		try (FileWriter writer = new FileWriter(file)) {
			JsonObject root = new JsonObject();
			JsonObject filters = new JsonObject();
			filters.addProperty("claims", showClaims);
			filters.addProperty("warps", showPublicWarps);
			filters.addProperty("homes", showPrivateHomes);
			filters.addProperty("waypoints", showCustomWaypoints);
			filters.addProperty("factionSpawn", showFactionSpawn);
			filters.addProperty("inWorld", showInWorld);
			root.add("filters", filters);
			root.addProperty("nextColorIndex", nextColorIndex);

			List<CustomWaypoint> snapshot;
			synchronized (customWaypoints) {
				snapshot = new ArrayList<>(customWaypoints);
			}
			root.add("waypoints", GSON.toJsonTree(snapshot));

			JsonArray disabled = new JsonArray();
			for (String key : disabledServerMarkers) {
				disabled.add(key);
			}
			root.add("disabledServerMarkers", disabled);
			GSON.toJson(root, writer);
		} catch (Exception e) {
			CantaleMod.LOGGER.warn("[Cantale] Impossible de sauvegarder cantale_waypoints.json: {}", e.getMessage());
		}
	}

	public static void addCustomWaypoint(String name, String world, double x, double y, double z, int color) {
		String id = "custom_" + System.currentTimeMillis();
		CustomWaypoint wp = new CustomWaypoint(id, name, world, x, y, z, color);
		customWaypoints.add(wp);
		save();
	}

	/** Couleur suivante de la palette, pour distinguer visuellement les waypoints. */
	public static int nextWaypointColor() {
		int color = PALETTE[Math.floorMod(nextColorIndex, PALETTE.length)];
		nextColorIndex = Math.floorMod(nextColorIndex + 1, PALETTE.length);
		return color;
	}

	public static CustomWaypoint getCustomWaypoint(String id) {
		if (id == null) return null;
		synchronized (customWaypoints) {
			for (CustomWaypoint wp : customWaypoints) {
				if (id.equals(wp.getId())) return wp;
			}
		}
		return null;
	}

	public static void setName(String id, String name) {
		CustomWaypoint wp = getCustomWaypoint(id);
		if (wp == null || name == null || name.isBlank()) return;
		wp.setName(name.trim());
		save();
	}

	public static void setColor(String id, int color) {
		CustomWaypoint wp = getCustomWaypoint(id);
		if (wp == null) return;
		wp.setColor(color & 0xFFFFFF);
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == (color & 0xFFFFFF)) {
				nextColorIndex = Math.floorMod(i + 1, PALETTE.length);
				break;
			}
		}
		save();
	}

	public static void setEnabled(String id, boolean enabled) {
		CustomWaypoint wp = getCustomWaypoint(id);
		if (wp == null) return;
		wp.setEnabled(enabled);
		save();
	}

	public static void removeCustomWaypoint(String id) {
		customWaypoints.removeIf(wp -> wp.getId().equals(id));
		save();
	}

	public static void toggleCustomWaypoint(String id) {
		CustomWaypoint wp = getCustomWaypoint(id);
		if (wp == null) return;
		wp.setEnabled(!wp.isEnabled());
		save();
	}

	public static List<CustomWaypoint> getCustomWaypoints() {
		synchronized (customWaypoints) {
			return new ArrayList<>(customWaypoints);
		}
	}

	/** Waypoints visibles sur la carte (activés) dans ce monde. */
	public static List<CustomWaypoint> getCustomWaypointsInWorld(String world) {
		return listCustomWaypointsInWorld(world, true);
	}

	/** Tous les waypoints du monde, y compris ceux décochés dans la liste. */
	public static List<CustomWaypoint> listCustomWaypointsInWorld(String world, boolean enabledOnly) {
		List<CustomWaypoint> result = new ArrayList<>();
		synchronized (customWaypoints) {
			for (CustomWaypoint wp : customWaypoints) {
				if (enabledOnly && !wp.isEnabled()) continue;
				if (wp.getWorld() == null || wp.getWorld().equals(world)) {
					result.add(wp);
				}
			}
		}
		return result;
	}

	public static boolean isMarkerEnabled(String key) {
		return !disabledServerMarkers.contains(key);
	}

	public static void toggleServerMarker(String key) {
		if (disabledServerMarkers.contains(key)) {
			disabledServerMarkers.remove(key);
		} else {
			disabledServerMarkers.add(key);
		}
		save();
	}

	public static boolean isShowClaims() { return showClaims; }
	public static void toggleShowClaims() { setShowClaims(!showClaims); }
	public static void setShowClaims(boolean value) { showClaims = value; save(); }

	public static boolean isShowPublicWarps() { return showPublicWarps; }
	public static void toggleShowPublicWarps() { setShowPublicWarps(!showPublicWarps); }
	public static void setShowPublicWarps(boolean value) { showPublicWarps = value; save(); }

	public static boolean isShowPrivateHomes() { return showPrivateHomes; }
	public static void toggleShowPrivateHomes() { setShowPrivateHomes(!showPrivateHomes); }
	public static void setShowPrivateHomes(boolean value) { showPrivateHomes = value; save(); }

	public static boolean isShowCustomWaypoints() { return showCustomWaypoints; }
	public static void toggleShowCustomWaypoints() { setShowCustomWaypoints(!showCustomWaypoints); }
	public static void setShowCustomWaypoints(boolean value) { showCustomWaypoints = value; save(); }

	public static boolean isShowFactionSpawn() { return showFactionSpawn; }
	public static void toggleShowFactionSpawn() { setShowFactionSpawn(!showFactionSpawn); }
	public static void setShowFactionSpawn(boolean value) { showFactionSpawn = value; save(); }

	public static boolean isShowInWorld() { return showInWorld; }
	public static void toggleShowInWorld() { setShowInWorld(!showInWorld); }
	public static void setShowInWorld(boolean value) { showInWorld = value; save(); }

	private static void replaceWaypoints(List<CustomWaypoint> loaded) {
		customWaypoints.clear();
		if (loaded != null) {
			customWaypoints.addAll(loaded);
		}
	}

	private static boolean bool(JsonObject obj, String key, String altKey, boolean fallback) {
		if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
			return obj.get(key).getAsBoolean();
		}
		if (obj.has(altKey) && obj.get(altKey).isJsonPrimitive()) {
			return obj.get(altKey).getAsBoolean();
		}
		return fallback;
	}
}
