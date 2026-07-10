package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private final CantalePlugin plugin;
    private final HomeDAO homeDAO;
    private final Map<UUID, Map<String, Home>> playerHomes;

    public HomeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.homeDAO = new HomeDAO(plugin);
        this.playerHomes = new ConcurrentHashMap<>();
    }

    private int getMaxHomes(Player player) {
        PlayerRank rank = plugin.getRankManager().getRank(player);
        return rank.getMaxHomes();
    }

    private int getMaxHomes(UUID uuid) {
        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        return rank.getMaxHomes();
    }

    public boolean setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());

        int maxHomes = getMaxHomes(player);
        if (homes.size() >= maxHomes && !homes.containsKey(name)) {
            player.sendMessage("§8[§6Cantale§8] §cVous avez atteint la limite de " + maxHomes + " homes.");
            return false;
        }

        Home home = new Home(uuid, name, player.getLocation());
        int id = homeDAO.createHome(home);
        if (id > 0) {
            home.setId(id);
            homes.put(name, home);
            player.sendMessage("§8[§6Cantale§8] §aHome §e" + name + " §adéfini !");
            plugin.getLogger().info("Home créé: " + name + " pour " + player.getName() + " (UUID: " + uuid + ")");
            return true;
        }

        player.sendMessage("§8[§6Cantale§8] §cErreur lors de la création du home.");
        return false;
    }

    public boolean deleteHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = playerHomes.get(uuid);

        if (homes == null || !homes.containsKey(name)) {
            player.sendMessage("§8[§6Cantale§8] §cHome §e" + name + " §cinexistant.");
            return false;
        }

        homeDAO.deleteHome(homes.get(name).getId());
        homes.remove(name);
        player.sendMessage("§8[§6Cantale§8] §aHome §e" + name + " §asupprimé.");
        return true;
    }

    public boolean teleportHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = playerHomes.get(uuid);

        plugin.getLogger().info("Tentative TP home: " + name + " pour " + player.getName() + " (UUID: " + uuid + ")");
        plugin.getLogger().info("Homes map: " + (homes != null ? homes.size() : "null") + " homes");

        if (homes == null || !homes.containsKey(name)) {
            player.sendMessage("§8[§6Cantale§8] §cHome §e" + name + " §cinexistant.");
            plugin.getLogger().warning("Home non trouvé: " + name);
            return false;
        }

        Location loc = homes.get(name).getLocation();
        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(loc);
            player.sendMessage("§8[§6Cantale§8] §aTéléporté au home §e" + name + "§a.");
            plugin.getCooldownManager().setCooldown(player, "home");
            plugin.getLogger().info("TP home réussi: " + name);
        });
        return true;
    }

    public List<Home> getPlayerHomes(UUID uuid) {
        Map<String, Home> homes = playerHomes.get(uuid);
        return homes != null ? List.copyOf(homes.values()) : List.of();
    }

    public void listHomes(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = playerHomes.get(uuid);

        // Charger les homes si pas encore chargés
        if (homes == null) {
            loadPlayerHomes(uuid);
            homes = playerHomes.get(uuid);
        }

        if (homes == null || homes.isEmpty()) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez aucun home.");
            return;
        }

        int maxHomes = getMaxHomes(player);
        player.sendMessage("§8[§6Cantale§8] §7=== Vos Homes (" + homes.size() + "/" + maxHomes + ") ===");
        for (Home home : homes.values()) {
            Location loc = home.getLocation();
            player.sendMessage("§7  - §e" + home.getName() + " §7(" + loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
        }
    }

    public void loadPlayerHomes(UUID uuid) {
        List<Home> homes = homeDAO.getPlayerHomes(uuid);
        Map<String, Home> homeMap = new HashMap<>();
        for (Home home : homes) {
            homeMap.put(home.getName(), home);
        }
        playerHomes.put(uuid, homeMap);
    }

    public void loadAllHomes() {
        // Chargement à la demande pour éviter de tout charger au démarrage
    }
}
