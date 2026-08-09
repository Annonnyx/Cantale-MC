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

    public boolean canSeeVanished(Player viewer) {
        return viewer.hasPermission("cantale.admin") || viewer.hasPermission("cantale.moderator");
    }

    public void setVanished(Player player, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(player.getUniqueId());
            // Do NOT use setInvisible(true): vanilla invisibility still shows held items / armor.
            // hidePlayer fully removes the entity (including equipment) from non-staff clients.
            hideFromNonStaff(player);
            if (plugin.getTagManager() != null) {
                plugin.getTagManager().removePlayerTeam(player);
            }
            sendFakeQuit(player);
            plugin.getLogger().info("[Vanish] " + player.getName() + " est maintenant vanish.");
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            showToAll(player);
            if (plugin.getTagManager() != null) {
                plugin.getTagManager().updatePlayerNametag(player);
            }
            sendFakeJoin(player);
            plugin.getLogger().info("[Vanish] " + player.getName() + " n'est plus vanish.");
        }
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()));
    }

    /**
     * Re-apply hidePlayer for a vanished player (e.g. after equipment / slot changes).
     */
    public void refreshHide(Player vanished) {
        if (!isVanished(vanished.getUniqueId())) {
            return;
        }
        hideFromNonStaff(vanished);
    }

    /**
     * On join: hide existing vanished players from the joiner, and if the joiner
     * is vanished, hide them from others. Delayed 1 tick so entity tracking is ready.
     */
    public void handleJoin(Player joiner) {
        applyJoinHides(joiner);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joiner.isOnline()) {
                applyJoinHides(joiner);
            }
        }, 1L);
    }

    private void applyJoinHides(Player joiner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(joiner)) {
                continue;
            }
            if (isVanished(online.getUniqueId()) && !canSeeVanished(joiner)) {
                joiner.hidePlayer(plugin, online);
            }
        }
        if (isVanished(joiner.getUniqueId())) {
            hideFromNonStaff(joiner);
            if (plugin.getTagManager() != null) {
                plugin.getTagManager().removePlayerTeam(joiner);
            }
        }
    }

    public void hideFromNonStaff(Player vanished) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(vanished)) {
                continue;
            }
            if (!canSeeVanished(other)) {
                other.hidePlayer(plugin, vanished);
                plugin.getLogger().info("[Vanish] Masqué pour " + other.getName());
            }
        }
    }

    public void showToAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    /** @deprecated use {@link #hideFromNonStaff(Player)} */
    public void hideFromOthers(Player player) {
        hideFromNonStaff(player);
    }

    /** @deprecated use {@link #showToAll(Player)} */
    public void showToOthers(Player player) {
        showToAll(player);
    }

    private void sendFakeJoin(Player player) {
        // Message MC uniquement — pas de fausse notif Discord
        Bukkit.broadcastMessage("§e" + player.getName() + " a rejoint le serveur.");
    }

    private void sendFakeQuit(Player player) {
        // Message MC uniquement — pas de fausse notif Discord
        Bukkit.broadcastMessage("§e" + player.getName() + " a quitté le serveur.");
    }
}
