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
        if (rank == PlayerRank.ADMIN || rank == PlayerRank.OWNER) {
            return Integer.MAX_VALUE;
        }
        return rank.getMaxHomes();
    }

    private int getMaxHomes(UUID uuid) {
        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        if (rank == PlayerRank.ADMIN || rank == PlayerRank.OWNER) {
            return Integer.MAX_VALUE;
        }
        return rank.getMaxHomes();
    }

    public boolean setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());

        int maxHomes = getMaxHomes(player);
        if (homes.size() >= maxHomes && !homes.containsKey(name)) {
            player.sendMessage("§8[§6Cantale§8] §cVous avez atteint la limite de " + (maxHomes == Integer.MAX_VALUE ? "∞" : maxHomes) + " homes.");
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

        int delay = plugin.getTeleportDelayManager().getTeleportDelay(player);
        if (loc != null && loc.getWorld() != null) {
            plugin.getChunkLoader().preloadChunksAround(loc, 1);
            if (delay > 0) {
                long ticksBefore = Math.max(0, (delay - 1) * 20L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (loc.getWorld() != null) {
                        loc.getWorld().getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                    }
                }, ticksBefore);
            } else {
                loc.getWorld().getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
        }

        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(loc);
            player.sendMessage("§8[§6Cantale§8] §aTéléporté au home §e" + name + "§a.");
            plugin.getCooldownManager().setCooldown(player, "home");
            plugin.getLogger().info("TP home réussi: " + name);
        });
        return true;
    }

    public List<Home> getPlayerHomes(UUID uuid) {
        return List.copyOf(ensureHomesLoaded(uuid).values());
    }

    /**
     * Charge depuis la DB si besoin, puis retourne la map des homes (jamais null).
     */
    public Map<String, Home> ensureHomesLoaded(UUID uuid) {
        Map<String, Home> homes = playerHomes.get(uuid);
        if (homes == null) {
            loadPlayerHomes(uuid);
            homes = playerHomes.get(uuid);
        }
        return homes != null ? homes : Map.of();
    }

    /**
     * Recherche un home par nom (insensible à la casse).
     */
    public Home findHome(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return null;
        Map<String, Home> homes = ensureHomesLoaded(uuid);
        Home exact = homes.get(name);
        if (exact != null) return exact;
        for (Map.Entry<String, Home> entry : homes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void listHomes(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = ensureHomesLoaded(uuid);

        if (homes.isEmpty()) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez aucun home.");
            return;
        }

        int maxHomes = getMaxHomes(player);
        String maxLabel = maxHomes == Integer.MAX_VALUE ? "∞" : String.valueOf(maxHomes);
        player.sendMessage("§8[§6Cantale§8] §7=== Vos Homes (" + homes.size() + "/" + maxLabel + ") ===");
        for (Home home : homes.values()) {
            sendHomeLine(player, home);
        }
    }

    /**
     * Liste les homes d'un joueur cible pour le staff (/adminhome check).
     */
    public void listHomesOf(org.bukkit.command.CommandSender viewer, UUID targetUuid, String displayName) {
        Map<String, Home> homes = ensureHomesLoaded(targetUuid);
        String name = displayName != null ? displayName : targetUuid.toString();

        if (homes.isEmpty()) {
            viewer.sendMessage("§8[§6Cantale§8] §c" + name + " n'a aucun home.");
            return;
        }

        int maxHomes = getMaxHomes(targetUuid);
        String maxLabel = maxHomes == Integer.MAX_VALUE ? "∞" : String.valueOf(maxHomes);
        viewer.sendMessage("§8[§6Cantale§8] §7=== Homes de §e" + name + " §7(" + homes.size() + "/" + maxLabel + ") ===");
        for (Home home : homes.values()) {
            sendHomeLine(viewer, home);
        }
    }

    /**
     * Téléporte immédiatement un staff vers le home d'un autre joueur (pas de délai / cooldown).
     */
    public boolean teleportAdminToHome(Player admin, UUID targetUuid, String homeName, String targetDisplayName) {
        Home home = findHome(targetUuid, homeName);
        if (home == null) {
            admin.sendMessage("§8[§6Cantale§8] §cHome §e" + homeName + " §cinexistant pour §e"
                    + (targetDisplayName != null ? targetDisplayName : "ce joueur") + "§c.");
            return false;
        }

        Location loc = home.getLocation();
        if (loc == null || loc.getWorld() == null) {
            admin.sendMessage("§8[§6Cantale§8] §cLe monde de ce home est introuvable (déchargé ?).");
            return false;
        }

        plugin.getChunkLoader().preloadChunksAround(loc, 1);
        loc.getWorld().getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        admin.teleport(loc);
        admin.sendMessage("§8[§6Cantale§8] §aTéléporté au home §e" + home.getName()
                + " §ade §e" + (targetDisplayName != null ? targetDisplayName : "joueur") + "§a.");
        return true;
    }

    private void sendHomeLine(org.bukkit.command.CommandSender viewer, Home home) {
        Location loc = home.getLocation();
        if (loc == null || loc.getWorld() == null) {
            viewer.sendMessage("§7  - §e" + home.getName() + " §c(monde introuvable)");
            return;
        }
        viewer.sendMessage("§7  - §e" + home.getName() + " §7(" + loc.getWorld().getName()
                + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
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
