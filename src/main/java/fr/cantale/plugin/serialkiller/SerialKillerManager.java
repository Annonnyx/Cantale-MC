package fr.cantale.plugin.serialkiller;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.wanted.WantedManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SerialKillerManager {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;
    private final WantedManager wantedManager;
    private final Set<UUID> serialKillers;
    
    private static final int KILL_STREAK_THRESHOLD = 10;
    private static final double SERIAL_KILLER_BOUNTY = 500.0;

    public SerialKillerManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
        this.wantedManager = new WantedManager(plugin);
        this.serialKillers = new HashSet<>();
    }

    public void checkKillStreak(UUID killerUuid, String killerName) {
        int killStreak = playerDAO.getKillStreak(killerUuid);
        
        if (killStreak >= KILL_STREAK_THRESHOLD && !serialKillers.contains(killerUuid)) {
            // Le joueur devient Serial Killer
            serialKillers.add(killerUuid);
            
            // Ajouter une prime automatique
            wantedManager.addWanted(killerUuid, killerName, SERIAL_KILLER_BOUNTY, "Serial Killer (" + killStreak + " kills sans morts)", null);
            
            // Notifier le serveur
            Bukkit.broadcastMessage("§c[Serial Killer] §e" + killerName + " §cest devenu un Serial Killer !");
            Bukkit.broadcastMessage("§c[Serial Killer] §7Prime de §e" + SERIAL_KILLER_BOUNTY + " Cantox §7sur sa tête !");
            
            plugin.getLogger().info(killerName + " est devenu un Serial Killer (kill streak: " + killStreak + ")");
        }
    }

    public void onPlayerDeath(UUID victimUuid, String victimName, UUID killerUuid) {
        if (serialKillers.contains(victimUuid)) {
            // Le Serial Killer est mort
            serialKillers.remove(victimUuid);
            
            // Récompenser le killer
            if (killerUuid != null) {
                Player killer = Bukkit.getPlayer(killerUuid);
                if (killer != null && killer.isOnline()) {
                    plugin.getEconomyManager().deposit(killer, SERIAL_KILLER_BOUNTY);
                    killer.sendMessage("§a[Serial Killer] §7Vous avez tué le Serial Killer §e" + victimName + " §7et gagné §e" + SERIAL_KILLER_BOUNTY + " Cantox§7 !");
                }
            }
            
            // Notifier le serveur
            Bukkit.broadcastMessage("§a[Serial Killer] §e" + victimName + " §7(le Serial Killer) a été tué !");
            
            plugin.getLogger().info(victimName + " (Serial Killer) a été tué par " + killerUuid);
        }
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        int killStreak = playerDAO.getKillStreak(uuid);
        
        if (killStreak >= KILL_STREAK_THRESHOLD) {
            serialKillers.add(uuid);
            Bukkit.broadcastMessage("§c[Serial Killer] §e" + player.getName() + " §cest un Serial Killer en fuite !");
            Bukkit.broadcastMessage("§c[Serial Killer] §7Prime de §e" + SERIAL_KILLER_BOUNTY + " Cantox §7sur sa tête !");
        }
    }

    public boolean isSerialKiller(UUID uuid) {
        return serialKillers.contains(uuid);
    }

    public int getKillStreakThreshold() {
        return KILL_STREAK_THRESHOLD;
    }

    public double getSerialKillerBounty() {
        return SERIAL_KILLER_BOUNTY;
    }
}
