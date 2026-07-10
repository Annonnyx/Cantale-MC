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
        this.maxEntitiesPerChunk = plugin.getConfig().getInt("optimization.max-entities-per-chunk", 30);
        this.clearGroundItems = plugin.getConfig().getBoolean("optimization.clear-ground-items", true);
        this.clearArrows = plugin.getConfig().getBoolean("optimization.clear-arrows", true);
        this.clearExperience = plugin.getConfig().getBoolean("optimization.clear-experience", false);
        this.optimizeChunks = plugin.getConfig().getBoolean("optimization.optimize-chunks", true);
        this.tpsWarningThreshold = plugin.getConfig().getInt("optimization.tps-warning-threshold", 15);
        this.villagersDisabled = plugin.getConfig().getBoolean("optimization.villagers-disabled", false);
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

        // Entités nommées : conservées par défaut sauf si includeNamed
        if (!includeNamed && e.customName() != null) return true;

        return false;
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
            // Nettoyer les items au sol
            if (clearGroundItems) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item item) {
                        // Supprimer les items au sol vieux de plus de 2 minutes
                        if (item.getTicksLived() > 20 * 120) {
                            item.remove();
                            removedItems++;
                        }
                    }
                }
            }

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

            // Limiter les entités par chunk
            Map<Long, List<Entity>> chunkEntities = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || entity instanceof Villager) continue;
                if (entity instanceof Animals && !((Animals) entity).isAdult()) continue; // Protéger les bébés

                long chunkKey = getChunkKey(entity.getLocation().getChunk().getX(), entity.getLocation().getChunk().getZ());
                chunkEntities.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(entity);
            }

            for (Map.Entry<Long, List<Entity>> entry : chunkEntities.entrySet()) {
                List<Entity> entities = entry.getValue();
                if (entities.size() > maxEntitiesPerChunk) {
                    // Trier par âge (les plus vieux en premier)
                    entities.sort((a, b) -> Long.compare(b.getTicksLived(), a.getTicksLived()));
                    for (int i = maxEntitiesPerChunk; i < entities.size(); i++) {
                        entities.get(i).remove();
                        removedExcess++;
                    }
                }
            }

            // Limiter les items par chunk
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
                    if (items.size() > maxItemsPerChunk) {
                        items.sort((a, b) -> Long.compare(b.getTicksLived(), a.getTicksLived()));
                        for (int i = maxItemsPerChunk; i < items.size(); i++) {
                            items.get(i).remove();
                            removedItems++;
                        }
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
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    if (item.getTicksLived() > 20 * 30) { // 30 secondes en urgence
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
