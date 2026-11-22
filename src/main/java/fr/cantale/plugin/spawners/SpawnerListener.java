package fr.cantale.plugin.spawners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SpawnerListener implements Listener {

    private final CantalePlugin plugin;
    private final SpawnerManager manager;

    public SpawnerListener(CantalePlugin plugin, SpawnerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        if (!manager.isCantaleSpawnerItem(item)) {
            event.setCancelled(true);
            player.sendMessage("§8[§6Cantale§8] §cUtilise un spawner Cantale (caisses /admin).");
            return;
        }

        Block block = event.getBlockPlaced();
        if (!manager.canPlayerPlace(player, block)) {
            event.setCancelled(true);
            player.sendMessage(manager.denyPlaceMessage(player, block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceCommit(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!manager.isCantaleSpawnerItem(item)) return;

        Player player = event.getPlayer();
        SpawnerMob mob = manager.items().readMob(item);
        int tier = manager.items().readTier(item);
        if (mob == null) return;
        if (mob != mob.canonicalMob()) {
            player.sendMessage("§8[§6Cantale§8] §7Spawner à " + mob.frenchName()
                    + " converti en cube de magma.");
        }
        // Faction propriétaire du spawner : celle du joueur ; en bypass admin posé dans
        // un claim étranger, celle du claim (sinon le spawner ne spawnnerait jamais —
        // trySpawnCycle exige claim.factionId == record.factionId).
        var fac = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        int factionId = fac != null ? fac.getId() : -1;
        if (plugin.getAdminBypassManager().isBypassing(player)) {
            Block placed = event.getBlockPlaced();
            Claim claim = plugin.getFactionManager().getClaimAt(
                    placed.getWorld().getName(), placed.getChunk().getX(), placed.getChunk().getZ());
            if (claim != null) {
                factionId = claim.getFactionId();
            }
        }
        if (factionId < 0) return;
        manager.place(event.getBlockPlaced(), mob, tier, factionId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        SpawnerRecord existing = manager.getAt(block);
        if (existing == null) return;

        if (hand.getType().name().endsWith("_SPAWN_EGG")) {
            event.setCancelled(true);
            return;
        }

        if (!manager.isCantaleSpawnerItem(hand)) return;

        event.setCancelled(true);

        if (!manager.canPlayerPlace(player, block)) {
            player.sendMessage(manager.denyPlaceMessage(player, block));
            return;
        }

        SpawnerMob itemMob = manager.items().readMob(hand);
        int itemTier = manager.items().readTier(hand);
        if (itemMob == null || itemMob.canonicalMob() != existing.mob().canonicalMob()) {
            player.sendMessage("§8[§6Cantale§8] §cMême type de mob uniquement.");
            return;
        }

        int combined = existing.tier() + itemTier;
        if (combined > manager.maxTier()) {
            player.sendMessage("§8[§6Cantale§8] §cMaximum " + manager.maxTier()
                    + " (actuel x" + existing.tier() + " + item x" + itemTier + ").");
            return;
        }

        manager.updateTier(existing, combined);
        consumeOne(player, hand);
        player.sendMessage("§8[§6Cantale§8] §aSpawner à " + existing.mob().frenchName()
                + " §7→ §ex" + existing.tier());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        SpawnerRecord record = manager.getAt(block);
        if (record == null) return;

        event.setDropItems(false);
        event.setExpToDrop(0);

        Player player = event.getPlayer();
        boolean silk = hasSilkTouchPickaxe(player.getInventory().getItemInMainHand())
                && player.getGameMode() != GameMode.CREATIVE;

        manager.remove(record, silk, block.getLocation());
        if (silk) {
            player.sendMessage("§8[§6Cantale§8] §aSpawner récupéré (x" + record.tier() + ").");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::destroySpawnerNoDrop);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::destroySpawnerNoDrop);
    }

    private boolean destroySpawnerNoDrop(Block block) {
        if (block.getType() != Material.SPAWNER) return false;
        SpawnerRecord record = manager.getAt(block);
        if (record == null) return false;
        manager.remove(record, false, null);
        block.setType(Material.AIR);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getAt(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getAt(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaSpawnerSpawn(SpawnerSpawnEvent event) {
        if (manager.getAt(event.getSpawner().getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        var chunk = event.getChunk();
        manager.noteChunkLoaded(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        // Pendant ChunkLoadEvent, getBlock() peut encore renvoyer AIR — attendre 1 tick.
        Bukkit.getScheduler().runTask(plugin, () -> manager.restoreHolograms(event.getChunk()));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.restoreHolograms(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        manager.noteChunkUnloaded(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        manager.unloadHolograms(chunk);
    }

    private static boolean hasSilkTouchPickaxe(ItemStack tool) {
        if (tool == null || !tool.getType().name().endsWith("_PICKAXE")) return false;
        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return false;
        return meta.hasEnchant(Enchantment.SILK_TOUCH) || tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private static void consumeOne(Player player, ItemStack hand) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(amount - 1);
        }
    }
}
