package fr.cantale.plugin.lives;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LifeManager {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;

    public LifeManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
    }

    public int getLives(UUID uuid) {
        return playerDAO.getLives(uuid);
    }

    public void setLives(UUID uuid, int lives) {
        playerDAO.setLives(uuid, lives);
        if (lives <= 0) {
            handleZeroLives(uuid);
        }
    }

    public void addLife(UUID uuid) {
        int current = getLives(uuid);
        playerDAO.setLives(uuid, current + 1);
    }

    public void addLife(UUID uuid, int amount) {
        int current = getLives(uuid);
        playerDAO.setLives(uuid, current + amount);
    }

    public void removeLife(UUID uuid) {
        int current = getLives(uuid);
        if (current > 0) {
            playerDAO.setLives(uuid, current - 1);
        }
        if (current - 1 <= 0) {
            handleZeroLives(uuid);
        }
    }

    public void handleDeath(Player player) {
        handleDeath(player, null);
    }

    /**
     * @param inventorySnapshot inventaire cloné à la mort (stockage+armure+offhand), ou null
     */
    public void handleDeath(Player player, ItemStack[] inventorySnapshot) {
        UUID uuid = player.getUniqueId();
        Location deathLoc = player.getLocation();
        
        // Les admins ont des vies illimitées (pas de keepInventory forcé ici)
        if (player.hasPermission("cantale.admin")) {
            playerDAO.incrementDeaths(uuid, deathLoc, inventorySnapshot);
            player.sendMessage(formatMessage("&cVous êtes mort ! En tant qu'admin, vous avez des vies illimitées."));
            return;
        }
        
        int currentLives = getLives(uuid);

        if (currentLives <= 1) {
            playerDAO.setLives(uuid, 0);
            playerDAO.incrementDeaths(uuid, deathLoc, inventorySnapshot);
            banPlayer(player);
        } else {
            playerDAO.decrementLives(uuid);
            playerDAO.incrementDeaths(uuid, deathLoc, inventorySnapshot);
            int remaining = getLives(uuid);
            player.sendMessage(formatMessage("&cVous êtes mort ! Il vous reste " + remaining + " vie(s)."));
        }
    }

    /**
     * Pénalité déco combat : retire toujours 1 vie.
     * Pas d'exemption admin — le broadcast annonce déjà la perte, et la déco combat
     * ne doit pas être gratuite pour le staff / les OP ({@code cantale.admin} default: op).
     * Safe pendant {@code PlayerQuitEvent} (pas de sendMessage au joueur qui part).
     */
    public void handleCombatDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        int currentLives = getLives(uuid);

        playerDAO.incrementDeaths(uuid, player.getLocation());

        if (currentLives <= 1) {
            playerDAO.setLives(uuid, 0);
            banPlayer(player);
        } else {
            playerDAO.decrementLives(uuid);
        }
    }

    public void addLives(UUID uuid, int amount) {
        int current = getLives(uuid);
        int newLives = Math.min(3, current + amount); // Max 3 vies
        playerDAO.setLives(uuid, newLives);
    }

    public void enforceMaxLives(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        int current = getLives(uuid);
        if (current > 3) {
            int excess = current - 3;
            setLives(uuid, 3);
            var customItemMgr = plugin.getCustomItemManager();
            if (customItemMgr != null) {
                ItemStack vieItem = customItemMgr.createCustomItem(fr.cantale.plugin.custom.CustomItemType.VIE);
                vieItem.setAmount(excess);
                var leftover = player.getInventory().addItem(vieItem);
                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
            }
            player.sendMessage(formatMessage("&8[&6Cantale&8] &eAjustement de vos vies : vous étiez à &c" + current
                    + " &evies. La limite maximale est de 3. Vos &c" + excess + " &evie(s) en trop vous ont été rendues sous forme d'item(s)."));
        }
    }

    private void handleZeroLives(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            banPlayer(player);
        }
    }

    private void banPlayer(Player player) {
        String message = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getBanMessage());
        String reason = ChatColor.translateAlternateColorCodes('&', "&cVous n'avez plus de vies sur Cantale !");

        // Empêcher le bannissement avec l'inventaire : drop le stuff (hors EC/PC) puis clear.
        if (player.isOnline()) {
            Location loc = player.getLocation();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    player.getWorld().dropItemNaturally(loc, item.clone());
                }
            }
            player.getInventory().clear();
        }

        // Ban UUID/profil en premier — résiste au changement de pseudo.
        // player.ban(..., kickPlayer=false) : on kick avec le message config ensuite.
        try {
            player.ban(reason, (java.time.Instant) null, "Cantale", false);
        } catch (Exception e) {
            plugin.getLogger().warning("Ban PROFILE échoué pour " + player.getName() + " : " + e.getMessage());
            // Fallback name ban si l'API profil échoue
            plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    player.getName(),
                    reason,
                    (java.util.Date) null,
                    "Cantale"
            );
        }

        // Ban IP (adresse peut être null pendant un quit)
        String ip = null;
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            ip = player.getAddress().getAddress().getHostAddress();
            playerDAO.setLastIp(player.getUniqueId(), ip);
            plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).addBan(
                    ip,
                    reason,
                    (java.util.Date) null,
                    "Cantale"
            );
        }

        if (player.isOnline()) {
            player.kickPlayer(message);
        }
    }

    /**
     * Lève ban NAME + PROFILE + IP après restauration de vies.
     * L'IP vient du joueur en ligne, sinon de {@code players.last_ip}.
     */
    public void pardonLifeBan(UUID uuid, String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            try {
                plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME).pardon(playerName);
            } catch (Exception ignored) {}
            try {
                plugin.getServer().getBanList(org.bukkit.BanList.Type.PROFILE).pardon(playerName);
            } catch (Exception ignored) {}
        }

        String ip = null;
        Player online = uuid != null ? plugin.getServer().getPlayer(uuid) : null;
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            ip = online.getAddress().getAddress().getHostAddress();
        }
        if (ip == null && uuid != null) {
            ip = playerDAO.getLastIp(uuid);
        }
        if (ip != null && !ip.isBlank()) {
            try {
                plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).pardon(ip);
                plugin.getLogger().info("[Lives] Ban IP levé pour " + playerName + " (" + ip + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("[Lives] Impossible de lever le ban IP " + ip + " : " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("[Lives] Pas d'IP connue pour " + playerName
                    + " — utilise /unbanip <ip> (voir banned-ips.json)");
        }
    }

    public boolean canGiveLife(UUID from, UUID to) {
        return getLives(from) > 1;
    }

    public boolean transferLife(UUID from, UUID to) {
        if (!canGiveLife(from, to)) {
            return false;
        }
        playerDAO.decrementLives(from);
        playerDAO.incrementLives(to);
        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
