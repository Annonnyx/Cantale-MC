package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerOptimizer {

    private final CantalePlugin plugin;
    private final Map<String, Object> stats;
    private boolean enabled;
    private int autoSaveIntervalMinutes;
    private int entityCleanupIntervalSeconds;
    private int maxItemsPerChunk;
    private int maxEntitiesPerChunk;
    private boolean clearGroundItems;
    private boolean clearArrows;
    private boolean clearExperience;
    private boolean villagersDisabled;
    /** Ne jamais supprimer / cull un item plus jeune que ce délai (loot de mort). */
    private int protectRecentItemSeconds;
    /** Anti-boucle : un TPS bas persistant ne doit pas relancer urgence + scan en boucle. */
    private long lastEmergencyCleanupAt;

    public ServerOptimizer(CantalePlugin plugin) {
        this.plugin = plugin;
        this.stats = new ConcurrentHashMap<>();
        this.enabled = true;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("optimization.enabled", true);
        this.autoSaveIntervalMinutes = plugin.getConfig().getInt("optimization.autosave-interval", 5);
        this.entityCleanupIntervalSeconds = plugin.getConfig().getInt("optimization.entity-cleanup-interval", 60);
        this.maxItemsPerChunk = plugin.getConfig().getInt("optimization.max-items-per-chunk", 20);
        this.maxEntitiesPerChunk = plugin.getConfig().getInt("optimization.max-entities-per-chunk", 15);
        this.clearGroundItems = plugin.getConfig().getBoolean("optimization.clear-ground-items", true);
        this.clearArrows = plugin.getConfig().getBoolean("optimization.clear-arrows", true);
        this.clearExperience = plugin.getConfig().getBoolean("optimization.clear-experience", false);
        this.villagersDisabled = plugin.getConfig().getBoolean("optimization.villagers-disabled", false);
        // Défaut 5 min : une mort + retour spawn/home ne doit jamais perdre le loot au nettoyage
        this.protectRecentItemSeconds = Math.max(60,
                plugin.getConfig().getInt("optimization.protect-recent-item-seconds", 300));
    }

    /** True si l'item est trop récent pour être clear/cull (loot de mort, drops récents). */
    private boolean isProtectedRecentItem(Item item) {
        return item.getTicksLived() < protectRecentItemSeconds * 20;
    }

    public boolean isVillagersDisabled() {
        return villagersDisabled;
    }

    /**
     * Active/désactive entièrement les villageois sur le serveur.
     * Persiste dans config.yml et supprime immédiatement les villageois présents.
     */
    public int setVillagersDisabled(boolean disabled) {
        this.villagersDisabled = disabled;
        plugin.getConfig().set("optimization.villagers-disabled", disabled);
        plugin.saveConfig();
        if (disabled) {
            return removeAllVillagers();
        }
        return 0;
    }

    /** Supprime tous les villageois (Villager, WanderingTrader, Zombie Villager). */
    public int removeAllVillagers() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Villager || e instanceof WanderingTrader || e instanceof ZombieVillager) {
                    e.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Force le clear de toutes les entités sauf :
     * - Joueurs
     * - Bêtes apprivoisées (Tameable.isTamed())
     * - Entités avec custom name (sauf si forceNamed = true)
     * - NPCs Cantale (metadata cantale_npc)
     * - Armor stands marqués "keep" (metadata cantale_keep)
     *
     * @param includeNamed si true, supprime aussi les entités nommées (sauf joueurs/tames)
     * @return nombre d'entités supprimées
     */
    public int forceClearEntities(boolean includeNamed) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (shouldKeep(e, includeNamed)) continue;
                e.remove();
                removed++;
            }
        }
        plugin.getLogger().info("[Optimizer] Force clear: " + removed + " entités supprimées (includeNamed=" + includeNamed + ")");
        return removed;
    }

    private boolean shouldKeep(Entity e, boolean includeNamed) {
        if (e instanceof Player) return true;
        if (e instanceof Tameable t && t.isTamed()) return true;
        if (e.hasMetadata("cantale_npc")) return true;
        if (e.hasMetadata("cantale_keep")) return true;
        var spawners = plugin.getSpawnerManager();
        if (spawners != null && spawners.isSpawnerHologram(e)) return true;
        if (e instanceof StorageMinecart || e instanceof HopperMinecart) return true;
        // Entités nommées : conservées par défaut sauf si includeNamed.
        // Les stacks Cantale (PDC mob_stack + libellé ×N) ne comptent pas comme « nom joueur ».
        if (!includeNamed && e.customName() != null && !isCantaleMobStack(e)) return true;
        return false;
    }

    private boolean isCantaleMobStack(Entity e) {
        MobStackManager stacks = plugin.getMobStackManager();
        return stacks != null && stacks.isStacked(e);
    }

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupEntities,
                entityCleanupIntervalSeconds * 20L,
                entityCleanupIntervalSeconds * 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, this::autoSave,
                autoSaveIntervalMinutes * 20L * 60L,
                autoSaveIntervalMinutes * 20L * 60L);

        plugin.getLogger().info("[Optimizer] enabled=" + enabled
                + ", auto-save=" + autoSaveIntervalMinutes + "min, cleanup=" + entityCleanupIntervalSeconds + "s");
    }

    /**
     * Nettoyage périodique : UNE seule itération des entités par monde.
     * Classification dans la boucle (flèches / XP / items / autres), puis cull par chunk.
     * L'âge des items au sol reste géré par ClearLagTask (annonces joueurs) : un clear
     * silencieux ici vidait les items éligibles avant le countdown → ClearLag annonçait « 0 ».
     */
    public void cleanupEntities() {
        if (!enabled) return;

        int removedItems = 0;
        int removedArrows = 0;
        int removedExp = 0;
        int removedExcess = 0;

        for (World world : Bukkit.getWorlds()) {
            Map<Long, List<Item>> chunkItems = clearGroundItems ? new HashMap<>() : null;
            Map<Long, List<Entity>> chunkEntities = new HashMap<>();

            for (Entity entity : world.getEntities()) {
                if (clearArrows && entity instanceof Arrow arrow) {
                    if (arrow.getTicksLived() > 20 * 30) {
                        arrow.remove();
                        removedArrows++;
                    }
                    continue;
                }
                if (clearExperience && entity instanceof ExperienceOrb) {
                    entity.remove();
                    removedExp++;
                    continue;
                }
                if (entity instanceof Item item) {
                    if (chunkItems != null && !isProtectedRecentItem(item)) {
                        long key = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
                        chunkItems.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                    }
                    continue;
                }
                if (shouldSkipEntityCap(entity)) continue;
                long key = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
                chunkEntities.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
            }

            if (chunkItems != null) {
                removedItems += cullItemsPerChunk(chunkItems);
            }
            removedExcess += cullExcessFromMap(chunkEntities);
        }

        int total = removedItems + removedArrows + removedExp + removedExcess;
        if (total > 0) {
            plugin.getLogger().info("[Optimizer] Nettoyé " + total + " entités");
        }

        stats.put("last_cleanup_items", removedItems);
        stats.put("last_cleanup_arrows", removedArrows);
        stats.put("last_cleanup_exp", removedExp);
        stats.put("last_cleanup_excess", removedExcess);
    }

    /** Cull les items hors grâce au-delà de max-items-per-chunk, plus vieux d'abord. */
    private int cullItemsPerChunk(Map<Long, List<Item>> chunkItems) {
        int removed = 0;
        for (List<Item> items : chunkItems.values()) {
            if (items.size() <= maxItemsPerChunk) continue;
            items.sort((a, b) -> Long.compare(b.getTicksLived(), a.getTicksLived()));
            int over = items.size() - maxItemsPerChunk;
            for (int i = 0; i < over; i++) {
                items.get(i).remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Applique le plafond {@code max-entities-per-chunk} sur tous les mondes.
     * Appelé par le ClearLag (l'optimizer passe par {@link #cleanupEntities()}).
     *
     * @return nombre d'entités retirées
     */
    public int cullExcessEntitiesPerChunk() {
        if (!enabled) return 0;
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            Map<Long, List<Entity>> chunkEntities = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (shouldSkipEntityCap(entity)) continue;
                long key = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
                chunkEntities.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
            }
            removed += cullExcessFromMap(chunkEntities);
        }
        if (removed > 0) {
            plugin.getLogger().info("[Optimizer] Plafond chunk: " + removed + " entités en surplus retirées");
            stats.put("last_cleanup_excess", removed);
        }
        return removed;
    }

    /** Retire le surplus au-delà de {@link #maxEntitiesPerChunk} par chunk, plus vieux d'abord. */
    private int cullExcessFromMap(Map<Long, List<Entity>> chunkEntities) {
        int removed = 0;
        for (List<Entity> entities : chunkEntities.values()) {
            if (entities.size() <= maxEntitiesPerChunk) continue;
            entities.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived()));
            int toRemove = entities.size() - maxEntitiesPerChunk;
            for (int i = 0; i < toRemove; i++) {
                entities.get(i).remove();
                removed++;
            }
        }
        return removed;
    }

    /** Entités qui ne comptent pas / ne sont jamais cullées par le plafond par chunk. */
    private boolean shouldSkipEntityCap(Entity entity) {
        if (entity instanceof Player || entity instanceof Villager) return true;
        if (entity instanceof Item) return true; // items : ClearLag + max-items-per-chunk
        // Passifs : empilés via MobStackManager (plus de cull moutons/vaches/…)
        if (entity instanceof Animals) return true;
        if (entity instanceof Tameable t && t.isTamed()) return true;
        var spawners = plugin.getSpawnerManager();
        if (spawners != null) {
            if (spawners.isSpawnerMob(entity) || spawners.isSpawnerHologram(entity)) return true;
        }
        // Mobs nommés joueur — les stacks Cantale ont un customName mais restent cullables
        if (entity.customName() != null && !isCantaleMobStack(entity)) return true;
        if (entity.hasMetadata("cantale_npc") || entity.hasMetadata("cantale_keep")) return true;
        return false;
    }

    public void autoSave() {
        if (!enabled) return;
        // Paper gère l'auto-save des mondes nativement (incremental, étalé) — world.save()
        // provoquerait des freezes. On sauvegarde seulement les données joueurs (rapide).
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.saveData();
        }
    }

    /**
     * Nettoyage d'urgence (items hors grâce, flèches > 10 s, toutes les orbes XP).
     * Déclenché par le PerformanceGovernor en mode CRITICAL. Cooldown 2 min interne.
     *
     * @return true si le nettoyage a été exécuté (false si encore en cooldown)
     */
    public boolean emergencyCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastEmergencyCleanupAt < 120_000L) return false;
        lastEmergencyCleanupAt = now;

        int removed = 0;
        // Même en urgence : ne pas toucher au loot de mort récent
        int itemMinAgeTicks = protectRecentItemSeconds * 20;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    if (item.getTicksLived() > itemMinAgeTicks) {
                        item.remove();
                        removed++;
                    }
                } else if (entity instanceof Arrow arrow) {
                    if (arrow.getTicksLived() > 20 * 10) {
                        arrow.remove();
                        removed++;
                    }
                } else if (entity instanceof ExperienceOrb orb) {
                    orb.remove();
                    removed++;
                }
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("[Optimizer] Urgence: " + removed + " entités nettoyées");
        }
        return true;
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public double getTPS() {
        try {
            return Bukkit.getTPS()[0]; // TPS sur 1 minute
        } catch (NoSuchMethodError e) {
            return 20.0;
        }
    }

    /** TPS Paper [1m, 5m, 15m], fallback {20, 20, 20}. */
    public double[] getTPSAll() {
        try {
            return Bukkit.getTPS();
        } catch (NoSuchMethodError e) {
            return new double[]{20.0, 20.0, 20.0};
        }
    }

    /** MSPT moyen (Paper getAverageTickTime), -1 si indisponible. */
    public double getAverageTickTime() {
        try {
            return Bukkit.getServer().getAverageTickTime();
        } catch (NoSuchMethodError e) {
            return -1;
        }
    }

    public Map<String, Object> getStats() {
        if (Bukkit.isPrimaryThread()) {
            return buildStats();
        }
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, this::buildStats).get();
        } catch (Exception e) {
            plugin.getLogger().warning("[Optimizer] Impossible de collecter les stats depuis l'async: " + e.getMessage());
            return new HashMap<>(stats);
        }
    }

    private Map<String, Object> buildStats() {
        double[] tps = getTPSAll();
        stats.put("tps", tps[0]);
        stats.put("tps_5m", tps.length > 1 ? tps[1] : tps[0]);
        stats.put("tps_15m", tps.length > 2 ? tps[2] : tps[0]);
        stats.put("mspt", getAverageTickTime());
        stats.put("online_players", Bukkit.getOnlinePlayers().size());
        stats.put("max_players", Bukkit.getMaxPlayers());
        stats.put("used_memory_mb", getUsedMemoryMB());
        stats.put("max_memory_mb", getMaxMemoryMB());

        int totalChunks = 0;
        int totalEntities = 0;
        Map<String, Map<String, Integer>> worlds = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            int chunks = world.getLoadedChunks().length;
            int entities = world.getEntityCount();
            int tiles = 0;
            for (Chunk chunk : world.getLoadedChunks()) {
                tiles += chunk.getTileEntities().length;
            }
            totalChunks += chunks;
            totalEntities += entities;
            Map<String, Integer> w = new LinkedHashMap<>();
            w.put("chunks", chunks);
            w.put("entities", entities);
            w.put("tile_entities", tiles);
            w.put("sim_distance", world.getSimulationDistance());
            w.put("view_distance", world.getViewDistance());
            worlds.put(world.getName(), w);
        }
        stats.put("loaded_chunks", totalChunks);
        stats.put("total_entities", totalEntities);
        stats.put("worlds", worlds);

        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
            if (gc.getCollectionTime() > 0) gcTimeMs += gc.getCollectionTime();
        }
        stats.put("gc_count", gcCount);
        stats.put("gc_time_ms", gcTimeMs);

        return new HashMap<>(stats);
    }

    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("optimization.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
