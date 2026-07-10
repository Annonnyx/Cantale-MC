package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FortifiedChunkDAO;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Gère les Runes de Fortification.
 *
 * Comportement (refonte) :
 * - 1 rune = 1 chunk fortifié.
 * - Les blocks d'un chunk fortifié ne peuvent être cassés que par les membres
 *   de la faction propriétaire (et empêche la destruction par explosions).
 * - Les coffres et inventaires restent piables (pas de blocage d'interaction).
 * - Chaque chunk fortifié ajoute un coût à la taxe journalière de la faction
 *   (calculé dans TerritoryTaxManager).
 */
public class RuneFortificationListener implements Listener {

    private final CustomItemManager customItemManager;
    private final FactionManager factionManager;
    private final FortifiedChunkDAO fortifiedChunkDAO;

    public RuneFortificationListener(CantalePlugin plugin) {
        this.customItemManager = new CustomItemManager();
        this.factionManager = plugin.getFactionManager();
        this.fortifiedChunkDAO = plugin.getFortifiedChunkDAO();
    }

    /**
     * Clic-droit avec une Rune sur un block dans un claim de sa faction → fortifie le chunk.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        ItemStack item = event.getItem();
        if (item == null || !customItemManager.isCustomItem(item)) return;

        CustomItemType type = customItemManager.getCustomItemType(item);
        if (type == null || !type.isRuneFortification()) return;

        // Empêcher l'event vanilla (par ex. ouvrir un livre enchanté)
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Chunk chunk = block.getChunk();
        String world = block.getWorld().getName();

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(Component.text("Vous devez être dans une faction pour utiliser cette rune.", NamedTextColor.RED));
            return;
        }

        // Le chunk doit être un claim de la faction du joueur
        Faction claimFaction = factionManager.getFactionAtClaim(world, chunk.getX(), chunk.getZ());
        if (claimFaction == null || claimFaction.getId() != faction.getId()) {
            player.sendMessage(Component.text("Vous ne pouvez fortifier qu'un chunk claim de votre faction.", NamedTextColor.RED));
            return;
        }

        // Vérifier que le chunk n'est pas déjà fortifié
        if (fortifiedChunkDAO.isFortified(world, chunk.getX(), chunk.getZ())) {
            player.sendMessage(Component.text("Ce chunk est déjà fortifié.", NamedTextColor.RED));
            return;
        }

        if (fortifiedChunkDAO.fortifyChunk(world, chunk.getX(), chunk.getZ(), faction.getId(), player.getUniqueId())) {
            // Consommer 1 rune
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);

            player.sendMessage(Component.text("Chunk fortifié avec succès ! (" + chunk.getX() + ", " + chunk.getZ() + ")", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Aucun joueur d'une autre faction ne peut casser de blocks dans ce chunk.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("⚠ La taxe journalière augmente pour chaque chunk fortifié.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Erreur lors de la fortification du chunk.", NamedTextColor.RED));
        }
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
        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());

        if (playerFaction == null || playerFaction.getId() != factionId) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ce chunk est fortifié — vous ne pouvez pas casser de blocks ici.", NamedTextColor.RED));
        }
    }

    /** Empêche les explosions de détruire les blocks dans un chunk fortifié. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosionBlocks(event.getLocation().getWorld().getName(), event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosionBlocks(event.getBlock().getWorld().getName(), event.blockList().iterator());
    }

    private void filterExplosionBlocks(String world, Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            if (fortifiedChunkDAO.isFortified(world, b.getChunk().getX(), b.getChunk().getZ())) {
                it.remove();
            }
        }
    }
}
