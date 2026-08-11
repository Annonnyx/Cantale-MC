package fr.cantale.plugin.lives;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
        UUID uuid = player.getUniqueId();
        
        // Les admins ont des vies illimitées
        if (player.hasPermission("cantale.admin")) {
            playerDAO.incrementDeaths(uuid);
            player.sendMessage(formatMessage("&cVous êtes mort ! En tant qu'admin, vous avez des vies illimitées."));
            return;
        }
        
        int currentLives = getLives(uuid);

        if (currentLives <= 1) {
            playerDAO.setLives(uuid, 0);
            playerDAO.incrementDeaths(uuid);
            banPlayer(player);
        } else {
            playerDAO.decrementLives(uuid);
            playerDAO.incrementDeaths(uuid);
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

        playerDAO.incrementDeaths(uuid);

        if (currentLives <= 1) {
            playerDAO.setLives(uuid, 0);
            banPlayer(player);
        } else {
            playerDAO.decrementLives(uuid);
        }
    }

    public void addLives(UUID uuid, int amount) {
        int current = getLives(uuid);
        int newLives = Math.min(10, current + amount); // Max 10 vies
        playerDAO.setLives(uuid, newLives);
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
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).addBan(
                    player.getAddress().getAddress().getHostAddress(),
                    reason,
                    (java.util.Date) null,
                    "Cantale"
            );
        }

        if (player.isOnline()) {
            player.kickPlayer(message);
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
