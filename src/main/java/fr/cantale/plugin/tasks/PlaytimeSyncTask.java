package fr.cantale.plugin.tasks;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronise le temps de jeu Minecraft → colonne {@code players.playtime}
 * (secondes) pour les joueurs en ligne.
 * <p>
 * Sans cette tâche, le site ne voyait le playtime qu'à la déconnexion
 * ({@code PlayerQuitEvent}), donc des stats périmées / à 0 tant que le
 * joueur restait connecté.
 */
public class PlaytimeSyncTask extends BukkitRunnable {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;

    public PlaytimeSyncTask(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = plugin.getPlayerDAO();
    }

    @Override
    public void run() {
        Map<UUID, Long> snapshot = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshot.put(player.getUniqueId(), playtimeSeconds(player));
        }
        if (snapshot.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                playerDAO.updatePlaytime(entry.getKey(), entry.getValue());
            }
        });
    }

    /** {@link Statistic#PLAY_ONE_MINUTE} est en ticks ; /20 → secondes. */
    public static long playtimeSeconds(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
    }
}
