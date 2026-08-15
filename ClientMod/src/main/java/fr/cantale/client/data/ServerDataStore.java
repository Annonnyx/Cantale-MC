package fr.cantale.client.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.world.ClientWorld;

/**
 * Stockage thread-safe des données reçues du serveur Cantale (canal {@code cantale:map}).
 *
 * Le réseau dépose les messages bruts dans une file ; l'application se fait sur le thread
 * client (tick). Les structures internes sont concurrentes / publiées en volatile pour que
 * le rendu ne lise jamais un état incohérent. Aucune donnée serveur n'est requise : sans
 * handshake, ce store reste vide et la carte n'affiche que le terrain.
 */
public final class ServerDataStore {

	/** Chunk claimé reçu du serveur. rel ∈ OWN | ALLY | WAR | ENEMY | OTHER | PASDIC. */
	public record ClaimChunk(int x, int z, String rel) {
	}

	/** Marqueur serveur (warp public, home du joueur, spawn de faction). */
	public record Marker(String name, String world, double x, double y, double z, boolean event) {
	}

	/** Claims appliqués : monde serveur → (chunk packé → relation). */
	private static final Map<String, Map<Long, String>> claims = new ConcurrentHashMap<>();
	/** Pages d'un renvoi complet en cours d'assemblage (commit atomique à la dernière page). */
	private static final Map<String, Map<Integer, List<ClaimChunk>>> pendingPages = new ConcurrentHashMap<>();
	/** Correspondance dimension client (ex. « overworld ») → nom de monde serveur (ex. « world »). */
	private static final Map<String, String> dimToWorld = new ConcurrentHashMap<>();

	private static volatile List<Marker> warps = List.of();
	private static volatile List<Marker> homes = List.of();
	private static volatile Marker factionSpawn = null;

	/** true après réception d'un {@code welcome} : le serveur a le bridge Cantale. */
	private static volatile boolean serverSupportsMod = false;
	private static volatile int serverProtocol = -1;

	/** Incrémenté à chaque mutation : les vues se reconstruisent quand ça change. */
	private static final AtomicLong dataVersion = new AtomicLong();

	private ServerDataStore() {
	}

	static {
		resetDimensionDefaults();
	}

	/** Correspondances Bukkit usuelles, écrasées par la liste {@code worlds} du welcome. */
	private static void resetDimensionDefaults() {
		dimToWorld.clear();
		dimToWorld.put("overworld", "world");
		dimToWorld.put("the_nether", "world_nether");
		dimToWorld.put("the_end", "world_the_end");
	}

	/** Réinitialise tout (déconnexion, nouveau handshake). */
	public static synchronized void reset() {
		claims.clear();
		pendingPages.clear();
		warps = List.of();
		homes = List.of();
		factionSpawn = null;
		serverSupportsMod = false;
		serverProtocol = -1;
		resetDimensionDefaults();
		dataVersion.incrementAndGet();
	}

	// ─── Application des messages (thread client uniquement) ─────

	public static synchronized void applyWelcome(int protocol, Map<String, String> worlds) {
		serverProtocol = protocol;
		serverSupportsMod = true;
		if (worlds != null && !worlds.isEmpty()) {
			resetDimensionDefaults();
			dimToWorld.putAll(worlds);
		}
		dataVersion.incrementAndGet();
	}

	/** Vide tous les claims avant un renvoi complet (gère les mondes devenus vides). */
	public static synchronized void applyClaimsReset() {
		claims.clear();
		pendingPages.clear();
		dataVersion.incrementAndGet();
	}

	/**
	 * Assemble une page de claims ; la carte du monde n'est remplacée que lorsque toutes
	 * les pages sont là (pas d'état partiel visible pendant un renvoi complet).
	 */
	public static synchronized void applyClaimsPage(String world, int page, int pages, List<ClaimChunk> chunks) {
		if (world == null || chunks == null || pages <= 0 || page < 0 || page >= pages) return;
		Map<Integer, List<ClaimChunk>> received = pendingPages.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
		received.put(page, chunks);
		if (received.size() < pages) return;

		Map<Long, String> assembled = new ConcurrentHashMap<>();
		for (int i = 0; i < pages; i++) {
			List<ClaimChunk> slice = received.get(i);
			if (slice == null) {
				// Trou de séquence : on attend la retransmission (renvoi complet à la prochaine connexion)
				return;
			}
			for (ClaimChunk chunk : slice) {
				assembled.put(pack(chunk.x(), chunk.z()), chunk.rel());
			}
		}
		claims.put(world, assembled);
		pendingPages.remove(world);
		dataVersion.incrementAndGet();
	}

	public static synchronized void applyClaimAdd(String world, int x, int z, String rel) {
		if (world == null || rel == null) return;
		claims.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(pack(x, z), rel);
		dataVersion.incrementAndGet();
	}

	public static synchronized void applyClaimRemove(String world, int x, int z) {
		if (world == null) return;
		Map<Long, String> worldClaims = claims.get(world);
		if (worldClaims != null) {
			worldClaims.remove(pack(x, z));
			dataVersion.incrementAndGet();
		}
	}

	public static synchronized void applyMarkers(List<Marker> newWarps, List<Marker> newHomes, Marker newFactionSpawn) {
		warps = newWarps != null ? List.copyOf(newWarps) : List.of();
		homes = newHomes != null ? List.copyOf(newHomes) : List.of();
		factionSpawn = newFactionSpawn;
		dataVersion.incrementAndGet();
	}

	// ─── Lecture (rendu) ─────────────────────────────────────────

	/** Claims d'un monde serveur (jamais null). */
	public static Map<Long, String> claimsOf(String serverWorld) {
		Map<Long, String> worldClaims = claims.get(serverWorld);
		return worldClaims != null ? worldClaims : Map.of();
	}

	public static List<Marker> warps() {
		return warps;
	}

	public static List<Marker> homes() {
		return homes;
	}

	public static Marker factionSpawn() {
		return factionSpawn;
	}

	public static boolean isServerSupportsMod() {
		return serverSupportsMod;
	}

	public static int serverProtocol() {
		return serverProtocol;
	}

	public static long dataVersion() {
		return dataVersion.get();
	}

	/** Nom de monde serveur correspondant à la dimension actuellement affichée côté client. */
	public static String currentServerWorld(ClientWorld world) {
		if (world == null) return "world";
		String dim = world.getRegistryKey().getValue().getPath();
		return dimToWorld.getOrDefault(dim, dim);
	}

	/** Marqueurs d'un type donné limités à un monde serveur. */
	public static List<Marker> markersIn(List<Marker> markers, String serverWorld) {
		List<Marker> out = new ArrayList<>();
		for (Marker marker : markers) {
			if (marker.world() != null && marker.world().equals(serverWorld)) {
				out.add(marker);
			}
		}
		return out;
	}

	/** (chunkX, chunkZ) → clé long, identique au pack de la map in-game. */
	public static long pack(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
