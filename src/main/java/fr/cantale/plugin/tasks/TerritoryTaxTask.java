package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.TerritoryTaxManager;
import org.bukkit.scheduler.BukkitRunnable;

public class TerritoryTaxTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private final TerritoryTaxManager taxManager;

    public TerritoryTaxTask(CantalePlugin plugin) {
        this.plugin = plugin;
        this.taxManager = new TerritoryTaxManager(plugin);
    }

    @Override
    public void run() {
        plugin.getLogger().info("Collecte des taxes de territoire...");
        taxManager.collectTaxes();
        plugin.getLogger().info("Collecte des taxes terminée.");
    }
}
