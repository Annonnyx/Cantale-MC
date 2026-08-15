package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.secret.SecretTaxManager;
import org.bukkit.scheduler.BukkitRunnable;

public class SecretTaxTask extends BukkitRunnable {

    private final CantalePlugin plugin;

    public SecretTaxTask(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        SecretTaxManager manager = plugin.getSecretTaxManager();
        if (manager != null) {
            manager.chargeAll();
        }
    }
}
