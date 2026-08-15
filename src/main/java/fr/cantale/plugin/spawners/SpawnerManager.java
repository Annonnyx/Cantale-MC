package fr.cantale.plugin.spawners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class SpawnerManager {

    private final CantalePlugin plugin;
    private final SpawnerKeys keys;
    private final SpawnerItems items;
    private final SpawnerDAO dao;
    private final Map<String, SpawnerRecord> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();

    /** Rayon carré max entre un hologramme et la position attendue du SIEN (x+0.5, y+1.35, z+0.5).
     *  En deçà, un TextDisplay taggé appartient à un spawner adjacent et ne doit pas être touché. */
    private static final double HOLOGRAM_MATCH_RADIUS_SQUARED = 0.75 * 0.75;

    private BukkitTask spawnTask;
    private boolean loaded;
    /** Ralentissement piloté par le PerformanceGovernor (1.0 = cadence nominale). */
    private volatile double rateMultiplier = 1.0;

    public SpawnerManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.keys = new SpawnerKeys(plugin);
        this.items = new SpawnerItems(keys);
        this.dao = new SpawnerDAO(plugin);
    }

    public SpawnerKeys keys() {
        return keys;
    }

    public SpawnerItems items() {
        return items;
    }

    public void start() {
        stop();
        loadFromDatabase();
        // 1 s : assez fin pour T8 (5 s) ; intervalMs a un plancher de 1000 ms.
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawn, 20L, 20L);
    }

    public void stop() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (isSpawnerHologram(entity)) {
                    entity.remove();
                }
            }
        }
        holograms.clear();
    }

    private void loadFromDatabase() {
        if (plugin.getDatabaseManager() == null || plugin.getDatabaseManager().getConnection() == null) {
            plugin.getLogger().warning("[Spawners] BDD indisponible — nouvel essai dans 10 s.");
            Bukkit.getScheduler().runTaskLater(plugin, this::loadFromDatabase, 20L * 10);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<SpawnerRecord> records = dao.loadAll();
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.clear();
                long now = System.currentTimeMillis();
                for (SpawnerRecord record : records) {
                    SpawnerMob canonical = record.mob().canonicalMob();
                    SpawnerRecord toStore = record;
                    if (canonical != record.mob()) {
                        toStore = record.withMob(canonical);
                        SpawnerRecord persist = toStore;
                        persistAsync(() -> dao.upsert(persist));
                        plugin.getLogger().info("[Spawners] " + record.mob().name() + " → " + canonical.name()
                                + " en " + record.world() + " " + record.x() + "," + record.y() + "," + record.z());
                    }
                    toStore.setNextSpawnAtMs(now + intervalMs(toStore));
                    cache.put(toStore.key(), toStore);
                }
                loaded = true;
                restoreHologramsLoadedChunks();
                plugin.getLogger().info("[Spawners] " + cache.size() + " spawner(s) chargé(s).");
            });
        });
    }

    public ItemStack createItem(SpawnerMob mob, int tier) {
        return items.create(mob, tier);
    }

    public boolean isCantaleSpawnerItem(ItemStack stack) {
        return items.isCantaleSpawner(stack);
    }

    public SpawnerRecord getAt(Block block) {
        if (block == null) return null;
        return cache.get(SpawnerRecord.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public boolean chunkHasSpawners(String world, int chunkX, int chunkZ) {
        for (SpawnerRecord record : cache.values()) {
            if (record.world().equals(world) && record.chunkX() == chunkX && record.chunkZ() == chunkZ) {
                return true;
            }
        }
        return false;
    }

    public boolean place(Block block, SpawnerMob mob, int tier, int factionId) {
        SpawnerMob canonical = mob.canonicalMob();
        int clamped = clampTier(tier);
        SpawnerRecord record = new SpawnerRecord(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                canonical, clamped, factionId);
        record.setNextSpawnAtMs(System.currentTimeMillis() + intervalMs(record));
        cache.put(record.key(), record);
        applyBlockState(block, record);
        persistAsync(() -> dao.upsert(record));
        // 1 tick : le BlockStateMeta de l'item (appliqué APRÈS le BlockPlaceEvent) peut écraser
        // l'état posé ci-dessus → réapplication complète + update client, puis hologramme.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!cache.containsKey(record.key())) return;
            Location loc = record.location();
            if (loc != null && loc.getBlock().getType() == Material.SPAWNER) {
                applyBlockState(loc.getBlock(), record);
            }
            upsertHologram(record);
        });
        return true;
    }

    public void updateTier(SpawnerRecord record, int newTier) {
        record.setTier(clampTier(newTier));
        record.setNextSpawnAtMs(System.currentTimeMillis() + intervalMs(record));
        Location loc = record.location();
        if (loc != null) {
            applyBlockState(loc.getBlock(), record);
        }
        upsertHologram(record);
        persistAsync(() -> dao.upsert(record));
    }

    public void remove(SpawnerRecord record, boolean dropItem, Location dropAt) {
        cache.remove(record.key());
        Location holo = hologramLocation(record);
        removeHologram(record.key());
        if (holo != null && holo.getWorld() != null
                && holo.getWorld().isChunkLoaded(record.chunkX(), record.chunkZ())) {
            removeOrphanHolograms(holo);
        }
        persistAsync(() -> dao.delete(record.world(), record.x(), record.y(), record.z()));
        if (dropItem && dropAt != null && dropAt.getWorld() != null) {
            dropAt.getWorld().dropItemNaturally(dropAt, items.create(record.mob().canonicalMob(), record.tier()));
        }
    }

    public void restoreHolograms(Chunk chunk) {
        if (chunk == null) return;
        String world = chunk.getWorld().getName();
        for (SpawnerRecord record : cache.values()) {
            if (record.world().equals(world) && record.chunkX() == chunk.getX() && record.chunkZ() == chunk.getZ()) {
                Location loc = record.location();
                if (loc == null) continue;
                if (loc.getBlock().getType() == Material.SPAWNER) {
                    applyBlockState(loc.getBlock(), record);
                    upsertHologram(record);
                } else {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!cache.containsKey(record.key())) return;
                        Location later = record.location();
                        if (later != null && later.getBlock().getType() == Material.SPAWNER) {
                            applyBlockState(later.getBlock(), record);
                            upsertHologram(record);
                        }
                    }, 5L);
                }
            }
        }
    }

    public void unloadHolograms(Chunk chunk) {
        if (chunk == null) return;
        String world = chunk.getWorld().getName();
        // Hologrammes persistants : on lâche seulement le cache UUID.
        // L'entité reste dans le chunk et sera réutilisée au reload.
        holograms.entrySet().removeIf(entry -> {
            SpawnerRecord record = cache.get(entry.getKey());
            return record != null
                    && record.world().equals(world)
                    && record.chunkX() == chunk.getX()
                    && record.chunkZ() == chunk.getZ();
        });
    }

    public boolean isSpawnerHologram(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(keys.hologram, PersistentDataType.BYTE);
    }

    public boolean isSpawnerMob(Entity entity) {
        return entity != null && keys.isSpawnerMob(entity.getPersistentDataContainer());
    }

    public double crateChancePercent(CrateType type) {
        return switch (type) {
            case VOTE -> 0;
            case RARE -> plugin.getConfig().getDouble("spawners.crate.rare-chance-percent", 5.0);
            case EPIC -> plugin.getConfig().getDouble("spawners.crate.epic-chance-percent", 3.0);
            case MYTHIC -> plugin.getConfig().getDouble("spawners.crate.mythic-chance-percent", 1.5);
            case LEGENDARY -> plugin.getConfig().getDouble("spawners.crate.legendary-chance-percent", 1.5);
        };
    }

    public ItemStack rollCrateSpawner(CrateType type) {
        return rollSpawnerOfRarity(rollCrateRarity(type));
    }

    /** Tire un spawner d'une rareté exacte (palier 1). null si la rareté est vide/invalide. */
    public ItemStack rollSpawnerOfRarity(SpawnerRarity rarity) {
        if (rarity == null) return null;
        List<SpawnerMob> pool = SpawnerMob.ofRarity(rarity);
        if (pool.isEmpty()) return null;
        SpawnerMob mob = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return items.create(mob, 1);
    }

    private SpawnerRarity rollCrateRarity(CrateType type) {
        double roll = ThreadLocalRandom.current().nextDouble();
        return switch (type) {
            case VOTE -> null;
            case RARE -> SpawnerRarity.RARE;
            case EPIC -> roll < 0.70 ? SpawnerRarity.EPIC : SpawnerRarity.RARE;
            case MYTHIC, LEGENDARY -> {
                if (roll < 0.60) yield SpawnerRarity.MYTHIC;
                if (roll < 0.85) yield SpawnerRarity.EPIC;
                yield SpawnerRarity.RARE;
            }
        };
    }

    public int maxTier() {
        return Math.max(1, plugin.getConfig().getInt("spawners.max-tier", 8));
    }

    public int clampTier(int tier) {
        return Math.max(1, Math.min(maxTier(), tier));
    }

    /**
     * Multiplicateur de ralentissement appliqué à l'intervalle de spawn
     * (PerformanceGovernor en mode dégradé/critique). 1.0 = nominal, plancher 1.0.
     * Effet au prochain cycle de chaque spawner (re-planification avec la nouvelle cadence).
     */
    public void setRateMultiplier(double multiplier) {
        this.rateMultiplier = Math.max(1.0, multiplier);
    }

    public double getRateMultiplier() {
        return rateMultiplier;
    }

    /**
     * Intervalle d'un cycle : {@code base-interval-seconds / palier}.
     * Golem de fer : encore multiplié par {@code iron-golem-interval-multiplier} (défaut 2).
     * Governor : multiplié par {@link #rateMultiplier} (ralentissement sous charge).
     * Plancher 1 s (le tick spawn tourne toutes les 20 ticks).
     */
    public long intervalMs(SpawnerRecord record) {
        double base = plugin.getConfig().getDouble("spawners.base-interval-seconds", 12.0);
        int tier = clampTier(record.tier());
        double intervalSeconds = base / tier;
        if (record.mob().slowSpawn()) {
            intervalSeconds *= plugin.getConfig().getDouble("spawners.iron-golem-interval-multiplier", 2.0);
        }
        intervalSeconds *= rateMultiplier;
        return Math.max(1000L, Math.round(intervalSeconds * 1000.0));
    }

    /** Mobs par cycle = palier (1…max-tier). */
    public int mobsPerCycle(int tier) {
        return clampTier(tier);
    }

    private void tickSpawn() {
        if (!loaded || cache.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (SpawnerRecord record : cache.values()) {
            if (record.nextSpawnAtMs() > now) continue;
            trySpawnCycle(record);
            record.setNextSpawnAtMs(now + intervalMs(record));
        }
    }

    private void trySpawnCycle(SpawnerRecord record) {
        Location loc = record.location();
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(record.chunkX(), record.chunkZ())) return;
        if (loc.getBlock().getType() != Material.SPAWNER) return;

        Claim claim = plugin.getFactionManager().getClaimAt(record.world(), record.chunkX(), record.chunkZ());
        if (claim == null || claim.getFactionId() != record.factionId()) {
            return;
        }
        if (!record.mob().obtainable()) {
            return;
        }

        int count = mobsPerCycle(record.tier());
        int attemptsPerMob = Math.max(1, plugin.getConfig().getInt("spawners.spawn-attempts-per-mob", 8));
        for (int i = 0; i < count; i++) {
            Location spawnAt = findSpawnLocation(record, attemptsPerMob);
            if (spawnAt == null) continue;
            spawnMob(record, spawnAt);
        }
    }

    private Location findSpawnLocation(SpawnerRecord record, int attempts) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return null;
        int range = Math.max(1, plugin.getConfig().getInt("spawners.spawn-range", 4));
        int minX = record.chunkX() << 4;
        int maxX = minX + 15;
        int minZ = record.chunkZ() << 4;
        int maxZ = minZ + 15;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < attempts; i++) {
            int x = Math.max(minX, Math.min(maxX, record.x() + rng.nextInt(-range, range + 1)));
            int z = Math.max(minZ, Math.min(maxZ, record.z() + rng.nextInt(-range, range + 1)));
            int y = record.y() + rng.nextInt(-1, 2);
            if (y < world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) continue;

            // Spawn aérien / aquatique voulu (farms à chute ou à eau) : aucune exigence de sol,
            // air et eau acceptés. Seule interdiction : bloc solide aux pieds/tête (suffocation).
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (feet.getType().isSolid() || head.getType().isSolid()) continue;
            if (record.mob() == SpawnerMob.GHAST || record.mob() == SpawnerMob.IRON_GOLEM) {
                Block above = world.getBlockAt(x, y + 2, z);
                if (above.getType().isSolid()) continue;
            }
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private void spawnMob(SpawnerRecord record, Location at) {
        World world = at.getWorld();
        if (world == null) return;
        try {
            // Reason SPAWNER (pas CUSTOM) : l'allow-list PASDIC (ClaimListener) n'accepte que les
            // spawns de spawner ; le stacking (MobStackListener, MONITOR) ne filtre pas la reason.
            Entity spawned = world.spawnEntity(at, record.mob().entityType(), CreatureSpawnEvent.SpawnReason.SPAWNER);
            if (!(spawned instanceof LivingEntity living)) {
                spawned.remove();
                return;
            }
            living.getPersistentDataContainer().set(keys.mobFlag, PersistentDataType.BYTE, (byte) 1);
            living.setRemoveWhenFarAway(true);
            if (living instanceof Slime slime) {
                slime.setSize(2);
            }
            if (living instanceof MagmaCube magma) {
                magma.setSize(2);
            }
            if (living instanceof IronGolem golem) {
                golem.setPlayerCreated(true);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Spawn spawner échoué: " + e.getMessage());
        }
    }

    public void applyBlockState(Block block, SpawnerRecord record) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;
        spawner.setSpawnedType(record.mob().entityType());
        spawner.setDelay(Integer.MAX_VALUE);
        // CraftBukkit valide min <= max à chaque setter : le max DOIT être monté avant
        // le min, sinon MAX_VALUE > 800 (défaut vanilla) → IllegalArgumentException.
        spawner.setMaxSpawnDelay(Integer.MAX_VALUE);
        spawner.setMinSpawnDelay(Integer.MAX_VALUE);
        spawner.setSpawnCount(0);
        // Le client n'anime le mob de la cage que si le spawner est « actif »
        // (joueur dans RequiredPlayerRange) : 0 → cage figée/vide. Le spawn vanilla
        // reste impossible (delay MAX_VALUE, spawnCount 0, maxNearby 0 + SpawnerSpawnEvent annulé).
        spawner.setRequiredPlayerRange(
                Math.max(1, plugin.getConfig().getInt("spawners.cage-activation-range", 16)));
        spawner.setMaxNearbyEntities(0);
        spawner.getPersistentDataContainer().set(keys.type, PersistentDataType.STRING, record.mob().name());
        spawner.getPersistentDataContainer().set(keys.tier, PersistentDataType.INTEGER, record.tier());
        // update(true, true) : physique incluse → paquet block-entity émis, le client
        // reçoit le SpawnData (mob de la cage) même juste après la pose.
        spawner.update(true, true);
    }

    private void restoreHologramsLoadedChunks() {
        for (SpawnerRecord record : cache.values()) {
            Location loc = record.location();
            if (loc == null) continue;
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(record.chunkX(), record.chunkZ())) continue;
            if (loc.getBlock().getType() == Material.SPAWNER) {
                applyBlockState(loc.getBlock(), record);
                upsertHologram(record);
            }
        }
    }

    private Location hologramLocation(SpawnerRecord record) {
        Location loc = record.location();
        if (loc == null || loc.getWorld() == null) return null;
        return loc.clone().add(0.5, 1.35, 0.5);
    }

    private void upsertHologram(SpawnerRecord record) {
        Location holo = hologramLocation(record);
        if (holo == null) return;
        if (!holo.getWorld().isChunkLoaded(record.chunkX(), record.chunkZ())) return;

        Component label = Component.text(record.mob().hologramLabel(record.tier()), NamedTextColor.YELLOW);
        TextDisplay existing = findHologram(record, holo);
        if (existing != null && existing.isValid()) {
            applyHologramStyle(existing, label);
            existing.teleport(holo);
            holograms.put(record.key(), existing.getUniqueId());
            return;
        }

        removeHologram(record.key());
        removeOrphanHolograms(holo);

        try {
            TextDisplay display = holo.getWorld().spawn(holo, TextDisplay.class, d -> applyHologramStyle(d, label));
            applyHologramStyle(display, label);
            holograms.put(record.key(), display.getUniqueId());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[Spawners] Hologramme impossible en "
                    + record.world() + " " + record.x() + "," + record.y() + "," + record.z()
                    + " : " + ex.getMessage());
        }
    }

    private void applyHologramStyle(TextDisplay display, Component label) {
        display.text(label);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        // Alpha 0 = fond transparent OK ; le texte restait invisible dans le noir sans brightness.
        display.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
        display.setBrightness(new Display.Brightness(15, 15));
        display.setPersistent(true);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.getPersistentDataContainer().set(keys.hologram, PersistentDataType.BYTE, (byte) 1);
        display.setMetadata("cantale_keep", new FixedMetadataValue(plugin, true));
    }

    private TextDisplay findHologram(SpawnerRecord record, Location holo) {
        UUID id = holograms.get(record.key());
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof TextDisplay display && entity.isValid()) {
                return display;
            }
        }
        // Position déterministe par spawner : un TextDisplay taggé trop loin de la position
        // attendue appartient à un spawner adjacent (farms côte à côte) — ne pas y toucher.
        for (Entity entity : holo.getWorld().getNearbyEntities(holo, 1.25, 1.75, 1.25)) {
            if (entity instanceof TextDisplay display && isSpawnerHologram(entity) && entity.isValid()
                    && entity.getLocation().distanceSquared(holo) <= HOLOGRAM_MATCH_RADIUS_SQUARED) {
                return display;
            }
        }
        return null;
    }

    private void removeOrphanHolograms(Location holo) {
        for (Entity entity : holo.getWorld().getNearbyEntities(holo, 1.25, 1.75, 1.25)) {
            if (isSpawnerHologram(entity)
                    && entity.getLocation().distanceSquared(holo) <= HOLOGRAM_MATCH_RADIUS_SQUARED) {
                entity.remove();
            }
        }
    }

    private void removeHologram(String key) {
        UUID id = holograms.remove(key);
        if (id == null) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private void persistAsync(Runnable sql) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, sql);
    }

    public boolean canPlayerPlace(org.bukkit.entity.Player player, Block block) {
        // Bypass admin (/f bypass) : pose de spawners possible partout, y compris claims étrangers.
        if (plugin.getAdminBypassManager().isBypassing(player)) return true;
        Faction fac = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (fac == null) return false;
        Claim claim = plugin.getFactionManager().getClaimAt(
                block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
        return claim != null && claim.getFactionId() == fac.getId();
    }

    public String denyPlaceMessage(org.bukkit.entity.Player player, Block block) {
        Faction fac = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (fac == null) {
            return "§8[§6Cantale§8] §cTu dois être dans une faction pour poser un spawner.";
        }
        Claim claim = plugin.getFactionManager().getClaimAt(
                block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
        if (claim == null || claim.getFactionId() != fac.getId()) {
            return "§8[§6Cantale§8] §cLes spawners se posent uniquement dans les claims de ta faction.";
        }
        return "§8[§6Cantale§8] §cTu ne peux pas poser de spawner ici.";
    }
}
