package fr.cantale.plugin.moderation;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishManager {

    private final CantalePlugin plugin;
    private final Set<UUID> vanishedPlayers;

    public VanishManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = new HashSet<>();
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void setVanished(Player player, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(player.getUniqueId());
            player.setInvisible(true);
            // Cacher des autres joueurs (et donc aussi les items en main / armure)
            for (Player other : player.getServer().getOnlinePlayers()) {
                if (!canSeeVanished(other)) {
                    other.hidePlayer(plugin, player);
                }
            }
            // Retirer du scoreboard/nametag pour ne pas apparaître dans le tab
            if (plugin.getTagManager() != null) {
                plugin.getTagManager().removePlayerTeam(player);
            }
            // Faire croire à une déconnexion
            sendFakeQuit(player);
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            player.setInvisible(false);
            // Montrer aux autres joueurs
            for (Player other : player.getServer().getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
            // Restaurer le nametag
            if (plugin.getTagManager() != null) {
                plugin.getTagManager().updatePlayerNametag(player);
            }
            // Faire croire à une reconnexion
            sendFakeJoin(player);
        }
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()));
    }

    public void hideFromOthers(Player player) {
        for (Player other : player.getServer().getOnlinePlayers()) {
            if (!other.hasPermission("cantale.admin")) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    public void showToOthers(Player player) {
        for (Player other : player.getServer().getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    private boolean canSeeVanished(Player other) {
        return other.hasPermission("cantale.admin") || other.hasPermission("cantale.moderator");
    }

    private void sendFakeJoin(Player player) {
        Bukkit.broadcastMessage("§e" + player.getName() + " a rejoint le serveur.");
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendSystemMessage("🟢 **" + player.getName() + "** a rejoint le serveur.");
        }
    }

    private void sendFakeQuit(Player player) {
        Bukkit.broadcastMessage("§e" + player.getName() + " a quitté le serveur.");
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            plugin.getDiscordBot().sendSystemMessage("🔴 **" + player.getName() + "** a quitté le serveur.");
        }
    }
}
