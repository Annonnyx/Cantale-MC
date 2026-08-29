package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Fusionne les entités Item au sol pour réduire leur nombre entre deux passages
 * de ClearLag (ex. minage netherrack : 80 entités → 1-3 stacks).
 *
 * Deux mécanismes complémentaires :
 * <ul>
 *   <li>fusion au drop : 3 ticks après le spawn (la fusion vanilla a déjà eu lieu),
 *       avec les items similaires dans un petit rayon ;</li>
 *   <li>balayage périodique : pour chaque chunk chargé dépassant un seuil d'items,
 *       fusion des similaires (coût borné au chunk, chunks chargés seulement).</li>
 * </ul>
 *
 * Hôte = entité la plus ancienne ; plafond {@code max-stack} par entité, l'excédent
 * reste sur une entité séparée (le pickup remplit plusieurs stacks d'inventaire).
 *
 * Limite dure : le codec vanilla borne le count d'un ItemStack à {@value #MAX_SERIALIZED_STACK}
 * — au-delà, l'entité n'est plus sérialisable et est perdue au chunk unload
 * (warnings Moonrise « Value must be within range [1;99] »). Le plafond configuré est
 * donc clampé à 99 et les entités historiques oversized sont re-découpées au balayage.
 *
 * Protections : jamais de fusion sur un item non ramassable, en pickup infini,
 * invulnérable, nommé (hologrammes / décoratifs d'autres systèmes) ou invalide.
 */
public class ItemMergeManager implements Listener {

    /** Borne haute du codec vanilla ItemStack (champ count ∈ [1;99] à la sérialisation). */
    private static final int MAX_SERIALIZED_STACK = 99;

    private final CantalePlugin plugin;

    private boolean enabled;
    private double radius;
    private int intervalSeconds;
    private int minItemsPerChunk;
    private int maxStack;

    private BukkitTask sweepTask;

    public ItemMergeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("optimization.item-merge.enabled", true);
        this.radius = Math.max(1.0, plugin.getConfig().getDouble("optimization.item-merge.radius", 4.5));
        this.intervalSeconds = Math.max(5, plugin.getConfig().getInt("optimization.item-merge.interval-seconds", 15));
        this.minItemsPerChunk = Math.max(2, plugin.getConfig().getInt("optimization.item-merge.min-items-per-chunk", 6));
        // Clamp dur à 99 : au-delà, l'ItemEntity n'est plus sauvegardée (codec vanilla [1;99]).
        this.maxStack = Math.min(MAX_SERIALIZED_STACK,
                Math.max(2, plugin.getConfig().getInt("optimization.item-merge.max-stack", MAX_SERIALIZED_STACK)));
    }

    public void start() {
        stop();
        if (!enabled) {
            plugin.getLogger().info("[Opti] ItemMerge : désactivé (optimization.item-merge.enabled=false).");
            return;
        }
        long period = intervalSeconds * 20L;
        this.sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepPass, period, period);
        plugin.getLogger().info("[Opti] ItemMerge : fusion activée (rayon " + radius
                + ", sweep " + intervalSeconds + "s, max " + maxStack + ").");
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ─── Fusion au drop ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!enabled) return;
        Item item = event.getEntity();
        // Jamais dans l'event : 3 ticks plus tard, la fusion vanilla a déjà agi
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid() && !item.isDead()) {
                mergeAroundDrop(item);
            }
        }, 3L);
    }

    private void mergeAroundDrop(Item source) {
        if (!isMergeable(source)) return;
        List<Item> candidates = new ArrayList<>();
        candidates.add(source);
        for (Entity nearby : source.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Item other
                    && !other.getUniqueId().equals(source.getUniqueId())
                    && isMergeable(other)) {
                candidates.add(other);
            }
        }
        if (candidates.size() < 2) return;
        mergeGroup(candidates);
    }

    // ─── Balayage périodique ────────────────────────────────────────

    private void sweepPass() {
        if (!enabled) return;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                List<Item> items = null;
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Item item && isMergeable(item)) {
                        normalizeOversized(item);
                        if (items == null) items = new ArrayList<>();
                        items.add(item);
                    }
                }
                if (items != null && items.size() >= minItemsPerChunk) {
                    mergeGroup(items);
                }
            }
        }
    }

    /**
     * Répare les entités créées avec l'ancien plafond (&gt; {@value #MAX_SERIALIZED_STACK},
     * jamais sauvegardées au chunk unload) : l'excédent est re-posé en entités
     * séparées à la même position, qui re-fusionneront ensuite sans dépasser le plafond.
     */
    private void normalizeOversized(Item item) {
        ItemStack stack = item.getItemStack();
        int amount = stack.getAmount();
        if (amount <= MAX_SERIALIZED_STACK) return;

        ItemStack host = stack.clone();
        host.setAmount(MAX_SERIALIZED_STACK);
        item.setItemStack(host);

        int remaining = amount - MAX_SERIALIZED_STACK;
        while (remaining > 0) {
            ItemStack extra = stack.clone();
            extra.setAmount(Math.min(MAX_SERIALIZED_STACK, remaining));
            Item dropped = item.getWorld().dropItem(item.getLocation(), extra);
            dropped.setPickupDelay(item.getPickupDelay());
            remaining -= extra.getAmount();
        }
        plugin.getLogger().info("[Opti] ItemMerge : stack oversized x" + amount + " "
                + stack.getType() + " re-découpé @ " + item.getLocation().getBlockX() + ","
                + item.getLocation().getBlockY() + "," + item.getLocation().getBlockZ());
    }

    // ─── Règles de fusion ───────────────────────────────────────────

    /**
     * Regroupe par similarité ({@link ItemStack#isSimilar}, ignore le count) puis
     * fusionne chaque groupe dans le plus ancien, plafonné à {@code maxStack} :
     * quand l'hôte est plein, l'item suivant le plus ancien devient le nouvel hôte.
     */
    private void mergeGroup(List<Item> items) {
        List<List<Item>> groups = new ArrayList<>();
        for (Item item : items) {
            if (!isMergeable(item)) continue;
            List<Item> target = null;
            for (List<Item> group : groups) {
                if (group.get(0).getItemStack().isSimilar(item.getItemStack())) {
                    target = group;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                groups.add(target);
            }
            target.add(item);
        }

        for (List<Item> group : groups) {
            if (group.size() < 2) continue;
            // Répare d'éventuelles entités legacy au-delà de la borne de sérialisation
            group.forEach(this::normalizeOversized);
            // Hôte = plus ancien (ticksLived max) : l'entité visible reste stable
            group.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived()));

            Item host = null;
            for (Item candidate : group) {
                if (!candidate.isValid() || candidate.isDead()) continue;
                if (host == null) {
                    host = candidate;
                    continue;
                }
                ItemStack hostStack = host.getItemStack();
                int room = maxStack - hostStack.getAmount();
                if (room <= 0) {
                    // Hôte plein : l'excédent reste, le candidat devient le nouvel hôte
                    host = candidate;
                    continue;
                }
                int amount = candidate.getItemStack().getAmount();
                int take = Math.min(room, amount);
                hostStack.setAmount(hostStack.getAmount() + take);
                host.setItemStack(hostStack);
                if (take >= amount) {
                    candidate.remove();
                } else {
                    ItemStack leftover = candidate.getItemStack();
                    leftover.setAmount(amount - take);
                    candidate.setItemStack(leftover);
                }
            }
        }
    }

    /**
     * Gardes anti-casse : ne jamais toucher aux items décoratifs / hologrammes
     * (non ramassables, pickup infini 32767, invulnérables, nommés) ni aux entités mortes.
     */
    private boolean isMergeable(Item item) {
        if (item == null || item.isDead() || !item.isValid()) return false;
        if (!item.canPlayerPickup()) return false;
        if (item.getPickupDelay() >= 32767) return false;
        if (item.isInvulnerable()) return false;
        if (item.customName() != null) return false;
        ItemStack stack = item.getItemStack();
        return !stack.getType().isAir() && stack.getAmount() > 0;
    }
}
