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

    private final FactionManager factionManager;
    private final FortifiedChunkDAO fortifiedChunkDAO;

    public RuneFortificationListener(CantalePlugin plugin) {
        this.factionManager = plugin.getFactionManager();
        this.fortifiedChunkDAO = plugin.getFortifiedChunkDAO();
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
