package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class ActionLogger {

    private final CantalePlugin plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat;

    public ActionLogger(CantalePlugin plugin) {
        this.plugin = plugin;
        
        // Créer le dossier du plugin s'il n'existe pas
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        this.logFile = new File(plugin.getDataFolder(), "actionlog.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier de log : " + e.getMessage());
            }
        }
    }

    public void logAction(String actionType, String player, String details) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] [%s] %s: %s", timestamp, actionType, player, details);
        
        // Log dans le fichier uniquement (pas de console)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'écriture dans le fichier de log", e);
        }
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
