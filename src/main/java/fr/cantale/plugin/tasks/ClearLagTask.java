package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;

public class ClearLagTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private int countdown;
    private int interval; // Intervalle configurable en secondes

    // Paliers de notification (premier warning à T-30s uniquement)
    private static final Set<Integer> ANNOUNCE_SECONDS = Set.of(30, 10, 5, 3, 2, 1);

    public ClearLagTask(CantalePlugin plugin) {
        this.plugin = plugin;
        this.interval = plugin.getConfig().getInt("clearlag.interval", 180); // 3 minutes
        this.countdown = interval;
    }

    @Override
    public void run() {
        countdown--;

        if (countdown <= 0) {
            clearItems();
            countdown = interval;
        } else if (ANNOUNCE_SECONDS.contains(countdown)) {
            String color = countdown <= 5 ? "&c" : "&e";
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Cantale&8] " + color + "Clear-lag dans " + countdown + " seconde" + (countdown > 1 ? "s" : "") + " !"));
        }
    }

    private void clearItems() {
        int cleared = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    cleared++;
                }
            }
        }

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&8[&6Cantale&8] &a" + cleared + " items supprimés !"));
    }

    /**
     * Met à jour l'intervalle du ClearLag.
     * Le nouveau countdown est recalculé proportionnellement.
     */
    public void setInterval(int seconds) {
        this.interval = seconds;
        // Réinitialiser le countdown au nouvel intervalle
        this.countdown = seconds;
        // Sauvegarder dans la config
        plugin.getConfig().set("clearlag.interval", seconds);
        plugin.saveConfig();
    }

    public int getInterval() {
        return interval;
    }

    public int getCountdown() {
        return countdown;
    }
}
