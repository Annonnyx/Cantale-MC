package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.listeners.AfkListener;
import org.bukkit.scheduler.BukkitRunnable;

public class AfkCheckTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private final AfkListener afkListener;

    public AfkCheckTask(CantalePlugin plugin, AfkListener afkListener) {
        this.plugin = plugin;
        this.afkListener = afkListener;
    }

    @Override
    public void run() {
        afkListener.checkAfkPlayers();
    }
}
