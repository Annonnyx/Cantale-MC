package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class ActionLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long FLUSH_PERIOD_TICKS = 20L;

    private final CantalePlugin plugin;
    private final File logFile;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final Object flushLock = new Object();
    private final BukkitTask flushTask;

    public ActionLogger(CantalePlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.logFile = new File(plugin.getDataFolder(), "actionlog.txt");

        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier de log : " + e.getMessage());
            }
        }

        this.flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::flush, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    public void logAction(String actionType, String player, String details) {
        String timestamp = DATE_FORMAT.format(LocalDateTime.now());
        String logEntry = String.format("[%s] [%s] %s: %s", timestamp, actionType, player, details);
        queue.offer(logEntry);
    }

    /** Écrit la file sur disque. Appelé périodiquement et au shutdown. */
    public void flush() {
        if (queue.isEmpty()) return;
        synchronized (flushLock) {
            if (queue.isEmpty()) return;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                String entry;
                while ((entry = queue.poll()) != null) {
                    writer.write(entry);
                    writer.newLine();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'écriture dans le fichier de log", e);
            }
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flush();
    }

    public void logBlockBreak(String player, String blockType, String location) {
        logAction("BLOCK_BREAK", player, "a cassé " + blockType + " à " + location);
    }

    public void logBlockPlace(String player, String blockType, String location) {
        logAction("BLOCK_PLACE", player, "a posé " + blockType + " à " + location);
    }

    public void logChatMessage(String player, String message) {
        logAction("CHAT", player, "a dit: " + message);
    }

    public void logCommand(String player, String command) {
        logAction("COMMAND", player, "a exécuté: " + command);
    }

    public void logDeath(String player, String cause) {
        logAction("DEATH", player, "est mort (cause: " + cause + ")");
    }

    public void logJoin(String player) {
        logAction("JOIN", player, "a rejoint le serveur");
    }

    public void logQuit(String player) {
        logAction("QUIT", player, "a quitté le serveur");
    }
}
