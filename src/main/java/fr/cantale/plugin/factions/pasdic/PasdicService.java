package fr.cantale.plugin.factions.pasdic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Règles PASDIC partagées entre {@code /f pasdic} et le pont admin du site.
 *
 * Un PASDIC est un claim renforcé : seuls les membres de la faction peuvent
 * y poser/casser. Les spawners, fours, hoppers, droppers, dispensers sont autorisés.
 * Seuls les conteneurs de stockage interdits (coffres, barrels, shulkers, etc.)
 * bloquent la conversion.
 */
public class PasdicService {

    public static final int MAX_CHUNKS = 250;
    private static final long CHUNK_LOAD_TIMEOUT_SECONDS = 8L;

    private final CantalePlugin plugin;
    private final FactionDAO factionDAO;

    public PasdicService(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionDAO = new FactionDAO(plugin);
    }

    /**
     * Active ou désactive le PASDIC sur un claim déjà chargé.
     * @return message d'erreur FR, ou {@code null} si succès
     */
    public String setPasdic(Claim claim, boolean pasdic, Chunk loadedChunk) {
        if (claim == null) {
            return "Ce chunk n'est pas claim par une faction.";
        }
        if (pasdic) {
            String block = inspectBlockReason(claim.getWorld(), claim.getChunkX(), claim.getChunkZ(), loadedChunk);
            if (block != null) {
                return refuseMessage(block);
            }
        }
        persist(claim, pasdic);
        return null;
    }

    public String refuseMessage(String reason) {
        return switch (reason) {
            case "spawners" -> "Impossible d'activer le PASDIC : ce claim contient des spawners.";
            case "stockage" -> "Impossible d'activer le PASDIC : ce claim contient du stockage (coffres / conteneurs).";
            case "stockage+spawners" ->
                    "Impossible d'activer le PASDIC : ce claim contient des spawners et du stockage.";
            default -> "Impossible d'activer le PASDIC.";
        };
    }

    /**
     * Traite un lot de chunks (payload JSON du site) hors du timeout 12 s du pont admin.
     * @return JSON résultat pour {@code web_admin_actions.result}
     */
    public CompletableFuture<String> applyBatchFromWeb(String payload) {
        List<ChunkRef> refs;
        try {
            refs = parsePayload(payload);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(failJson(e.getMessage() != null ? e.getMessage() : "payload invalide"));
        }
        if (refs.isEmpty()) {
            return CompletableFuture.completedFuture(failJson("aucun chunk"));
        }

        CompletableFuture<String> finished = new CompletableFuture<>();
        List<ChunkOutcome> outcomes = new ArrayList<>(refs.size());
        processNext(refs, 0, outcomes, finished);
        return finished;
    }

    private void processNext(List<ChunkRef> refs, int index, List<ChunkOutcome> outcomes,
                             CompletableFuture<String> finished) {
        if (!plugin.isEnabled()) {
            finished.complete(failJson("plugin arrêté"));
            return;
        }
        if (index >= refs.size()) {
            finished.complete(toResultJson(outcomes));
            return;
        }
        inspectAndApply(refs.get(index)).whenComplete((outcome, error) -> runOnMain(() -> {
            if (error != null) {
                outcomes.add(new ChunkOutcome(refs.get(index), false, "chargement"));
            } else {
                outcomes.add(outcome);
            }
            processNext(refs, index + 1, outcomes, finished);
        }));
    }

    private CompletableFuture<ChunkOutcome> inspectAndApply(ChunkRef ref) {
        CompletableFuture<ChunkOutcome> done = new CompletableFuture<>();
        runOnMain(() -> {
            try {
                startInspect(ref, done);
            } catch (Exception e) {
                done.complete(new ChunkOutcome(ref, false, "chargement"));
            }
        });
        return done;
    }

    private void startInspect(ChunkRef ref, CompletableFuture<ChunkOutcome> done) {
        FactionManager factions = plugin.getFactionManager();
        if (factions == null) {
            done.complete(new ChunkOutcome(ref, false, "wilderness"));
            return;
        }
        Claim claim = factions.getClaimAt(ref.world, ref.x, ref.z);
        if (claim == null) {
            done.complete(new ChunkOutcome(ref, false, "wilderness"));
            return;
        }
        if (claim.isPasdic()) {
            done.complete(new ChunkOutcome(ref, true, "deja"));
            return;
        }

        World world = Bukkit.getWorld(ref.world);
        if (world == null) {
            done.complete(new ChunkOutcome(ref, false, "monde"));
            return;
        }

        if (world.isChunkLoaded(ref.x, ref.z)) {
            done.complete(scanAndSet(claim, world.getChunkAt(ref.x, ref.z)));
            return;
        }

        world.getChunkAtAsync(ref.x, ref.z, false)
                .orTimeout(CHUNK_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((chunk, error) -> runOnMain(() -> {
                    if (error != null) {
                        done.complete(new ChunkOutcome(ref, false, "chargement"));
                        return;
                    }
                    done.complete(scanAndSet(claim, chunk));
                }));
    }

    private ChunkOutcome scanAndSet(Claim claim, Chunk chunk) {
        String block = inspectBlockReason(claim.getWorld(), claim.getChunkX(), claim.getChunkZ(), chunk);
        if (block != null) {
            return new ChunkOutcome(
                    new ChunkRef(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()),
                    false, block);
        }
        persist(claim, true);
        return new ChunkOutcome(
                new ChunkRef(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()),
                true, "ok");
    }

    /**
     * @return code de refus ({@code stockage}) ou null si le chunk peut devenir PASDIC
     */
    public String inspectBlockReason(String world, int chunkX, int chunkZ, Chunk loadedChunk) {
        if (loadedChunk == null) return null;
        Set<Material> banned = plugin.getConfigManager().getPasdicBannedContainers();
        for (BlockState state : loadedChunk.getTileEntities()) {
            if (banned.contains(state.getType())) {
                return "stockage";
            }
        }
        return null;
    }

    private boolean hasCantaleSpawners(String world, int chunkX, int chunkZ) {
        var spawners = plugin.getSpawnerManager();
        return spawners != null && spawners.chunkHasSpawners(world, chunkX, chunkZ);
    }

    private void persist(Claim claim, boolean pasdic) {
        factionDAO.updateClaimPasdic(claim.getId(), pasdic);
        claim.setPasdic(pasdic);
        FactionManager factions = plugin.getFactionManager();
        if (factions == null) return;
        Faction faction = factions.getFaction(claim.getFactionId());
        if (faction == null) return;
        for (Claim other : faction.getClaims()) {
            if (other.getId() == claim.getId()
                    || (other.getWorld().equals(claim.getWorld())
                    && other.getChunkX() == claim.getChunkX()
                    && other.getChunkZ() == claim.getChunkZ())) {
                other.setPasdic(pasdic);
            }
        }
        // Le chunk change de relation (PASDIC ↔ normale) : delta vers les clients du mod Cantale
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().onClaimPasdicToggled(claim);
        }
    }

    public List<ChunkRef> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload vide");
        }
        JsonElement root = JsonParser.parseString(payload);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("chunks")) {
            JsonElement chunks = root.getAsJsonObject().get("chunks");
            if (!chunks.isJsonArray()) {
                throw new IllegalArgumentException("chunks invalide");
            }
            array = chunks.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("JSON chunks requis");
        }
        if (array.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("trop de chunks (max " + MAX_CHUNKS + ")");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ChunkRef> refs = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String world = readWorld(obj);
            if (world == null || world.isBlank()) continue;
            if (!obj.has("x") || !obj.has("z")) continue;
            int x;
            int z;
            try {
                x = obj.get("x").getAsInt();
                z = obj.get("z").getAsInt();
            } catch (Exception e) {
                continue;
            }
            String key = world + ":" + x + ":" + z;
            if (!seen.add(key)) continue;
            refs.add(new ChunkRef(world, x, z));
        }
        return refs;
    }

    private static String readWorld(JsonObject obj) {
        if (obj.has("w")) return obj.get("w").getAsString();
        if (obj.has("world")) return obj.get("world").getAsString();
        return null;
    }

    private String toResultJson(List<ChunkOutcome> outcomes) {
        int ok = 0;
        int already = 0;
        JsonArray refused = new JsonArray();
        for (ChunkOutcome outcome : outcomes) {
            if (outcome.ok) {
                ok++;
                if ("deja".equals(outcome.reason)) already++;
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("w", outcome.ref.world);
            row.addProperty("x", outcome.ref.x);
            row.addProperty("z", outcome.ref.z);
            row.addProperty("reason", outcome.reason);
            refused.add(row);
        }
        JsonObject root = new JsonObject();
        root.addProperty("ok", ok);
        root.addProperty("already", already);
        root.addProperty("refusedCount", refused.size());
        root.add("refused", refused);
        return root.toString();
    }

    private static String failJson(String message) {
        JsonObject root = new JsonObject();
        root.addProperty("ok", 0);
        root.addProperty("already", 0);
        root.addProperty("refusedCount", 0);
        root.add("refused", new JsonArray());
        root.addProperty("error", message);
        return root.toString();
    }

    private void runOnMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public record ChunkRef(String world, int x, int z) {
        public String label() {
            return world + " " + x + "," + z;
        }
    }

    public record ChunkOutcome(ChunkRef ref, boolean ok, String reason) {}
}
