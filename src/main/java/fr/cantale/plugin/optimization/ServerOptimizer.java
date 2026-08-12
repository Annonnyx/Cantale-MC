package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;

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
    private boolean optimizeChunks;
    private int tpsWarningThreshold;
    private boolean villagersDisabled;
    private long lastSaveTime;
    /** Ne jamais supprimer / cull un item plus jeune que ce délai (loot de mort). */
    private int protectRecentItemSeconds;

    public ServerOptimizer(CantalePlugin plugin) {
        this.plugin = plugin;
        this.stats = new ConcurrentHashMap<>();
        this.enabled = true;
        loadConfig();
    }

    public void loadConfig() {
        this.autoSaveIntervalMinutes = plugin.getConfig().getInt("optimization.autosave-interval", 5);
        this.entityCleanupIntervalSeconds = plugin.getConfig().getInt("optimization.entity-cleanup-interval", 60);
        this.maxItemsPerChunk = plugin.getConfig().getInt("optimization.max-items-per-chunk", 20);
        this.maxEntitiesPerChunk = plugin.getConfig().getInt("optimization.max-entities-per-chunk", 15);
        this.clearGroundItems = plugin.getConfig().getBoolean("optimization.clear-ground-items", true);
        this.clearArrows = plugin.getConfig().getBoolean("optimization.clear-arrows", true);
        this.clearExperience = plugin.getConfig().getBoolean("optimization.clear-experience", false);
        this.optimizeChunks = plugin.getConfig().getBoolean("optimization.optimize-chunks", true);
        this.tpsWarningThreshold = plugin.getConfig().getInt("optimization.tps-warning-threshold", 15);
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
        // Toujours préserver : joueurs
        if (e instanceof Player) return true;

        // Bêtes apprivoisées (Wolf, Cat, Parrot, Horse domestiqué, etc.)
        if (e instanceof Tameable t && t.isTamed()) return true;

        // Marqueurs Cantale (NPCs, armor stand-keep, etc.)
        if (e.hasMetadata("cantale_npc")) return true;
        if (e.hasMetadata("cantale_keep")) return true;

        // Stockage en mouvement (minecart à coffre / hopper) — préserver par sécurité
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
        // Nettoyage des entités
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupEntities,
                entityCleanupIntervalSeconds * 20L,
                entityCleanupIntervalSeconds * 20L);

        // Auto-save
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoSave,
                autoSaveIntervalMinutes * 20L * 60L,
                autoSaveIntervalMinutes * 20L * 60L);

        // Monitoring TPS
        Bukkit.getScheduler().runTaskTimer(plugin, this::monitorTPS, 20L, 20L * 30);

        // Optimisation des chunks
        if (optimizeChunks) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::optimizeChunks, 20L * 60 * 5, 20L * 60 * 5);
        }

        plugin.getLogger().info("[Optimizer] Auto-save: " + autoSaveIntervalMinutes + "min, Cleanup: " + entityCleanupIntervalSeconds + "s");
    }

    public void cleanupEntities() {
        if (!enabled) return;

        int removedItems = 0;
        int removedArrows = 0;
        int removedExp = 0;
        int removedExcess = 0;

        for (World world : Bukkit.getWorlds()) {
            // Âge des items au sol : géré uniquement par ClearLagTask (annonces joueurs).
            // Un clear silencieux ici (toutes les ~60s, même seuil 5 min) vidait les
            // items éligibles avant le countdown → ClearLag annonçait toujours « 0 ».

            // Nettoyer les flèches
            if (clearArrows) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Arrow arrow) {
                        if (arrow.getTicksLived() > 20 * 30) {
                            arrow.remove();
                            removedArrows++;
                        }
                    }
                }
            }

            // Nettoyer l'expérience au sol
            if (clearExperience) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ExperienceOrb) {
                        entity.remove();
                        removedExp++;
                    }
                }
            }

            // Plafond max-entities-per-chunk (Animals exclus — empilés via MobStack)
            removedExcess += cullExcessEntitiesInWorld(world);

            // Limiter les items par chunk — ne cull jamais les items récents (loot de mort)
            if (clearGroundItems) {
                Map<Long, List<Item>> chunkItems = new HashMap<>();
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item item) {
                        long chunkKey = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
                        chunkItems.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(item);
                    }
                }

                for (Map.Entry<Long, List<Item>> entry : chunkItems.entrySet()) {
                    List<Item> items = entry.getValue();
                    if (items.size() <= maxItemsPerChunk) continue;

                    // Cull uniquement les items hors grâce, plus vieux d'abord
                    List<Item> cullable = new ArrayList<>();
                    for (Item item : items) {
                        if (!isProtectedRecentItem(item)) {
                            cullable.add(item);
                        }
                    }
                    cullable.sort((a, b) -> Long.compare(b.getTicksLived(), a.getTicksLived()));

                    int over = items.size() - maxItemsPerChunk;
                    for (int i = 0; i < over && i < cullable.size(); i++) {
                        cullable.get(i).remove();
                        removedItems++;
                    }
                }
            }
        }

        int total = removedItems + removedArrows + removedExcess;
        if (total > 0) {
            plugin.getLogger().info("[Optimizer] Nettoyé " + total + " entités");
        }

        stats.put("last_cleanup_items", removedItems);
        stats.put("last_cleanup_arrows", removedArrows);
        stats.put("last_cleanup_excess", removedExcess);
    }

    /**
     * Applique le plafond {@code max-entities-per-chunk} sur tous les mondes.
     * Animals exclus (stacking passif). Protège joueurs, villageois, items,
     * entités nommées (hors stacks Cantale), pets apprivoisés ({@link Tameable#isTamed()}),
     * NPCs Cantale. Surplus retiré plus vieux d'abord. Appelé par l'optimizer (~60s)
     * et le ClearLag.
     *
     * @return nombre d'entités retirées
     */
    public int cullExcessEntitiesPerChunk() {
        if (!enabled) return 0;
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += cullExcessEntitiesInWorld(world);
        }
        if (removed > 0) {
            plugin.getLogger().info("[Optimizer] Plafond chunk: " + removed + " entités en surplus retirées");
            stats.put("last_cleanup_excess", removed);
        }
        return removed;
    }

    /**
     * Cull entités d'un monde au-delà de {@link #maxEntitiesPerChunk} par chunk.
     * Animals exclus (mob stacking) ; items / joueurs / villageois / nommés / tamed / NPCs exclus.
     */
    private int cullExcessEntitiesInWorld(World world) {
        Map<Long, List<Entity>> chunkEntities = new HashMap<>();
        for (Entity entity : world.getEntities()) {
            if (shouldSkipEntityCap(entity)) continue;

            long chunkKey = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
            chunkEntities.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(entity);
        }

        int removed = 0;
        for (List<Entity> entities : chunkEntities.values()) {
            if (entities.size() <= maxEntitiesPerChunk) continue;
            // Plus vieux d'abord → on retire le surplus le plus ancien
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
        // Mobs nommés joueur — les stacks Cantale ont un customName mais restent cullables
        // s'ils n'étaient pas Animals (ne devrait pas arriver pour les types stackables).
        if (entity.customName() != null && !isCantaleMobStack(entity)) return true;
        if (entity.hasMetadata("cantale_npc") || entity.hasMetadata("cantale_keep")) return true;
        return false;
    }

    public void autoSave() {
        if (!enabled) return;
        
        // DÉSACTIVÉ - Paper gère déjà l'auto-save nativement
        // L'appel à world.save() cause des freezes de 40+ secondes
        // Paper sauvegarde automatiquement les chunks toutes les 5 minutes
        // On garde juste la sauvegarde des données joueurs (rapide)
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.saveData();
        }
    }

    public void monitorTPS() {
        if (!enabled) return;

        double tps = getTPS();
        stats.put("current_tps", tps);

        if (tps < tpsWarningThreshold) {
            plugin.getLogger().warning("[Optimizer] TPS bas détecté: " + String.format("%.1f", tps) + " - Nettoyage d'urgence activé");
            emergencyCleanup();
        }
    }

    public void optimizeChunks() {
        if (!enabled) return;
        
        // DÉSACTIVÉ - Cette méthode cause des freezes en interrogeant SQLite pour chaque chunk
        // Paper gère déjà le déchargement des chunks efficacement
        // La vérification isChunkClaimed() est trop coûteuse en base de données
        
        // int unloaded = 0;
        // for (World world : Bukkit.getWorlds()) {
        //     for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
        //         if (chunk.getEntities().length == 0 && !isChunkClaimed(world.getName(), chunk.getX(), chunk.getZ())) {
        //             if (world.unloadChunk(chunk.getX(), chunk.getZ())) {
        //                 unloaded++;
        //             }
        //         }
        //     }
        // }
    }

    private void emergencyCleanup() {
        int removed = 0;
        // Même en urgence TPS : ne pas toucher au loot de mort récent
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

        // Forcer le GC
        System.gc();

        if (removed > 0) {
            plugin.getLogger().info("[Optimizer] Urgence: " + removed + " entités nettoyées");
        }
    }

    private boolean isChunkClaimed(String world, int chunkX, int chunkZ) {
        try {
            return plugin.getFactionManager().getClaimAt(world, chunkX, chunkZ) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public double getTPS() {
        try {
            // Paper API fournit getTPS()
            double[] tps = Bukkit.getTPS();
            return tps[0]; // TPS sur 1 minute
        } catch (NoSuchMethodError e) {
            return 20.0; // Fallback pour les anciennes versions
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
        stats.put("loaded_chunks", getLoadedChunkCount());
        stats.put("total_entities", getTotalEntityCount());
        stats.put("online_players", Bukkit.getOnlinePlayers().size());
        stats.put("max_players", Bukkit.getMaxPlayers());
        stats.put("used_memory_mb", getUsedMemoryMB());
        stats.put("max_memory_mb", getMaxMemoryMB());
        stats.put("tps", getTPS());
        return new HashMap<>(stats);
    }

    private int getLoadedChunkCount() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getLoadedChunks().length;
        }
        return total;
    }

    private int getTotalEntityCount() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getEntityCount();
        }
        return total;
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
    }

    public boolean isEnabled() {
        return enabled;
    }
}
