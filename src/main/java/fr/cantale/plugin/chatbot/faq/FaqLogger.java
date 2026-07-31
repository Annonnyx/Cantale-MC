package fr.cantale.plugin.chatbot.faq;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journalise les questions non reconnues.
 */
public class FaqLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private File logFile;
    private final Logger logger;

    private File pluginDataFolder;

    public FaqLogger(File pluginDataFolder, String unmatchedLogPath) {
        this.logger = Logger.getLogger(FaqLogger.class.getName());
        this.pluginDataFolder = pluginDataFolder;
        this.logFile = resolveLogFile(pluginDataFolder, unmatchedLogPath);
    }

    private static File resolveLogFile(File pluginDataFolder, String unmatchedLogPath) {
        if (unmatchedLogPath == null || unmatchedLogPath.isBlank()) {
            unmatchedLogPath = "unmatched-questions.log";
        }
        File file = new File(pluginDataFolder, unmatchedLogPath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    public void setLogPath(String unmatchedLogPath) {
        this.logFile = resolveLogFile(pluginDataFolder, unmatchedLogPath);
    }

    public void logUnmatched(String source, String originalMessage, String normalizedMessage) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = String.format("[%s] [%s] ORIGINAL: %s | NORMALIZED: %s", timestamp, source, originalMessage, normalizedMessage);
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            out.println(line);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Impossible d'écrire dans le log de questions non reconnues", e);
        }
    }
}
