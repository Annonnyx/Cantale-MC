package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Empile les animaux passifs de ferme (même type) pour n'en simuler qu'un.
 * Taille du stack en PDC ; affichage via custom name « Mouton ×12 ».
 * Les Animals sont exclus du cull max-entities-per-chunk (le stacking remplace le cull).
 */
public class MobStackManager {

    private static final Set<EntityType> STACKABLE_TYPES = EnumSet.of(
            EntityType.SHEEP,
            EntityType.COW,
            EntityType.CHICKEN,
            EntityType.PIG,
            EntityType.RABBIT,
            EntityType.MOOSHROOM,
            EntityType.GOAT
    );

    private static final Map<EntityType, String> FRENCH_NAMES = Map.of(
            EntityType.SHEEP, "Mouton",
            EntityType.COW, "Vache",
            EntityType.CHICKEN, "Poulet",
            EntityType.PIG, "Cochon",
            EntityType.RABBIT, "Lapin",
            EntityType.MOOSHROOM, "Champimeuh",
            EntityType.GOAT, "Chèvre"
    );

    private final CantalePlugin plugin;
    private final NamespacedKey stackKey;
    private final NamespacedKey skipMergeKey;

    private boolean enabled;
    private int maxStack;
    private double radius;
    private int intervalSeconds;
    private boolean stackBabies;
    private boolean stackNamed;
    private boolean stackTamed;

    private BukkitTask task;

    public MobStackManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey(plugin, "mob_stack");
        this.skipMergeKey = new NamespacedKey(plugin, "mob_stack_skip");
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("optimization.mob-stacking.enabled", true);
        this.maxStack = Math.max(2, plugin.getConfig().getInt("optimization.mob-stacking.max-stack", 64));
        this.radius = Math.max(1.0, plugin.getConfig().getDouble("optimization.mob-stacking.radius", 8.0));
        this.intervalSeconds = Math.max(5, plugin.getConfig().getInt("optimization.mob-stacking.interval-seconds", 10));
        this.stackBabies = plugin.getConfig().getBoolean("optimization.mob-stacking.stack-babies", false);
        this.stackNamed = plugin.getConfig().getBoolean("optimization.mob-stacking.stack-named", false);
        this.stackTamed = plugin.getConfig().getBoolean("optimization.mob-stacking.stack-tamed", false);
    }

    public void start() {
        stop();
        if (!enabled) {
            plugin.getLogger().info("[MobStack] Désactivé (optimization.mob-stacking.enabled=false)");
            return;
        }
        long period = intervalSeconds * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::stackPass, period, period);
        plugin.getLogger().info("[MobStack] Actif — max=" + maxStack + ", radius=" + radius
                + ", interval=" + intervalSeconds + "s (Animals exclus du cull chunk)");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public double getRadius() {
        return radius;
    }

    /** True si l'entité porte une taille de stack Cantale (même à 1 après unsplit). */
    public boolean isStacked(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(stackKey, PersistentDataType.INTEGER);
    }

    public int getStackSize(Entity entity) {
        if (entity == null) return 1;
        Integer size = entity.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return size == null ? 1 : Math.max(1, size);
    }

    public void setStackSize(LivingEntity entity, int size) {
        int clamped = Math.max(1, Math.min(maxStack, size));
        if (clamped <= 1) {
            // Retirer le libellé ×N avant d'enlever le PDC (sinon clearStackDisplay croit à un nom joueur)
            entity.customName(null);
            entity.setCustomNameVisible(false);
            entity.getPersistentDataContainer().remove(stackKey);
        } else {
            entity.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, clamped);
            updateStackDisplay(entity, clamped);
        }
    }

    /**
     * Marque temporairement une entité pour ne pas la re-merger immédiatement
     * (ex. après unsplit sur kill).
     */
    public void markSkipMerge(Entity entity, int ticks) {
        entity.getPersistentDataContainer().set(skipMergeKey, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.getPersistentDataContainer().remove(skipMergeKey);
            }
        }, Math.max(1, ticks));
    }

    private boolean hasSkipMerge(Entity entity) {
        return entity.getPersistentDataContainer().has(skipMergeKey, PersistentDataType.BYTE);
    }

    public boolean isStackableCandidate(LivingEntity entity) {
        if (!enabled || entity == null || !entity.isValid() || entity.isDead()) return false;
        if (!STACKABLE_TYPES.contains(entity.getType())) return false;
        if (hasSkipMerge(entity)) return false;
        if (entity.isLeashed()) return false;
        if (!entity.getPassengers().isEmpty() || entity.getVehicle() != null) return false;

        if (entity instanceof Ageable ageable) {
            if (!ageable.isAdult() && !stackBabies) return false;
        }

        if (entity instanceof Tameable tameable && tameable.isTamed() && !stackTamed) return false;

        // Nom joueur : customName sans PDC stack (le display stack pose aussi un customName)
        if (!stackNamed && entity.customName() != null && !isStacked(entity)) return false;

        if (entity.hasMetadata("cantale_npc") || entity.hasMetadata("cantale_keep")) return false;

        return true;
    }

    /** Clé de compatibilité pour merger (type + détails mouton / lapin / champimeuh). */
    public String stackGroupKey(LivingEntity entity) {
        StringBuilder key = new StringBuilder(entity.getType().name());
        if (entity instanceof Ageable ageable) {
            key.append(ageable.isAdult() ? ":adult" : ":baby");
        }
        if (entity instanceof Sheep sheep) {
            DyeColor color = sheep.getColor();
            key.append(':').append(color == null ? "WHITE" : color.name());
            key.append(sheep.isSheared() ? ":sheared" : ":wool");
        }
        if (entity instanceof Rabbit rabbit) {
            key.append(':').append(rabbit.getRabbitType().name());
        }
        if (entity instanceof MushroomCow mooshroom) {
            key.append(':').append(mooshroom.getVariant().name());
        }
        return key.toString();
    }

    public void updateStackDisplay(LivingEntity entity, int size) {
        if (size <= 1) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
            return;
        }
        String label = FRENCH_NAMES.getOrDefault(entity.getType(), entity.getType().name());
        entity.customName(Component.text(label + " ×" + size, NamedTextColor.YELLOW));
        entity.setCustomNameVisible(true);
    }

    /** Recopie l'apparence (mouton / lapin / champimeuh) pour un unsplit après mort. */
    public void copyAppearance(LivingEntity from, LivingEntity to) {
        if (from instanceof Sheep fromSheep && to instanceof Sheep toSheep) {
            toSheep.setColor(fromSheep.getColor());
            toSheep.setSheared(fromSheep.isSheared());
        }
        if (from instanceof Rabbit fromRabbit && to instanceof Rabbit toRabbit) {
            toRabbit.setRabbitType(fromRabbit.getRabbitType());
        }
        if (from instanceof MushroomCow fromMoosh && to instanceof MushroomCow toMoosh) {
            toMoosh.setVariant(fromMoosh.getVariant());
        }
        if (from instanceof Ageable fromAge && to instanceof Ageable toAge) {
            if (fromAge.isAdult()) {
                toAge.setAdult();
            } else {
                toAge.setBaby();
            }
        }
    }

    /**
     * Réduit le stack d'1 après un coup létal : loot / XP d'une mort, entité conservée.
     * @return true si le coup a été absorbé par le stack (pas de vraie mort)
     */
    public boolean tryAbsorbLethalHit(LivingEntity entity, Entity killer) {
        int size = getStackSize(entity);
        if (size <= 1) return false;

        dropLootOnce(entity, killer instanceof Player player ? player : null);
        dropXpOnce(entity, killer);

        int remaining = size - 1;
        setStackSize(entity, remaining);

        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth(maxHealth.getValue());
        } else {
            entity.setHealth(entity.getHealth());
        }

        // Remettre un mouton tondu en laine si le stack contient encore des moutons « wool »
        // (on stacke sheared/wool séparément → pas de mélange)

        markSkipMerge(entity, 40);
        return true;
    }

    private void dropLootOnce(LivingEntity entity, Player killer) {
        Location loc = entity.getLocation();
        if (!(entity instanceof Lootable lootable)) return;
        LootTable table = lootable.getLootTable();
        if (table == null) return;

        LootContext.Builder builder = new LootContext.Builder(loc).lootedEntity(entity);
        if (killer != null) {
            builder.killer(killer);
        }
        Collection<ItemStack> loot = table.populateLoot(ThreadLocalRandom.current(), builder.build());
        World world = loc.getWorld();
        if (world == null) return;
        for (ItemStack stack : loot) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            world.dropItemNaturally(loc, stack);
        }
    }

    private void dropXpOnce(LivingEntity entity, Entity killer) {
        // XP approximatif vanilla pour animaux de ferme (2–4 typique)
        int xp = 1 + ThreadLocalRandom.current().nextInt(3);
        if (killer instanceof Player) {
            entity.getWorld().spawn(entity.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
    }

    /** Passe périodique : merge dans chaque monde chargé. */
    public void stackPass() {
        if (!enabled) return;
        for (World world : Bukkit.getWorlds()) {
            stackWorld(world);
        }
    }

    private void stackWorld(World world) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity living : world.getLivingEntities()) {
            if (isStackableCandidate(living)) {
                candidates.add(living);
            }
        }
        if (candidates.size() < 2) return;

        // Grouper par chunk puis tenter merge local (évite O(n²) global)
        Map<Long, List<LivingEntity>> byChunk = new HashMap<>();
        for (LivingEntity e : candidates) {
            long key = chunkKey(e.getLocation().getChunk().getX(), e.getLocation().getChunk().getZ());
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        Set<UUID> consumed = new HashSet<>();
        for (List<LivingEntity> inChunk : byChunk.values()) {
            mergeGroup(inChunk, consumed);
        }
    }

    private void mergeGroup(List<LivingEntity> entities, Set<UUID> consumed) {
        // Index par groupe de compatibilité
        Map<String, List<LivingEntity>> groups = new HashMap<>();
        for (LivingEntity e : entities) {
            if (consumed.contains(e.getUniqueId()) || !e.isValid()) continue;
            groups.computeIfAbsent(stackGroupKey(e), k -> new ArrayList<>()).add(e);
        }

        for (List<LivingEntity> group : groups.values()) {
            if (group.size() < 2) continue;
            // Garder le plus gros stack / plus ancien comme host
            group.sort((a, b) -> {
                int cmp = Integer.compare(getStackSize(b), getStackSize(a));
                if (cmp != 0) return cmp;
                return Integer.compare(b.getTicksLived(), a.getTicksLived());
            });

            LivingEntity host = group.get(0);
            if (consumed.contains(host.getUniqueId()) || !host.isValid()) continue;

            int total = getStackSize(host);
            for (int i = 1; i < group.size(); i++) {
                LivingEntity other = group.get(i);
                if (!other.isValid() || consumed.contains(other.getUniqueId())) continue;
                if (host.getLocation().distanceSquared(other.getLocation()) > radius * radius) continue;

                int otherSize = getStackSize(other);
                int room = maxStack - total;
                if (room <= 0) break;

                int take = Math.min(room, otherSize);
                total += take;
                consumed.add(other.getUniqueId());

                if (take >= otherSize) {
                    other.remove();
                } else {
                    setStackSize(other, otherSize - take);
                }
            }

            if (total != getStackSize(host)) {
                setStackSize(host, total);
            }
            consumed.add(host.getUniqueId());
        }
    }

    /**
     * Tente d'absorber une entité fraîchement spawn dans un stack proche (1 tick après spawn).
     */
    public void tryMergeNearby(LivingEntity spawned) {
        if (!isStackableCandidate(spawned)) return;
        String group = stackGroupKey(spawned);
        double r2 = radius * radius;
        LivingEntity best = null;
        int bestSize = 0;

        for (Entity nearby : spawned.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (living.getUniqueId().equals(spawned.getUniqueId())) continue;
            if (!isStackableCandidate(living)) continue;
            if (!stackGroupKey(living).equals(group)) continue;
            if (spawned.getLocation().distanceSquared(living.getLocation()) > r2) continue;
            int size = getStackSize(living);
            if (size >= maxStack) continue;
            if (size > bestSize) {
                bestSize = size;
                best = living;
            }
        }

        if (best == null) return;

        int add = getStackSize(spawned);
        int room = maxStack - bestSize;
        if (room <= 0) return;

        int take = Math.min(room, add);
        setStackSize(best, bestSize + take);
        if (take >= add) {
            spawned.remove();
        } else {
            setStackSize(spawned, add - take);
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
