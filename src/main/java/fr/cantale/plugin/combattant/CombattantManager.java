package fr.cantale.plugin.combattant;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombattantManager implements Listener {

    private static final class SavedInventory {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        private SavedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
            this.contents = cloneArray(contents);
            this.armor = cloneArray(armor);
            this.offhand = offhand != null ? offhand.clone() : null;
        }

        private static ItemStack[] cloneArray(ItemStack[] src) {
            if (src == null) return new ItemStack[0];
            ItemStack[] dst = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) {
                dst[i] = src[i] == null ? null : src[i].clone();
            }
            return dst;
        }
    }

    private final CantalePlugin plugin;
    private final Set<UUID> activeCombattants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SavedInventory> savedInventories = new ConcurrentHashMap<>();
    private final File logsDir;

    public CombattantManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.logsDir = new File(plugin.getDataFolder(), "logs" + File.separator + "combattant");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    public boolean isCombattant(UUID uuid) {
        return uuid != null && activeCombattants.contains(uuid);
    }

    public boolean toggleCombattant(Player staff, Player target) {
        UUID uuid = target.getUniqueId();
        if (activeCombattants.contains(uuid)) {
            deactivateCombattant(target);
            logAction(target, "DÉSACTIVÉ par " + staff.getName());
            staff.sendMessage("§8[§6Cantale§8] §aMode combattant §cdésactivé §apour §e" + target.getName() + "§a.");
            return false;
        } else {
            activateCombattant(target);
            logAction(target, "ACTIVÉ par " + staff.getName());
            staff.sendMessage("§8[§6Cantale§8] §aMode combattant §activé §apour §e" + target.getName() + "§a.");
            return true;
        }
    }

    private void activateCombattant(Player player) {
        UUID uuid = player.getUniqueId();
        // Sauvegarder l'inventaire actuel
        SavedInventory saved = new SavedInventory(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand()
        );
        savedInventories.put(uuid, saved);

        // Vider l'inventaire
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Rester en mode Survie
        player.setGameMode(GameMode.SURVIVAL);

        activeCombattants.add(uuid);
        player.sendMessage("§8[§6Cantale§8] §c⚔ Mode combattant ACTIVÉ ! §7Votre inventaire est conservé et sera restitué à la désactivation. L'accès à /pc, /ec et aux TP est bloqué.");
    }

    public void deactivateCombattant(Player player) {
        UUID uuid = player.getUniqueId();
        activeCombattants.remove(uuid);

        // Vider les items du mode combattant (ne rien garder)
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Restituer l'inventaire précédent
        SavedInventory saved = savedInventories.remove(uuid);
        if (saved != null) {
            player.getInventory().setContents(saved.contents);
            player.getInventory().setArmorContents(saved.armor);
            if (saved.offhand != null) {
                player.getInventory().setItemInOffHand(saved.offhand);
            }
        }

        player.sendMessage("§8[§6Cantale§8] §a⚔ Mode combattant DÉSACTIVÉ ! §7Votre inventaire d'origine a été restitué.");
    }

    public void logAction(Player player, String message) {
        if (player == null) return;
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] "
                + player.getName() + " (" + player.getUniqueId() + ") : " + message;
        String fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log";
        File logFile = new File(logsDir, fileName);
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(line + System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("[Combattant] Impossible d'écrire le log : " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isCombattant(player.getUniqueId())) return;

        String cmd = event.getMessage().toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/pc") || cmd.startsWith("/ec")
                || cmd.startsWith("/tp") || cmd.startsWith("/tpa") || cmd.startsWith("/tpaccept") || cmd.startsWith("/tpdeny")
                || cmd.startsWith("/spawn") || cmd.startsWith("/warp") || cmd.startsWith("/fw")
                || cmd.startsWith("/home") || cmd.startsWith("/back")
                || cmd.startsWith("/f home") || cmd.startsWith("/f tp")) {
            event.setCancelled(true);
            player.sendMessage("§8[§6Cantale§8] §cAction impossible en mode combattant ! (/pc, /ec et téléportations bloqués)");
            logAction(player, "COMMANDE BLOQUÉE : " + event.getMessage());
        } else {
            logAction(player, "COMMANDE : " + event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isCombattant(player.getUniqueId())) {
            logAction(player, "DÉCONNEXION en mode combattant !");
            deactivateCombattant(player);
        }
    }
}
