package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuestTrackListener implements Listener {

    private static final Map<Material, String> CROPS = Map.ofEntries(
            Map.entry(Material.WHEAT, "WHEAT"),
            Map.entry(Material.CARROTS, "CARROT"),
            Map.entry(Material.POTATOES, "POTATO"),
            Map.entry(Material.BEETROOTS, "BEETROOT"),
            Map.entry(Material.NETHER_WART, "NETHER_WART"),
            Map.entry(Material.SUGAR_CANE, "SUGAR_CANE"),
            Map.entry(Material.MELON, "MELON"),
            Map.entry(Material.PUMPKIN, "PUMPKIN"),
            Map.entry(Material.COCOA, "COCOA"),
            Map.entry(Material.SWEET_BERRY_BUSH, "SWEET_BERRIES"),
            Map.entry(Material.CAVE_VINES, "GLOW_BERRIES"),
            Map.entry(Material.CAVE_VINES_PLANT, "GLOW_BERRIES"),
            Map.entry(Material.KELP, "KELP"),
            Map.entry(Material.KELP_PLANT, "KELP"),
            Map.entry(Material.BAMBOO, "BAMBOO"),
            Map.entry(Material.CACTUS, "CACTUS"),
            Map.entry(Material.CHORUS_PLANT, "CHORUS"),
            Map.entry(Material.CHORUS_FLOWER, "CHORUS"),
            Map.entry(Material.BROWN_MUSHROOM, "BROWN_MUSHROOM"),
            Map.entry(Material.BROWN_MUSHROOM_BLOCK, "BROWN_MUSHROOM"),
            Map.entry(Material.RED_MUSHROOM, "RED_MUSHROOM"),
            Map.entry(Material.RED_MUSHROOM_BLOCK, "RED_MUSHROOM"),
            Map.entry(Material.CRIMSON_FUNGUS, "CRIMSON_FUNGUS"),
            Map.entry(Material.WARPED_FUNGUS, "WARPED_FUNGUS"),
            Map.entry(Material.TORCHFLOWER_CROP, "TORCHFLOWER"),
            Map.entry(Material.TORCHFLOWER, "TORCHFLOWER"),
            Map.entry(Material.PITCHER_CROP, "PITCHER_CROP")
    );

    /** Broken as-is (column / fruit / fungi). Ageable max-age does not apply. */
    private static final Set<Material> HARVEST_AS_IS = Set.of(
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.KELP,
            Material.KELP_PLANT,
            Material.BAMBOO,
            Material.CACTUS,
            Material.CHORUS_PLANT,
            Material.CHORUS_FLOWER,
            Material.BROWN_MUSHROOM,
            Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM,
            Material.RED_MUSHROOM_BLOCK,
            Material.CRIMSON_FUNGUS,
            Material.WARPED_FUNGUS,
            Material.TORCHFLOWER
    );

    private static final Map<Material, String> ORES = Map.ofEntries(
            Map.entry(Material.COAL_ORE, "COAL"),
            Map.entry(Material.DEEPSLATE_COAL_ORE, "COAL"),
            Map.entry(Material.IRON_ORE, "IRON"),
            Map.entry(Material.DEEPSLATE_IRON_ORE, "IRON"),
            Map.entry(Material.COPPER_ORE, "COPPER"),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, "COPPER"),
            Map.entry(Material.GOLD_ORE, "GOLD"),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, "GOLD"),
            Map.entry(Material.NETHER_GOLD_ORE, "NETHER_GOLD"),
            Map.entry(Material.REDSTONE_ORE, "REDSTONE"),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "REDSTONE"),
            Map.entry(Material.LAPIS_ORE, "LAPIS"),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, "LAPIS"),
            Map.entry(Material.DIAMOND_ORE, "DIAMOND"),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "DIAMOND"),
            Map.entry(Material.EMERALD_ORE, "EMERALD"),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, "EMERALD"),
            Map.entry(Material.NETHER_QUARTZ_ORE, "QUARTZ"),
            Map.entry(Material.ANCIENT_DEBRIS, "ANCIENT_DEBRIS")
    );

    private final CantalePlugin plugin;
    private final Map<UUID, long[]> lastBlock = new ConcurrentHashMap<>();
    private static final Set<UUID> teleportSkip = ConcurrentHashMap.newKeySet();

    public QuestTrackListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public static void markTeleport(UUID uuid) {
        teleportSkip.add(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getQuestEngine().loadPlayer(event.getPlayer().getUniqueId());
        plugin.getCollectionManager().loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastBlock.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String crop = CROPS.get(block.getType());
        if (crop != null && isRipe(block)) {
            plugin.getQuestEngine().addProgress(player, QuestObjectiveType.HARVEST, crop, 1);
            plugin.getCollectionManager().add(player.getUniqueId(), "crops", crop, 1);
            return;
        }
        String ore = ORES.get(block.getType());
        if (ore != null && !hasSilk(player)) {
            plugin.getQuestEngine().addProgress(player, QuestObjectiveType.MINE, ore, 1);
            plugin.getCollectionManager().add(player.getUniqueId(), "mining", ore, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        String crop = CROPS.get(event.getHarvestedBlock().getType());
        if (crop == null) return;
        Player player = event.getPlayer();
        plugin.getQuestEngine().addProgress(player, QuestObjectiveType.HARVEST, crop, 1);
        plugin.getCollectionManager().add(player.getUniqueId(), "crops", crop, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        String key = event.getItemType().name();
        int amt = Math.max(1, event.getItemAmount());
        plugin.getQuestEngine().addProgress(player, QuestObjectiveType.SMELT, key, amt);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) {
            plugin.getQuestEngine().addProgress(killer, QuestObjectiveType.KILL_PLAYER, "PLAYER", 1);
            return;
        }
        String key = event.getEntityType().name();
        plugin.getQuestEngine().addProgress(killer, QuestObjectiveType.KILL_MOB, key, 1);
        plugin.getCollectionManager().add(killer.getUniqueId(), "mobs", key, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        markTeleport(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        UUID uuid = event.getPlayer().getUniqueId();
        if (teleportSkip.remove(uuid)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        long x = event.getTo().getBlockX();
        long z = event.getTo().getBlockZ();
        long[] prev = lastBlock.put(uuid, new long[]{x, z});
        if (prev == null) return;
        long dx = Math.abs(x - prev[0]);
        long dz = Math.abs(z - prev[1]);
        int dist = (int) Math.hypot(dx, dz);
        if (dist <= 0 || dist > 16) return;
        plugin.getQuestEngine().addDistance(event.getPlayer(), dist);
    }

    private static boolean hasSilk(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand != null && hand.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private static boolean isRipe(Block block) {
        Material type = block.getType();
        if (block.getBlockData() instanceof CaveVinesPlant vines) {
            return vines.isBerries();
        }
        if (HARVEST_AS_IS.contains(type)) {
            return true;
        }
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }
}
