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
 *
 * <p>Les champs {@code fid}, le nom et le chef sont optionnels (protocole v1 étendu).
 * Un serveur plus ancien, qui n'envoie que {@code x/z/rel}, reste affichable.
 */
public final class ServerDataStore {

	/** Chunk claimé reçu du serveur. rel ∈ OWN | ALLY | WAR | ENEMY | OTHER | PASDIC. */
	public record ClaimChunk(int x, int z, String rel, int factionId) {
		public ClaimChunk(int x, int z, String rel) {
			this(x, z, rel, 0);
		}
	}

	/** Cellule de claim déjà appliquée : relation du chunk et id de faction (0 si inconnu). */
	public record ClaimCell(String rel, int factionId) {
	}

	/**
	 * Fiche faction poussée par le serveur (index ou delta). {@code rel} est la relation
	 * diplomatique ; un chunk peut surcharger en PASDIC.
	 */
	public record FactionCard(int id, String name, String leader, String rel) {
	}

	/** Marqueur serveur (warp public, home du joueur, spawn de faction). */
	public record Marker(String name, String world, double x, double y, double z, boolean event) {
	}

	/** État visuel d'un joueur. Le client n'équipe rien : il affiche ce que le serveur a envoyé. */
	public record CosmeticLook(String title, String aura, String cape) {
		public static final CosmeticLook NONE = new CosmeticLook("", "", "");

		public boolean visible() {
			return !title.isEmpty() || !aura.isEmpty() || !cape.isEmpty();
		}
	}

	/**
	 * Instantané HUD. Le décompte de combat est ancré sur {@code receivedAtMs} pour
	 * animer la barre entre deux paquets sans nouvelle allocation réseau.
	 */
	public record HudState(int lives, int maxLives, boolean unlimited, int combatRemainingMs, int combatMaxMs,
						   long receivedAtMs, String opponent) {
	}

	/** Tooltip de claim ouvert par un clic, confirmé ou corrigé par {@code claim_info}. */
	public record ClaimTooltip(int seq, String world, int chunkX, int chunkZ, int factionId, String rel,
							   String faction, String leader) {
	}

	/** Claims appliqués : monde serveur → (chunk packé → cellule). */
	private static final Map<String, Map<Long, ClaimCell>> claims = new ConcurrentHashMap<>();
	/** Pages d'un renvoi complet en cours d'assemblage (commit atomique à la dernière page). */
	private static final Map<String, Map<Integer, List<ClaimChunk>>> pendingPages = new ConcurrentHashMap<>();
	/** Pages de l'index factions en cours d'assemblage. */
	private static final Map<Integer, List<FactionCard>> pendingFactionPages = new ConcurrentHashMap<>();
	/** id de faction → fiche (nom, chef, relation diplomatique). */
	private static final Map<Integer, FactionCard> factions = new ConcurrentHashMap<>();
	/** Correspondance dimension client (ex. « overworld ») → nom de monde serveur (ex. « world »). */
	private static final Map<String, String> dimToWorld = new ConcurrentHashMap<>();

	private static volatile List<Marker> warps = List.of();
	private static volatile List<Marker> homes = List.of();
	private static volatile Marker factionSpawn = null;
	private static volatile HudState hud = null;
	private static volatile ClaimTooltip tooltip = null;
	/** uuid et nom en minuscules → cosmétique visuel envoyé par le serveur. */
	private static volatile Map<String, CosmeticLook> cosmetics = Map.of();

	/** true après réception d'un {@code welcome} : le serveur a le bridge Cantale. */
	private static volatile boolean serverSupportsMod = false;
	private static volatile int serverProtocol = -1;

	/** Incrémenté à chaque mutation de carte : les vues se reconstruisent quand ça change. */
	private static final AtomicLong dataVersion = new AtomicLong();
	/** Incrémenté quand le territoire ou l'index faction change (boussole, tooltip). */
	private static final AtomicLong metaVersion = new AtomicLong();

	private static int nextInspectSeq = 1;

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
		pendingFactionPages.clear();
		factions.clear();
		warps = List.of();
		homes = List.of();
		factionSpawn = null;
		hud = null;
		tooltip = null;
		cosmetics = Map.of();
		serverSupportsMod = false;
		serverProtocol = -1;
		nextInspectSeq = 1;
		resetDimensionDefaults();
		dataVersion.incrementAndGet();
		metaVersion.incrementAndGet();
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
		metaVersion.incrementAndGet();
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

		Map<Long, ClaimCell> assembled = new ConcurrentHashMap<>();
		for (int i = 0; i < pages; i++) {
			List<ClaimChunk> slice = received.get(i);
			if (slice == null) {
				// Trou de séquence : on attend la retransmission (renvoi complet à la prochaine connexion)
				return;
			}
			for (ClaimChunk chunk : slice) {
				assembled.put(pack(chunk.x(), chunk.z()), new ClaimCell(chunk.rel(), chunk.factionId()));
			}
		}
		claims.put(world, assembled);
		pendingPages.remove(world);
		refreshOpenTooltip(world, assembled);
		dataVersion.incrementAndGet();
		metaVersion.incrementAndGet();
	}

	public static synchronized void applyClaimAdd(String world, int x, int z, String rel, int factionId,
												  String faction, String leader) {
		if (world == null || rel == null) return;
		claims.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(pack(x, z), new ClaimCell(rel, factionId));
		if (factionId > 0 && faction != null && !faction.isEmpty()) {
			FactionCard prev = factions.get(factionId);
			String cardRel = rel;
			if ("PASDIC".equals(rel)) {
				cardRel = prev != null ? prev.rel() : "OTHER";
			}
			String cardLeader = leader == null || leader.isEmpty()
					? (prev != null ? prev.leader() : "")
					: leader;
			factions.put(factionId, new FactionCard(factionId, faction, cardLeader, cardRel));
		}
		if (tooltip != null && world.equals(tooltip.world()) && tooltip.chunkX() == x && tooltip.chunkZ() == z) {
			String shownFaction = faction == null || faction.isEmpty() ? tooltip.faction() : faction;
			String shownLeader = leader == null || leader.isEmpty() ? tooltip.leader() : leader;
			int shownId = factionId > 0 ? factionId : tooltip.factionId();
			tooltip = new ClaimTooltip(tooltip.seq(), world, x, z, shownId, rel, shownFaction, shownLeader);
		}
		dataVersion.incrementAndGet();
		metaVersion.incrementAndGet();
	}

	public static synchronized void applyClaimRemove(String world, int x, int z) {
		if (world == null) return;
		Map<Long, ClaimCell> worldClaims = claims.get(world);
		if (worldClaims != null && worldClaims.remove(pack(x, z)) != null) {
			if (tooltip != null && world.equals(tooltip.world()) && tooltip.chunkX() == x && tooltip.chunkZ() == z) {
				tooltip = null;
			}
			dataVersion.incrementAndGet();
			metaVersion.incrementAndGet();
		}
	}

	public static synchronized void applyFactionIndexPage(int page, int pages, List<FactionCard> entries) {
		if (entries == null || pages <= 0 || page < 0 || page >= pages) return;
		if (page == 0) pendingFactionPages.clear();
		pendingFactionPages.put(page, List.copyOf(entries));
		if (pendingFactionPages.size() < pages) return;

		Map<Integer, FactionCard> assembled = new ConcurrentHashMap<>();
		for (int i = 0; i < pages; i++) {
			List<FactionCard> slice = pendingFactionPages.get(i);
			if (slice == null) return;
			for (FactionCard card : slice) {
				if (card != null && card.id() > 0) assembled.put(card.id(), card);
			}
		}
		factions.clear();
		factions.putAll(assembled);
		pendingFactionPages.clear();
		if (tooltip != null && tooltip.factionId() > 0) {
			FactionCard card = assembled.get(tooltip.factionId());
			if (card != null) {
				tooltip = new ClaimTooltip(tooltip.seq(), tooltip.world(), tooltip.chunkX(), tooltip.chunkZ(),
						card.id(), tooltip.rel(), card.name(), card.leader());
			}
		}
		metaVersion.incrementAndGet();
	}

	public static synchronized void applyMarkers(List<Marker> newWarps, List<Marker> newHomes, Marker newFactionSpawn) {
		warps = newWarps != null ? List.copyOf(newWarps) : List.of();
		homes = newHomes != null ? List.copyOf(newHomes) : List.of();
		factionSpawn = newFactionSpawn;
		dataVersion.incrementAndGet();
	}

	/**
	 * Applique un instantané HUD. Le décompte local est conservé quand le paquet
	 * serveur ne s'en écarte pas de plus de 700 ms, pour éviter que la barre ne saute.
	 */
	public static synchronized void applyHud(int lives, int maxLives, boolean unlimited,
											 int combatMs, int combatMaxMs, String opponent) {
		long now = System.currentTimeMillis();
		int incoming = Math.max(0, combatMs);
		int slots = Math.max(1, Math.min(maxLives, 10));
		int max = Math.max(1, combatMaxMs);
		String opp = opponent == null ? "" : opponent;
		int predicted = 0;
		long anchor = now;
		if (hud != null) {
			predicted = (int) Math.max(0L, hud.combatRemainingMs - (now - hud.receivedAtMs));
			anchor = hud.receivedAtMs;
		}
		boolean keepClock = hud != null && incoming > 0 && predicted > 0
				&& incoming <= predicted && predicted - incoming < 700;
		if (keepClock) {
			hud = new HudState(lives, slots, unlimited, predicted, max, anchor, opp);
		} else {
			hud = new HudState(lives, slots, unlimited, incoming, max, now, opp);
		}
	}

	/**
	 * Ouvre un tooltip local (données déjà poussées par le serveur) et réserve un
	 * numéro d'intention. La réponse {@code claim_info} du même numéro remplace la fiche.
	 */
	public static synchronized int openTooltip(String world, int x, int z, int factionId, String rel,
											   String faction, String leader) {
		int seq = nextInspectSeq++;
		if (nextInspectSeq >= 1_000_000_000) nextInspectSeq = 1;
		tooltip = new ClaimTooltip(seq, world == null ? "" : world, x, z, factionId,
				rel == null ? "OTHER" : rel,
				faction == null ? "" : faction,
				leader == null ? "" : leader);
		return seq;
	}

	public static synchronized void applyClaimInfo(int seq, boolean found, String world, int x, int z,
												   String rel, String faction, String leader) {
		ClaimTooltip current = tooltip;
		if (current == null || current.seq() != seq) return;
		if (!found) {
			tooltip = null;
			return;
		}
		if (world == null || world.isEmpty() || !world.equals(current.world())
				|| x != current.chunkX() || z != current.chunkZ()) {
			return;
		}
		String safeRel = rel == null || rel.isEmpty() ? current.rel() : rel;
		String safeFaction = faction == null ? "" : faction;
		String safeLeader = leader == null ? "" : leader;
		tooltip = new ClaimTooltip(seq, current.world(), x, z, current.factionId(), safeRel, safeFaction, safeLeader);
	}

	public static synchronized void clearTooltip() {
		tooltip = null;
	}

	private static void refreshOpenTooltip(String world, Map<Long, ClaimCell> assembled) {
		if (tooltip == null || !world.equals(tooltip.world())) return;
		ClaimCell cell = assembled.get(pack(tooltip.chunkX(), tooltip.chunkZ()));
		if (cell == null) {
			tooltip = null;
			return;
		}
		if (!cell.rel().equals(tooltip.rel()) || cell.factionId() != tooltip.factionId()) {
			tooltip = new ClaimTooltip(tooltip.seq(), tooltip.world(), tooltip.chunkX(), tooltip.chunkZ(),
					cell.factionId(), cell.rel(), tooltip.faction(), tooltip.leader());
		}
	}

	// ─── Lecture (rendu) ─────────────────────────────────────────

	/** Claims d'un monde serveur (jamais null). */
	public static Map<Long, ClaimCell> claimsOf(String serverWorld) {
		Map<Long, ClaimCell> worldClaims = claims.get(serverWorld);
		return worldClaims != null ? worldClaims : Map.of();
	}

	public static ClaimCell cellAt(String serverWorld, int chunkX, int chunkZ) {
		Map<Long, ClaimCell> worldClaims = claims.get(serverWorld);
		if (worldClaims == null) return null;
		return worldClaims.get(pack(chunkX, chunkZ));
	}

	public static FactionCard faction(int factionId) {
		if (factionId <= 0) return null;
		return factions.get(factionId);
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

	public static HudState hud() {
		return hud;
	}

	public static synchronized void applyCosmetics(Map<String, CosmeticLook> next) {
		cosmetics = next == null || next.isEmpty() ? Map.of() : Map.copyOf(next);
	}

	public static CosmeticLook cosmeticOf(String uuid, String name) {
		Map<String, CosmeticLook> current = cosmetics;
		if (uuid != null) {
			CosmeticLook byId = current.get(uuid);
			if (byId != null) {
				return byId;
			}
		}
		if (name != null) {
			CosmeticLook byName = current.get(name.toLowerCase(java.util.Locale.ROOT));
			if (byName != null) {
				return byName;
			}
		}
		return CosmeticLook.NONE;
	}

	public static ClaimTooltip tooltip() {
		return tooltip;
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

	public static long metaVersion() {
		return metaVersion.get();
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
