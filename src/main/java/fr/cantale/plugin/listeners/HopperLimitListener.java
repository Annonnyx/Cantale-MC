package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-lag hoppers :
 * <ul>
 *   <li>Craft de {@link Material#HOPPER} et {@link Material#HOPPER_MINECART} interdit</li>
 *   <li>Pose : faction obligatoire + uniquement dans un claim de sa fac, plafonnée par le pouvoir
 *       ({@code max(min, pouvoir / divisor)} — voir {@code factions.hopper-limit})</li>
 *   <li>Action bar (au-dessus de la barre d'XP) tant qu'un hopper est en main</li>
 * </ul>
 * Les hoppers déjà posés ne sont pas retirés (place-only). Comptage = hoppers
 * présents dans les chunks claimés de la faction.
 */
public class HopperLimitListener implements Listener {

    private static final long ACTION_BAR_COUNT_TTL_MS = 5_000L;

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    /** Joueurs pour lesquels on a affiché l'action bar hopper (pour la clear au switch). */
    private final Set<UUID> showingHopperBar = ConcurrentHashMap.newKeySet();
    /** Compte hoppers par faction (action bar uniquement — TTL court). */
    private final Map<Integer, CachedHopperCount> hopperCountCache = new ConcurrentHashMap<>();

    public HopperLimitListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        // Rafraîchit l'action bar (~0,5 s) : pose, déplacement entre claims, changement de pouvoir.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHopperActionBars, 10L, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        if (isBlockedCraftResult(result.getType())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return;
        if (!isBlockedCraftResult(result.getType())) return;

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(formatMessage(
                    "&cLe craft de hoppers est désactivé. Achetez-les au &e/shop &c(prix élevé, plafond quotidien)."));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHopperPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) return;
        if (!plugin.getConfigManager().isHopperLimitEnabled()) return;

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("cantale.admin")
                || plugin.getAdminBypassManager().isBypassing(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> updateHopperActionBar(player));
            return;
        }

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        if (playerFaction == null) {
            event.setCancelled(true);
            player.sendMessage(formatMessage(
                    "&cTu dois être dans une faction pour poser des hoppers."));
            updateHopperActionBar(player);
            return;
        }

        Claim claim = factionManager.getClaimAt(
                event.getBlock().getWorld().getName(),
                event.getBlock().getChunk().getX(),
                event.getBlock().getChunk().getZ());

        if (claim == null || claim.getFactionId() != playerFaction.getId()) {
            event.setCancelled(true);
            player.sendMessage(formatMessage(
                    "&cLes hoppers ne peuvent être posés que dans les claims de ta faction."));
            updateHopperActionBar(player);
            return;
        }

        Faction faction = factionManager.getFaction(claim.getFactionId());
        if (faction == null) {
            event.setCancelled(true);
            return;
        }

        int max = plugin.getConfigManager().getMaxHoppersForPower(faction.getPower());
        // Le bloc n'est pas encore dans le monde au moment du check cancel — compter l'existant.
        int current = countHoppersInFactionClaims(faction, true);
        if (current >= max) {
            event.setCancelled(true);
            int divisor = plugin.getConfigManager().getHopperLimitDivisor();
            player.sendMessage(formatMessage(
                    "&cLimite de hoppers atteinte (&e" + current + "&c/&e" + max
                            + "&c). Formule : &emax(0, pouvoir / " + divisor
                            + "&c). Augmentez le pouvoir de faction (&e/f power&c)."));
            updateHopperActionBar(player);
            return;
        }

        // Après pose réussie : rafraîchir le compteur action bar.
        Bukkit.getScheduler().runTask(plugin, () -> {
            hopperCountCache.remove(faction.getId());
            updateHopperActionBar(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.HOPPER) return;
        Claim claim = factionManager.getClaimAt(
                event.getBlock().getWorld().getName(),
                event.getBlock().getChunk().getX(),
                event.getBlock().getChunk().getZ());
        if (claim != null) {
            hopperCountCache.remove(claim.getFactionId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> updateHopperActionBar(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> updateHopperActionBar(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showingHopperBar.remove(event.getPlayer().getUniqueId());
    }

    private void tickHopperActionBars() {
        if (!plugin.getConfigManager().isHopperLimitEnabled()) {
            for (UUID uuid : showingHopperBar) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendActionBar("");
                }
            }
            showingHopperBar.clear();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHopperActionBar(player);
        }
    }

    /**
     * Affiche l'action bar tant qu'un hopper est en main principale ; clear sinon.
     */
    private void updateHopperActionBar(Player player) {
        if (player == null || !player.isOnline()) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean holding = hand != null && hand.getType() == Material.HOPPER;
        UUID uuid = player.getUniqueId();

        if (!holding || !plugin.getConfigManager().isHopperLimitEnabled()) {
            if (showingHopperBar.remove(uuid)) {
                player.sendActionBar("");
            }
            return;
        }

        showingHopperBar.add(uuid);
        player.sendActionBar(buildHopperActionBar(player));
    }

    /**
     * Messages FR courts, style Cantale (sans préfixe chat — place limitée sur l'action bar).
     */
    private String buildHopperActionBar(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            return "§cHoppers: §7rejoins une faction";
        }

        int max = plugin.getConfigManager().getMaxHoppersForPower(faction.getPower());
        int current = countHoppersInFactionClaims(faction, false);
        int remaining = Math.max(0, max - current);

        Claim claim = factionManager.getClaimAt(
                player.getWorld().getName(),
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
        boolean inOwnClaim = claim != null && claim.getFactionId() == faction.getId();

        if (!inOwnClaim) {
            return "§eHoppers: §f" + current + "§7/§f" + max
                    + " §8· §cclaim de ta fac requis";
        }
        if (remaining <= 0) {
            return "§cHoppers: §f" + current + "§7/§f" + max + " §8· §climite";
        }
        return "§6Hoppers: §e" + current + "§7/§e" + max
                + " §8· §arestants: §e" + remaining;
    }

    private boolean isBlockedCraftResult(Material type) {
        return type == Material.HOPPER || type == Material.HOPPER_MINECART;
    }

    /**
     * Compte les hoppers (tile entities) dans les chunks claimés de la faction.
     *
     * @param loadChunks {@code true} à la pose (décompte exact, peut charger des chunks) ;
     *                   {@code false} pour l'action bar (chunks déjà chargés + cache 5 s)
     */
    private int countHoppersInFactionClaims(Faction faction, boolean loadChunks) {
        if (!loadChunks) {
            CachedHopperCount cached = hopperCountCache.get(faction.getId());
            long now = System.currentTimeMillis();
            if (cached != null && now < cached.expiresAtMillis) {
                return cached.count;
            }
        }

        int count = 0;
        for (Claim claim : faction.getClaims()) {
            World world = Bukkit.getWorld(claim.getWorld());
            if (world == null) continue;
            if (!loadChunks && !world.isChunkLoaded(claim.getChunkX(), claim.getChunkZ())) {
                continue;
            }
            Chunk chunk = world.getChunkAt(claim.getChunkX(), claim.getChunkZ());
            for (BlockState state : chunk.getTileEntities()) {
                if (state.getType() == Material.HOPPER) {
                    count++;
                }
            }
        }

        if (!loadChunks) {
            hopperCountCache.put(faction.getId(),
                    new CachedHopperCount(count, System.currentTimeMillis() + ACTION_BAR_COUNT_TTL_MS));
        }
        return count;
    }

    private record CachedHopperCount(int count, long expiresAtMillis) {}

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
