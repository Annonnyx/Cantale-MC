package fr.cantale.plugin.rank;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont grade Cantale → permissions Bukkit.
 * Sans plugin de permissions (LuckPerms…), hasPermission("cantale.admin") ne passe que pour
 * les ops vanilla. Ce pont attache les perms du grade à la connexion (et au changement de
 * grade via /rank), pour que tout le code existant (cantale.admin, cantale.moderator,
 * cantale.faction.bypass…) fonctionne pour le staff non-op. Attachments retirés à la déco.
 */
public class RankPermissionBridge implements Listener {

    private final CantalePlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public RankPermissionBridge(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    /** (Ré)applique les permissions du grade actuel. Appelé au join et après /rank set/remove. */
    public void refresh(Player player) {
        remove(player);
        PlayerRank rank = plugin.getRankManager().getRank(player);
        PermissionAttachment attachment = switch (rank) {
            case MODERATOR -> {
                PermissionAttachment a = player.addAttachment(plugin);
                a.setPermission("cantale.moderator", true);
                yield a;
            }
            case ADMIN, OWNER -> {
                PermissionAttachment a = player.addAttachment(plugin);
                // cantale.admin a pour enfants (plugin.yml) : givevie, moderator, feed,
                // anticheat.alerts/bypass, faction.bypass — le parent les couvre tous.
                a.setPermission("cantale.admin", true);
                a.setPermission("cantale.moderator", true);
                yield a;
            }
            default -> null;
        };
        if (attachment != null) {
            attachments.put(player.getUniqueId(), attachment);
        }
    }

    private void remove(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            player.removeAttachment(old);
        }
    }
}
