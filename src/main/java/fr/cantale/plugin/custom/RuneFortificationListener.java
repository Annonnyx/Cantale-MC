package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FortifiedChunkDAO;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Gère les Runes de Fortification.
 *
 * Comportement (refonte) :
 * - 1 rune = 1 chunk fortifié.
 * - Les blocks d'un chunk fortifié ne peuvent être cassés que par les membres
 *   de la faction propriétaire. Aucune explosion n'y fonctionne (TNT, cristaux,
 *   wither, lits, ancres, minecarts, etc.).
 * - Le claim du f-spawn ({@code FactionManager#isExplosionImmune}) a la même
 *   immunité explosions, sans bloquer la casse des membres.
 * - Les coffres et inventaires restent piables (pas de blocage d'interaction).
 * - Chaque chunk fortifié ajoute un coût à la taxe journalière de la faction
 *   (calculé dans TerritoryTaxManager).
 */
public class RuneFortificationListener implements Listener {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final FortifiedChunkDAO fortifiedChunkDAO;
    private final CustomItemManager customItemManager;

    public RuneFortificationListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.fortifiedChunkDAO = plugin.getFortifiedChunkDAO();
        this.customItemManager = new CustomItemManager();
    }

    /**
     * Utilisation de la rune :
     * - Clic-droit dans un claim de sa faction → fortifie le chunk (consomme 1 rune).
     * - Sneak + clic-droit dans un chunk fortifié par sa faction → retire la
     *   fortification et rend la rune.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRuneUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Ne jamais manger l'interaction avec un bloc utilisable (coffre, porte, enclume...)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isInteractableBlock(event.getClickedBlock())) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        boolean isRune = item != null && customItemManager.isCustomItem(item)
                && customItemManager.getCustomItemType(item) == CustomItemType.RUNE_FORTIFICATION;

        Chunk chunk = player.getLocation().getChunk();
        String world = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Sneak + clic (rune ou main vide) : retirer la fortification posée par sa faction.
        // La main vide est acceptée pour ne pas bloquer un joueur qui aurait
        // consommé sa dernière rune en fortifiant.
        if (player.isSneaking() && (isRune || item == null || item.getType().isAir())) {
            Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
            if (faction == null) return;
            if (fortifiedChunkDAO.getFortifyingFaction(world, chunkX, chunkZ) != faction.getId()) return;
            event.setCancelled(true);
            handleUnfortify(player, world, chunkX, chunkZ);
            return;
        }

        if (!isRune) return;
        event.setCancelled(true);
        player.sendMessage("§8[§6Cantale§8] §cLes runes de fortification ne sont plus utilisées. La protection de carte est désormais automatique.");
        return;
    }

    private boolean isInteractableBlock(Block block) {
        if (block == null) return false;
        if (block.getState() instanceof org.bukkit.block.Container) return true;
        String n = block.getType().name();
        return n.contains("DOOR") || n.contains("BUTTON") || n.contains("LEVER")
                || n.contains("TRAPDOOR") || n.contains("FENCE_GATE") || n.contains("NOTE_BLOCK")
                || n.contains("ANVIL") || n.contains("BELL") || n.contains("BED")
                || n.contains("SIGN") || n.contains("LECTERN") || n.contains("LOOM")
                || n.contains("GRINDSTONE") || n.contains("STONECUTTER") || n.contains("SMITHING")
                || n.contains("CARTOGRAPHY") || n.contains("FLETCHING") || n.contains("COMPOSTER")
                || n.contains("BREWING") || n.contains("ENCHANTING") || n.contains("BEACON")
                || n.contains("CRAFTING") || n.contains("CAKE") || n.contains("CAULDRON")
                || n.contains("DAYLIGHT") || n.contains("REPEATER") || n.contains("COMPARATOR")
                || n.contains("FLOWER_POT") || n.contains("JUKEBOX") || n.contains("END_PORTAL_FRAME")
                || n.contains("DRAGON_EGG") || n.contains("RESPAWN_ANCHOR") || n.contains("CANDLE");
    }

    private void handleFortify(Player player, Faction faction, ItemStack item, String world, int chunkX, int chunkZ) {
        Claim claim = factionManager.getClaimAt(world, chunkX, chunkZ);
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage("§8[§6Cantale§8] §cCe chunk n'est pas claim par votre faction. §7(/f claim)");
            return;
        }
        if (claim.isPasdic()) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de fortifier un chunk PASDIC.");
            return;
        }
        if (fortifiedChunkDAO.isFortified(world, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Cantale§8] §cCe chunk est déjà fortifié.");
            return;
        }

        if (!fortifiedChunkDAO.fortifyChunk(world, chunkX, chunkZ, faction.getId(), player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cErreur lors de la fortification. Contactez un admin.");
            return;
        }

        item.setAmount(item.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.sendMessage("§8[§6Cantale§8] §aChunk fortifié ! §7Seuls les membres de §e" + faction.getName()
                + " §7peuvent y casser des blocks.");
        player.sendMessage("§8[§6Cantale§8] §7Coût : §e50 Cantox/jour §7— sneak + clic-droit pour retirer.");
    }

    private void handleUnfortify(Player player, String world, int chunkX, int chunkZ) {
        if (!fortifiedChunkDAO.unfortifyChunk(world, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Cantale§8] §cErreur lors du retrait. Contactez un admin.");
            return;
        }

        // Rendre la rune consommée lors de la fortification
        ItemStack rune = customItemManager.createCustomItem(CustomItemType.RUNE_FORTIFICATION);
        if (!player.getInventory().addItem(rune).isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rune);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.sendMessage("§8[§6Cantale§8] §aFortification retirée. §7La rune vous a été rendue.");
    }

    /**
     * Cancel le break si le chunk est fortifié et que le joueur n'est pas membre
     * de la faction propriétaire.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();
        int factionId = fortifiedChunkDAO.getFortifyingFaction(block.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (factionId <= 0) return;

        Player player = event.getPlayer();
        if (player.hasPermission("cantale.admin")) return;
        // Bypass admin (/f bypass) : la casse est aussi permise en chunk fortifié.
        if (plugin.getAdminBypassManager().isBypassing(player)) return;

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());

        if (playerFaction == null || playerFaction.getId() != factionId) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ce chunk est fortifié — vous ne pouvez pas casser de blocks ici.", NamedTextColor.RED));
        }
    }

    /**
     * Sous une rune ou dans le claim du f-spawn : aucune explosion (TNT, cristaux,
     * wither, lits, ancres, minecarts TNT, creepers, fireballs, wind charges…).
     * Origine dans le chunk → explosion entièrement annulée. Origine à côté →
     * blocs immunisés retirés de la liste, dégâts d'explosion aux entités du
     * chunk annulés à part.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location origin = event.getLocation();
        if (isExplosionImmune(origin)) {
            event.setCancelled(true);
            event.blockList().clear();
            return;
        }
        if (origin.getWorld() == null) return;
        filterExplosionBlocks(origin.getWorld().getName(), event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (isExplosionImmune(block.getLocation())) {
            event.setCancelled(true);
            event.blockList().clear();
            return;
        }
        filterExplosionBlocks(block.getWorld().getName(), event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (isExplosionImmune(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (isExplosionImmune(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        if (isExplosionImmune(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWitherChangeBlock(EntityChangeBlockEvent event) {
        EntityType type = event.getEntityType();
        if (type != EntityType.WITHER && type != EntityType.WITHER_SKULL && type != EntityType.ENDER_DRAGON) {
            return;
        }
        if (isExplosionImmune(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) return;
        if (isExplosionImmune(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.EXPLOSION) return;
        if (isExplosionImmune(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private void filterExplosionBlocks(String world, Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            if (factionManager.isExplosionImmune(world, b.getChunk().getX(), b.getChunk().getZ())) {
                it.remove();
            }
        }
    }

    /** Rune active sur le chunk, ou claim du f-spawn. Casse membres inchangée. */
    private boolean isExplosionImmune(Location loc) {
        return factionManager.isExplosionImmune(loc);
    }
}
