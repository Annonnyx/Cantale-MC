package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handlers d'interaction distincts par {@link CrateInteraction}.
 * Emplacements lus depuis {@link CrateConfig} (crate.yml).
 */
public final class CrateInteractionHandler {

    private static final int WATER_CHECK_TICKS = 60;
    private static final int WATER_CHECK_PERIOD = 5;

    private final CantalePlugin plugin;
    private final CrateManager crateManager;
    private final Map<UUID, BukkitTask> pendingWaterChecks = new HashMap<>();

    public CrateInteractionHandler(CantalePlugin plugin, CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        CrateConfig config = crateManager.getCrateConfig();
        Block clicked = event.getClickedBlock();

        // Coffre Trésor Public configuré : jamais d'ouverture vanilla
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && config.isConfiguredTreasureChest(clicked)) {
            event.setCancelled(true);
            CrateType heldType = (item != null && !item.getType().isAir())
                    ? crateManager.getCrateType(item) : null;
            if (heldType == CrateType.RARE) {
                handleRareChest(event, player, item, heldType);
            } else {
                player.sendMessage("§8[§6Cantale§8] §cIl te faut une §9clef du Trésor Public §cpour ouvrir ce coffre.");
            }
            return;
        }

        if (item == null || item.getType().isAir()) {
            return;
        }

        CrateType type = crateManager.getCrateType(item);
        if (type == null) {
            return;
        }

        switch (type.getInteraction()) {
            case MAILBOX -> handleMailbox(event, player, item, type);
            case RIGHT_CLICK_ANIMATED -> handleRareChest(event, player, item, type);
            case THROW_WATER -> handleThrowWaterWrongInteract(event, player, type);
            case RITUAL_ALTAR -> handleRitualAltar(event, player, item, type);
            case RIGHT_CLICK_TICKET -> handleLegendaryTicket(event, player, item, type);
        }
    }

    public void handleDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item dropped = event.getItemDrop();
        ItemStack stack = dropped.getItemStack();

        CrateType type = crateManager.getCrateType(stack);
        if (type == null) {
            return;
        }

        if (type.getInteraction() != CrateInteraction.THROW_WATER) {
            return;
        }

        handleWaterThrow(event, player, dropped, type);
    }

    private void handleMailbox(PlayerInteractEvent event, Player player, ItemStack item, CrateType type) {
        CrateConfig config = crateManager.getCrateConfig();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !config.isValidMailbox(event.getClickedBlock())) {
            event.setCancelled(true);
            if (config.hasMailboxLocations()) {
                player.sendMessage("§8[§6Cantale§8] §cDépose le §fCadeau du Roi §cdans la boîte aux lettres au spawn !");
            } else {
                crateManager.sendUsageHint(player, type);
            }
            return;
        }

        event.setCancelled(true);
        if (!tryBegin(player)) {
            return;
        }

        consumeOne(player, item);
        player.sendMessage("§8[§6Cantale§8] §7Tu glisses le §fCadeau du Roi §7dans la boîte aux lettres...");
        crateManager.openMailbox(player, type);
    }

    private void handleRareChest(PlayerInteractEvent event, Player player, ItemStack item, CrateType type) {
        CrateConfig config = crateManager.getCrateConfig();
        if (config.hasChestLocations()) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                    || !config.isValidTreasureChest(event.getClickedBlock())) {
                event.setCancelled(true);
                player.sendMessage("§8[§6Cantale§8] §cOuvre le coffre du §9Trésor Public §cau spawn !");
                return;
            }
        }

        event.setCancelled(true);
        if (!tryBegin(player)) {
            return;
        }

        consumeOne(player, item);
        crateManager.openWithChestUI(player, type);
    }

    private void handleThrowWaterWrongInteract(PlayerInteractEvent event, Player player, CrateType type) {
        event.setCancelled(true);
        crateManager.sendUsageHint(player, type);
    }

    private void handleWaterThrow(PlayerDropItemEvent event, Player player, Item dropped, CrateType type) {
        if (dropped.getItemStack().getAmount() > 1) {
            event.setCancelled(true);
            player.sendMessage("§8[§6Cantale§8] §cJette tes pièces une par une dans l'eau.");
            return;
        }

        if (crateManager.isOpening(player)) {
            event.setCancelled(true);
            player.sendMessage("§8[§6Cantale§8] §cTu ouvres déjà une caisse...");
            return;
        }

        UUID playerId = player.getUniqueId();
        BukkitTask previous = pendingWaterChecks.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }

        final int[] ticks = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += WATER_CHECK_PERIOD;

            if (!player.isOnline() || dropped.isDead() || !dropped.isValid()) {
                cancelWaterCheck(playerId);
                return;
            }

            if (isValidMythicWaterDrop(dropped)) {
                cancelWaterCheck(playerId);
                Location openAt = dropped.getLocation().clone();
                dropped.remove();

                if (crateManager.isOpening(player)) {
                    return;
                }
                crateManager.openWaterOffer(player, type, openAt);
                return;
            }

            if (ticks[0] >= WATER_CHECK_TICKS) {
                cancelWaterCheck(playerId);
                CrateConfig config = crateManager.getCrateConfig();
                if (config.hasFountainRestriction() && isInWaterFountain(dropped)) {
                    player.sendMessage("§8[§6Cantale§8] §cJette la §dPièce Mythique §cdans la fontaine au spawn !");
                }
            }
        }, WATER_CHECK_PERIOD, WATER_CHECK_PERIOD);

        pendingWaterChecks.put(playerId, task);
    }

    private void handleRitualAltar(PlayerInteractEvent event, Player player, ItemStack item, CrateType type) {
        CrateConfig config = crateManager.getCrateConfig();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !config.isValidAltar(event.getClickedBlock())) {
            event.setCancelled(true);
            if (config.hasAltarLocations()) {
                player.sendMessage("§8[§6Cantale§8] §cConsacre la §5Médaille du Tournoi §csur l'autel au spawn !");
            } else {
                crateManager.sendUsageHint(player, type);
            }
            return;
        }

        event.setCancelled(true);
        if (!tryBegin(player)) {
            return;
        }

        Block altar = event.getClickedBlock();
        Location effectLoc = altar.getLocation().add(0.5, 0.5, 0.5);
        consumeOne(player, item);
        crateManager.openRitual(player, type, effectLoc);
    }

    private void handleLegendaryTicket(PlayerInteractEvent event, Player player, ItemStack item, CrateType type) {
        event.setCancelled(true);
        if (!tryBegin(player)) {
            return;
        }

        consumeOne(player, item);
        crateManager.openTicket(player, type);
    }

    private boolean tryBegin(Player player) {
        if (crateManager.isOpening(player)) {
            player.sendMessage("§8[§6Cantale§8] §cTu ouvres déjà une caisse...");
            return false;
        }
        return true;
    }

    private void cancelWaterCheck(UUID playerId) {
        BukkitTask task = pendingWaterChecks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * MYTHIC : dans l'eau ET (si configuré) dans une zone / rayon fontaine.
     */
    private boolean isValidMythicWaterDrop(Item item) {
        if (!isInWaterFountain(item)) {
            return false;
        }
        CrateConfig config = crateManager.getCrateConfig();
        Location loc = item.getLocation();
        if (config.isValidFountainLocation(loc)) {
            return true;
        }
        // Chaudron d'eau sous l'item : tester aussi le bloc du chaudron
        Block below = loc.getBlock().getRelative(0, -1, 0);
        if (below.getType() == Material.WATER_CAULDRON) {
            return config.isValidFountainLocation(below.getLocation());
        }
        return false;
    }

    /** MYTHIC — eau / fontaine uniquement (pas d'autel). */
    static boolean isInWaterFountain(Item item) {
        if (item.isInWater()) {
            return true;
        }

        Block block = item.getLocation().getBlock();
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.WATER_CAULDRON) {
            return true;
        }

        Block below = block.getRelative(0, -1, 0);
        Material belowType = below.getType();
        return belowType == Material.WATER_CAULDRON
                || belowType == Material.WATER
                || belowType == Material.BUBBLE_COLUMN;
    }

    private static void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
