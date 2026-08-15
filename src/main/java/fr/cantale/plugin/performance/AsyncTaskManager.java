package fr.cantale.plugin.performance;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pool partagé pour les tâches lourdes hors thread principal
 * (analyse de hotspots, scans préparatoires du governor, etc.).
 * Ne JAMAIS toucher à l'API Bukkit depuis ces threads : re-synchroniser via
 * {@link #runAsyncThenSync} ou le scheduler.
 */
public class AsyncTaskManager {

    private final CantalePlugin plugin;
    private final BukkitScheduler scheduler;
    private final ExecutorService executorService;

    public AsyncTaskManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();
        this.executorService = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "Cantale-Async");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Exécute une tâche de manière asynchrone
     * @param task La tâche à exécuter
     */
    public void runAsync(Runnable task) {
        executorService.submit(task);
    }

    /**
     * Exécute une tâche de manière asynchrone puis une tâche sur le main thread après
     * @param asyncTask Tâche asynchrone
     * @param syncAfterTask Tâche à exécuter sur le main thread après
     */
    public void runAsyncThenSync(Runnable asyncTask, Runnable syncAfterTask) {
        executorService.submit(() -> {
            asyncTask.run();
            scheduler.runTask(plugin, syncAfterTask);
        });
    }

    /**
     * Exécute une tâche sur le main thread après un délai
     * @param task Tâche à exécuter
     * @param delayTicks Délai en ticks
     */
    public void runDelayed(Runnable task, long delayTicks) {
        scheduler.runTaskLater(plugin, task, delayTicks);
    }

    /**
     * Exécute une tâche périodique
     * @param task Tâche à exécuter
     * @param delayTicks Délai initial en ticks
     * @param periodTicks Période en ticks
     */
    public void runPeriodic(Runnable task, long delayTicks, long periodTicks) {
        scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    /**
     * Ferme l'executor service lors de la désactivation du plugin
     * (attend brièvement les tâches en cours, puis force).
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
