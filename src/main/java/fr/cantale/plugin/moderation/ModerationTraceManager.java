package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ModerationTraceManager {

    private final CantalePlugin plugin;
    private final Map<UUID, TraceSession> activeSessions;
    private final File logsDir;

    public ModerationTraceManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
        this.logsDir = new File(plugin.getDataFolder(), "logs" + File.separator + "moderation");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    public boolean activateTrace(Player player) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cVous êtes déjà en mode trace.");
            return false;
        }

        // Sauvegarder l'inventaire en DB
        ItemStack[] inventoryContents = player.getInventory().getContents();
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        GameMode originalGameMode = player.getGameMode();
        Location originalLocation = player.getLocation();

        TraceSession session = new TraceSession(
            player.getUniqueId(),
            player.getName(),
            inventoryContents,
            armorContents,
            originalGameMode,
            originalLocation,
            new ArrayList<>()
        );

        activeSessions.put(player.getUniqueId(), session);

        // Vider l'inventaire et mettre en créatif
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.CREATIVE);

        player.sendMessage("§aMode trace activé. Toutes vos actions sont loguées.");
        return true;
    }

    public java.io.File deactivateTrace(Player player) {
        TraceSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cVous n'êtes pas en mode trace.");
            return null;
        }

        // Restaurer l'inventaire
        player.getInventory().setContents(session.inventoryContents);
        player.getInventory().setArmorContents(session.armorContents);

        // Restaurer le gamemode
        player.setGameMode(session.originalGameMode);

        // Téléporter à la position originale
        player.teleport(session.originalLocation);

        // Générer le rapport
        java.io.File report = generateReport(session);

        player.sendMessage("§aMode trace désactivé. Rapport généré.");
        return report;
    }

    public boolean isInTrace(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    public void logAction(UUID uuid, String action) {
        TraceSession session = activeSessions.get(uuid);
        if (session != null) {
            session.actions.add("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + action);
        }
    }

    public void logInventory(UUID uuid, ItemStack[] contents) {
        TraceSession session = activeSessions.get(uuid);
        if (session != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("] INVENTORY: ");
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    sb.append("[").append(i).append("]=").append(contents[i].getType().name()).append("x").append(contents[i].getAmount()).append(" ");
                }
            }
            session.actions.add(sb.toString());
        }
    }

    public void logPosition(UUID uuid, Location location) {
        TraceSession session = activeSessions.get(uuid);
        if (session != null) {
            session.actions.add("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] POSITION: " +
                location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
    }

    private File generateReport(TraceSession session) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File reportFile = new File(logsDir, session.playerName + "_" + timestamp + ".txt");

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("=== RAPPORT DE MODÉRATION ===\n");
            writer.write("Modérateur: " + session.playerName + "\n");
            writer.write("UUID: " + session.uuid + "\n");
            writer.write("Début: " + session.startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("Fin: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("Position de départ: " + session.originalLocation.getWorld().getName() + " " +
                session.originalLocation.getBlockX() + "," + session.originalLocation.getBlockY() + "," + session.originalLocation.getBlockZ() + "\n");
            writer.write("Gamemode original: " + session.originalGameMode.name() + "\n");
            writer.write("\n=== ACTIONS LOGUÉES ===\n");
            for (String action : session.actions) {
                writer.write(action + "\n");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la génération du rapport: " + e.getMessage());
            return null;
        }
        return reportFile;
    }

    private static class TraceSession {
        final UUID uuid;
        final String playerName;
        final ItemStack[] inventoryContents;
        final ItemStack[] armorContents;
        final GameMode originalGameMode;
        final Location originalLocation;
        final List<String> actions;
        final LocalDateTime startTime;

        TraceSession(UUID uuid, String playerName, ItemStack[] inventoryContents, ItemStack[] armorContents,
                    GameMode originalGameMode, Location originalLocation, List<String> actions) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.inventoryContents = inventoryContents;
            this.armorContents = armorContents;
            this.originalGameMode = originalGameMode;
            this.originalLocation = originalLocation;
            this.actions = actions;
            this.startTime = LocalDateTime.now();
        }
    }
}
