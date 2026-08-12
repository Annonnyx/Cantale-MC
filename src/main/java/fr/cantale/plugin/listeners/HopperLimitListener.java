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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Anti-lag hoppers :
 * <ul>
 *   <li>Craft de {@link Material#HOPPER} et {@link Material#HOPPER_MINECART} interdit</li>
 *   <li>Pose uniquement dans un claim de sa faction, plafonnée par le pouvoir
 *       ({@code max(min, pouvoir / divisor)} — voir {@code factions.hopper-limit})</li>
 * </ul>
 * Les hoppers déjà posés ne sont pas retirés (place-only). Comptage = hoppers
 * présents dans les chunks claimés de la faction.
 */
public class HopperLimitListener implements Listener {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;

    public HopperLimitListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
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
        if (player.isOp() || player.hasPermission("cantale.admin")) return;

        Claim claim = factionManager.getClaimAt(
                event.getBlock().getWorld().getName(),
                event.getBlock().getChunk().getX(),
                event.getBlock().getChunk().getZ());

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        if (claim == null || playerFaction == null || claim.getFactionId() != playerFaction.getId()) {
            // ClaimListener refuse déjà le stockage hors claim de fac ; message cohérent si on arrive ici.
            event.setCancelled(true);
            player.sendMessage(formatMessage(
                    "&cLes hoppers ne peuvent être posés que dans les claims de ta faction."));
            return;
        }

        Faction faction = factionManager.getFaction(claim.getFactionId());
        if (faction == null) {
            event.setCancelled(true);
            return;
        }

        int max = plugin.getConfigManager().getMaxHoppersForPower(faction.getPower());
        // Le bloc n'est pas encore dans le monde au moment du check cancel — compter l'existant.
        int current = countHoppersInFactionClaims(faction);
        if (current >= max) {
            event.setCancelled(true);
            int divisor = plugin.getConfigManager().getHopperLimitDivisor();
            player.sendMessage(formatMessage(
                    "&cLimite de hoppers atteinte (&e" + current + "&c/&e" + max
                            + "&c). Formule : &emax(0, pouvoir / " + divisor
                            + "&c). Augmentez le pouvoir de faction (&e/f power&c)."));
        }
    }

    private boolean isBlockedCraftResult(Material type) {
        return type == Material.HOPPER || type == Material.HOPPER_MINECART;
    }

    /**
     * Compte les hoppers (tile entities) dans tous les chunks claimés de la faction.
     * Appelé uniquement à la pose (événement rare) — charge les chunks si besoin pour un décompte exact.
     */
    private int countHoppersInFactionClaims(Faction faction) {
        int count = 0;
        for (Claim claim : faction.getClaims()) {
            World world = Bukkit.getWorld(claim.getWorld());
            if (world == null) continue;
            Chunk chunk = world.getChunkAt(claim.getChunkX(), claim.getChunkZ());
            for (BlockState state : chunk.getTileEntities()) {
                if (state.getType() == Material.HOPPER) {
                    count++;
                }
            }
        }
        return count;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
