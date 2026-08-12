package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;

public class ClearLagTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private int countdown;
    private int interval; // Intervalle configurable en secondes
    /** Items plus jeunes que ce délai (secondes) ne sont jamais clear — protège le loot de mort. */
    private int protectRecentSeconds;

    // Paliers de notification (premier warning à T-30s uniquement)
    private static final Set<Integer> ANNOUNCE_SECONDS = Set.of(30, 10, 5, 3, 2, 1);

    public ClearLagTask(CantalePlugin plugin) {
        this.plugin = plugin;
        this.interval = plugin.getConfig().getInt("clearlag.interval", 180); // 3 minutes
        this.protectRecentSeconds = plugin.getConfig().getInt("clearlag.protect-recent-seconds", 300); // 5 min
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
        // Recharger la grâce (admin peut changer la config sans restart)
        protectRecentSeconds = Math.max(60, plugin.getConfig().getInt("clearlag.protect-recent-seconds", 300));
        int protectTicks = protectRecentSeconds * 20;

        int cleared = 0;
        int protectedCount = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            // Copie : éviter toute interaction bizarre pendant remove()
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (!(entity instanceof Item item) || item.isDead()) continue;
                if (item.getTicksLived() < protectTicks) {
                    protectedCount++;
                    continue;
                }
                item.remove();
                cleared++;
            }
        }

        // Même cadence ClearLag : appliquer le plafond max-entities-per-chunk
        // (Animals exclus — stacking passif). Ne touche pas aux items.
        if (plugin.getServerOptimizer() != null) {
            plugin.getServerOptimizer().cullExcessEntitiesPerChunk();
        }

        // Annonce honnête : le compteur = entités Item réellement retirées ici
        // (pas de « 0 items supprimés » trompeur après le countdown si tout est en grâce)
        final String msg;
        if (cleared > 0) {
            msg = "&8[&6Cantale&8] &a" + cleared + " item" + (cleared > 1 ? "s" : "") + " supprimé"
                + (cleared > 1 ? "s" : "") + " !"
                + (protectedCount > 0 ? " &7(" + protectedCount + " récent" + (protectedCount > 1 ? "s" : "") + " protégé"
                    + (protectedCount > 1 ? "s" : "") + ")" : "");
        } else if (protectedCount > 0) {
            msg = "&8[&6Cantale&8] &aAucun item ancien à supprimer. &7("
                + protectedCount + " récent" + (protectedCount > 1 ? "s" : "")
                + " encore protégé" + (protectedCount > 1 ? "s" : "")
                + ", < " + protectRecentSeconds + "s)";
        } else {
            // Rien au sol : pas de spam après le countdown
            return;
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
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

    public int getProtectRecentSeconds() {
        return protectRecentSeconds;
    }
}
