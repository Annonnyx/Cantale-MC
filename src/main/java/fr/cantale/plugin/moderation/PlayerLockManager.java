package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerLockManager implements Listener {

    private final CantalePlugin plugin;
    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();
    private final File dataFile;

    public PlayerLockManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "locked_players.yml");
        loadData();
    }

    public boolean isLocked(UUID uuid) {
        return uuid != null && lockedPlayers.contains(uuid);
    }

    public boolean toggleLock(Player staff, Player target) {
        UUID uuid = target.getUniqueId();
        if (lockedPlayers.contains(uuid)) {
            lockedPlayers.remove(uuid);
            saveData();
            target.sendMessage("§8[§6Cantale§8] §aVous avez été déverrouillé par la modération.");
            staff.sendMessage("§8[§6Cantale§8] §aJoueur §e" + target.getName() + " §adéverrouillé.");
            return false;
        } else {
            lockedPlayers.add(uuid);
            saveData();
            target.sendMessage("§8[§6Cantale§8] §cVous avez été VERROUILLÉ par la modération. Vous ne pouvez plus bouger, vous téléporter ni exécuter de commandes.");
            staff.sendMessage("§8[§6Cantale§8] §cJoueur §e" + target.getName() + " §cVERROUILLÉ.");
            return true;
        }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        List<String> list = yaml.getStringList("locked");
        for (String s : list) {
            try {
                lockedPlayers.add(UUID.fromString(s));
            } catch (Exception ignored) {}
        }
    }

    private void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID uuid : lockedPlayers) {
            list.add(uuid.toString());
        }
        yaml.set("locked", list);
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Lock] Error saving locked_players.yml: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§8[§6Cantale§8] §cVous êtes verrouillé par la modération (téléportation impossible).");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isLocked(event.getPlayer().getUniqueId())) return;
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (msg.startsWith("/lock") || msg.startsWith("/moderation")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§8[§6Cantale§8] §cVous êtes verrouillé par la modération (commandes bloquées).");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isLocked(player.getUniqueId())) {
            String alert = "§8[§6Cantale§8] §c[Staff Alert] Le joueur verrouillé §e" + player.getName() + " §ca DÉCONNECTÉ !";
            plugin.getLogger().warning("[Lock Alert] Player " + player.getName() + " quit while locked!");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("cantale.moderator") || staff.hasPermission("cantale.admin")) {
                    staff.sendMessage(alert);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isLocked(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §c[VERROUILLÉ] Vous êtes toujours verrouillé par la modération.");
            String alert = "§8[§6Cantale§8] §e[Staff Alert] Le joueur verrouillé §c" + player.getName() + " §es'est RECONNECTÉ !";
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("cantale.moderator") || staff.hasPermission("cantale.admin")) {
                    staff.sendMessage(alert);
                }
            }
        }
    }
}
