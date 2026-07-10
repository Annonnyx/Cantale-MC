package fr.cantale.plugin.moderation;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishManager {

    private final Set<UUID> vanishedPlayers;

    public VanishManager() {
        this.vanishedPlayers = new HashSet<>();
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void setVanished(Player player, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(player.getUniqueId());
            player.setInvisible(true);
            // Cacher des autres joueurs
            for (Player other : player.getServer().getOnlinePlayers()) {
                if (!other.hasPermission("cantale.admin")) {
                    other.hidePlayer(player);
                }
            }
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            player.setInvisible(false);
            // Montrer aux autres joueurs
            for (Player other : player.getServer().getOnlinePlayers()) {
                other.showPlayer(player);
            }
        }
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()));
    }

    public void hideFromOthers(Player player) {
        for (Player other : player.getServer().getOnlinePlayers()) {
            if (!other.hasPermission("cantale.admin")) {
                other.hidePlayer(player);
            }
        }
    }

    public void showToOthers(Player player) {
        for (Player other : player.getServer().getOnlinePlayers()) {
            other.showPlayer(player);
        }
    }
}
