package fr.cantale.client.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.data.ServerDataStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

/**
 * Réseau du mod : handshake {@code HELLO <version>} à la connexion, réception des messages
 * {@code cantale:map} et application différée sur le thread client (file thread-safe).
 *
 * Règles d'or : tout le parsing est en try/catch (jamais de crash sur données inattendues),
 * la version du protocole est vérifiée, et sans serveur Cantale le mod reste silencieux.
 */
public final class CantaleNetworkHandler {

	/** Version du protocole parlée par ce client (cf. ClientMapBridge côté plugin). */
	public static final int PROTOCOL_VERSION = 1;

	/** Renvoi du HELLO si aucun welcome n'arrive (serveur lent à enregistrer le canal). */
	private static final int HELLO_RETRY_TICKS = 100;

	/** Messages bruts reçus (thread Netty) en attente d'application (thread client). */
	private static final Queue<String> incoming = new ConcurrentLinkedQueue<>();
	/** File bornée : au-delà, on jette (protection anti-flood d'un serveur défectueux). */
	private static final int MAX_QUEUED = 512;

	private static int ticksBeforeHelloRetry = -1;

	private CantaleNetworkHandler() {
	}

	/** Enregistre les codecs, le receveur et les événements de connexion. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(CantaleMapPayload.ID, CantaleMapPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CantaleMapPayload.ID, CantaleMapPayload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(CantaleMapPayload.ID, (payload, context) -> {
			try {
				if (incoming.size() < MAX_QUEUED) {
					incoming.offer(new String(payload.data(), StandardCharsets.UTF_8));
				}
			} catch (Exception e) {
				CantaleMod.LOGGER.debug("[Cantale] Message cantale:map illisible : {}", e.toString());
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerDataStore.reset();
			incoming.clear();
			sendHello(client);
			ticksBeforeHelloRetry = HELLO_RETRY_TICKS;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			incoming.clear();
			ticksBeforeHelloRetry = -1;
			ServerDataStore.reset();
		});
	}

	/** Appelé à chaque tick client : applique les messages reçus et gère le retry du handshake. */
	public static void tick(MinecraftClient client) {
		String json;
		while ((json = incoming.poll()) != null) {
			apply(json);
		}
		if (ticksBeforeHelloRetry > 0 && !ServerDataStore.isServerSupportsMod()) {
			if (--ticksBeforeHelloRetry == 0) {
				sendHello(client);
			}
		}
	}

	/** Envoie le handshake au serveur (sans effet sur un serveur sans le plugin Cantale). */
	public static void sendHello(MinecraftClient client) {
		try {
			if (client == null || client.getNetworkHandler() == null) return;
			byte[] data = ("HELLO " + PROTOCOL_VERSION).getBytes(StandardCharsets.UTF_8);
			sendIntent(client, "hello", data);
		} catch (Exception e) {
			CantaleMod.LOGGER.debug("[Cantale] Échec envoi du handshake : {}", e.toString());
		}
	}

	/**
	 * Intention d'affichage : le serveur revalide le chunk et répond par {@code claim_info}.
	 * Aucun effet de jeu n'est demandé.
	 */
	public static void sendInspect(MinecraftClient client, String world, int chunkX, int chunkZ, int seq) {
		try {
			if (client == null || client.getNetworkHandler() == null) return;
			if (seq <= 0 || world == null || world.isEmpty()) return;
			JsonObject msg = new JsonObject();
			msg.addProperty("v", PROTOCOL_VERSION);
			msg.addProperty("type", "inspect");
			msg.addProperty("world", world);
			msg.addProperty("x", chunkX);
			msg.addProperty("z", chunkZ);
			msg.addProperty("seq", seq);
			sendIntent(client, "inspect", msg.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			CantaleMod.LOGGER.debug("[Cantale] Échec de l'intention inspect : {}", e.toString());
		}
	}

	/**
	 * Le client n'émet que {@code hello} et {@code inspect}. Toute autre intention
	 * (claim, économie, monde) est bloquée ici : le serveur reste la source de vérité.
	 */
	private static void sendIntent(MinecraftClient client, String type, byte[] data) {
		if (!"hello".equals(type) && !"inspect".equals(type)) {
			CantaleMod.LOGGER.warn("[Cantale] Intention bloquée côté client : {}", type);
			return;
		}
		if (client == null || client.getNetworkHandler() == null) return;
		if (data == null || data.length == 0 || data.length > 1024) return;
		ClientPlayNetworking.send(new CantaleMapPayload(data));
	}

	// ─── Décodage des messages serveur ───────────────────────────

	private static void apply(String json) {
		try {
			JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
			int version = optInt(msg, "v", 1);
			if (version > PROTOCOL_VERSION) {
				// Protocole plus récent que le client : on ignore proprement
				CantaleMod.LOGGER.debug("[Cantale] Message protocole v{} ignoré (client v{}).", version, PROTOCOL_VERSION);
				return;
			}
			String type = optString(msg, "type");
			if (type == null) return;

			switch (type) {
				case "welcome" -> handleWelcome(msg);
				case "claims_reset" -> ServerDataStore.applyClaimsReset();
				case "claims" -> handleClaims(msg);
				case "claim_add" -> ServerDataStore.applyClaimAdd(
						optString(msg, "world"), optInt(msg, "x", 0), optInt(msg, "z", 0), optString(msg, "rel"),
						optInt(msg, "fid", 0), clean(optString(msg, "faction"), 16), clean(optString(msg, "leader"), 16));
				case "claim_remove" -> ServerDataStore.applyClaimRemove(
						optString(msg, "world"), optInt(msg, "x", 0), optInt(msg, "z", 0));
				case "markers" -> handleMarkers(msg);
				case "faction_index" -> handleFactionIndex(msg);
				case "hud" -> handleHud(msg);
				case "claim_info" -> handleClaimInfo(msg);
				case "cosmetics" -> handleCosmetics(msg);
				default -> CantaleMod.LOGGER.debug("[Cantale] Type de message inconnu : {}", type);
			}
		} catch (Exception e) {
			// Données corrompues ou inattendues : jamais de crash, on trace en debug
			CantaleMod.LOGGER.debug("[Cantale] Message cantale:map ignoré ({}).", e.toString());
		}
	}

	private static void handleWelcome(JsonObject msg) {
		int protocol = optInt(msg, "protocol", 1);
		Map<String, String> worlds = new LinkedHashMap<>();
		JsonArray worldsArray = msg.has("worlds") && msg.get("worlds").isJsonArray()
				? msg.getAsJsonArray("worlds") : null;
		if (worldsArray != null) {
			for (JsonElement element : worldsArray) {
				try {
					JsonObject w = element.getAsJsonObject();
					String name = optString(w, "name");
					String dim = optString(w, "dim");
					if (name != null && dim != null) {
						worlds.put(dim, name);
					}
				} catch (Exception ignored) {
					// Entrée monde malformée : on saute celle-ci seulement
				}
			}
		}
		ServerDataStore.applyWelcome(protocol, worlds);
		CantaleMod.LOGGER.info("[Cantale] Serveur Cantale détecté (protocole v{}) — carte synchronisée.", protocol);
	}

	private static void handleClaims(JsonObject msg) {
		String world = optString(msg, "world");
		if (world == null) return;
		int page = optInt(msg, "page", 0);
		int pages = optInt(msg, "pages", 1);
		List<ServerDataStore.ClaimChunk> chunks = new ArrayList<>();
		JsonArray array = msg.has("chunks") && msg.get("chunks").isJsonArray() ? msg.getAsJsonArray("chunks") : null;
		if (array != null) {
			for (JsonElement element : array) {
				try {
					JsonObject c = element.getAsJsonObject();
					String rel = optString(c, "rel");
					if (rel != null) {
						chunks.add(new ServerDataStore.ClaimChunk(
								optInt(c, "x", 0), optInt(c, "z", 0), rel, optInt(c, "fid", 0)));
					}
				} catch (Exception ignored) {
					// Chunk malformé : ignoré individuellement
				}
			}
		}
		ServerDataStore.applyClaimsPage(world, page, pages, chunks);
	}

	private static void handleMarkers(JsonObject msg) {
		List<ServerDataStore.Marker> warps = parseMarkers(msg, "warps", true);
		List<ServerDataStore.Marker> homes = parseMarkers(msg, "homes", false);
		ServerDataStore.Marker fspawn = null;
		if (msg.has("fspawn") && msg.get("fspawn").isJsonObject()) {
			try {
				fspawn = marker(msg.getAsJsonObject("fspawn"), false);
			} catch (Exception ignored) {
				// fspawn malformé : simplement absent
			}
		}
		ServerDataStore.applyMarkers(warps, homes, fspawn);
	}

	private static List<ServerDataStore.Marker> parseMarkers(JsonObject msg, String key, boolean warpKind) {
		List<ServerDataStore.Marker> out = new ArrayList<>();
		JsonArray array = msg.has(key) && msg.get(key).isJsonArray() ? msg.getAsJsonArray(key) : null;
		if (array == null) return out;
		for (JsonElement element : array) {
			try {
				ServerDataStore.Marker marker = marker(element.getAsJsonObject(), warpKind);
				if (marker != null) out.add(marker);
			} catch (Exception ignored) {
				// Marqueur malformé : ignoré individuellement
			}
		}
		return out;
	}

	private static ServerDataStore.Marker marker(JsonObject obj, boolean warpKind) {
		String name = optString(obj, "name");
		String world = optString(obj, "world");
		if (name == null || world == null) return null;
		boolean event = warpKind && obj.has("event") && obj.get("event").getAsBoolean();
		return new ServerDataStore.Marker(name, world,
				optDouble(obj, "x"), optDouble(obj, "y"), optDouble(obj, "z"), event);
	}

	private static void handleFactionIndex(JsonObject msg) {
		int page = optInt(msg, "page", 0);
		int pages = optInt(msg, "pages", 1);
		List<ServerDataStore.FactionCard> entries = new ArrayList<>();
		JsonArray array = msg.has("entries") && msg.get("entries").isJsonArray() ? msg.getAsJsonArray("entries") : null;
		if (array != null) {
			int count = 0;
			for (JsonElement element : array) {
				if (count++ >= 64) break;
				try {
					JsonObject entry = element.getAsJsonObject();
					int id = optInt(entry, "id", 0);
					if (id <= 0) continue;
					entries.add(new ServerDataStore.FactionCard(id,
							clean(optString(entry, "name"), 16),
							clean(optString(entry, "leader"), 16),
							clean(optString(entry, "rel"), 16)));
				} catch (Exception ignored) {
					// Fiche malformée : ignorée individuellement
				}
			}
		}
		ServerDataStore.applyFactionIndexPage(page, pages, entries);
	}

	private static void handleHud(JsonObject msg) {
		int lives = Math.max(0, Math.min(optInt(msg, "lives", 0), 99));
		int maxLives = Math.max(1, Math.min(optInt(msg, "maxLives", 10), 10));
		boolean unlimited = false;
		try {
			unlimited = msg.has("unlimited") && !msg.get("unlimited").isJsonNull() && msg.get("unlimited").getAsBoolean();
		} catch (Exception ignored) {
			unlimited = false;
		}
		int combatMs = Math.max(0, Math.min(optInt(msg, "combatMs", 0), 600_000));
		int combatMaxMs = Math.max(1, Math.min(optInt(msg, "combatMaxMs", 20_000), 600_000));
		ServerDataStore.applyHud(lives, maxLives, unlimited, combatMs, combatMaxMs, clean(optString(msg, "opponent"), 16));
	}

	private static void handleCosmetics(JsonObject msg) {
		Map<String, ServerDataStore.CosmeticLook> next = new LinkedHashMap<>();
		if (!msg.has("players") || !msg.get("players").isJsonArray()) {
			ServerDataStore.applyCosmetics(next);
			return;
		}
		int kept = 0;
		for (JsonElement element : msg.getAsJsonArray("players")) {
			if (kept >= 100) {
				break;
			}
			try {
				JsonObject row = element.getAsJsonObject();
				String id = clean(optString(row, "id"), 36);
				String name = clean(optString(row, "name"), 16);
				String title = clean(optString(row, "title"), 32);
				String aura = clean(optString(row, "aura"), 32);
				String cape = clean(optString(row, "cape"), 32);
				if (title == null) title = "";
				if (aura == null) aura = "";
				if (cape == null) cape = "";
				ServerDataStore.CosmeticLook look = new ServerDataStore.CosmeticLook(title, aura, cape);
				if (id != null) {
					next.put(id, look);
				}
				if (name != null) {
					next.put(name.toLowerCase(java.util.Locale.ROOT), look);
				}
				kept++;
			} catch (Exception ignored) {
				// Ligne cosmétique malformée : on saute celle-ci seulement
			}
		}
		ServerDataStore.applyCosmetics(next);
	}

	private static void handleClaimInfo(JsonObject msg) {
		int seq = optInt(msg, "seq", -1);
		if (seq <= 0) return;
		boolean found = false;
		try {
			found = msg.has("found") && !msg.get("found").isJsonNull() && msg.get("found").getAsBoolean();
		} catch (Exception ignored) {
			found = false;
		}
		ServerDataStore.applyClaimInfo(seq, found,
				clean(optString(msg, "world"), 64),
				optInt(msg, "x", 0),
				optInt(msg, "z", 0),
				clean(optString(msg, "rel"), 16),
				clean(optString(msg, "faction"), 16),
				clean(optString(msg, "leader"), 16));
	}

	/** Retire les caractères de contrôle et borne la longueur (réseau, pas le rendu). */
	private static String clean(String raw, int max) {
		if (raw == null || raw.isEmpty() || max <= 0) return "";
		StringBuilder sb = new StringBuilder(Math.min(raw.length(), max));
		int n = Math.min(raw.length(), max);
		for (int i = 0; i < n; i++) {
			char c = raw.charAt(i);
			if (c >= 32 && c != 127) sb.append(c);
		}
		return sb.toString();
	}

	// ─── Lecture JSON défensive ──────────────────────────────────

	private static String optString(JsonObject obj, String key) {
		try {
			return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static int optInt(JsonObject obj, String key, int fallback) {
		try {
			return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double optDouble(JsonObject obj, String key) {
		try {
			return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : 0.0;
		} catch (Exception e) {
			return 0.0;
		}
	}
}
