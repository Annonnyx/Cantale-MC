package fr.cantale.plugin.network;

import fr.cantale.plugin.CantalePlugin;
import io.papermc.paper.configuration.ServerConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Quand {@code CANTALE_VELOCITY_SECRET} est défini, une connexion qui n'a pas été
 * acceptée par le forwarding Paper est refusée. Aucun contournement : le plugin
 * n'écrit pas le secret et n'accepte pas un handshake direct.
 */
public final class VelocityGuardListener implements Listener {

    private final CantalePlugin plugin;

    public VelocityGuardListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void logStartup() {
        String serverId = plugin.getConfigManager().getServerId();
        boolean enabled = plugin.getConfigManager().isVelocityEnabled();
        if (!enabled) {
            plugin.getLogger().info("[Velocity] Mode proxy désactivé (CANTALE_VELOCITY_SECRET absent). "
                    + "Serveur autonome, id=" + serverId + ".");
            return;
        }
        ServerConfiguration config = Bukkit.getServerConfig();
        boolean proxy = config.isProxyEnabled();
        boolean online = config.isProxyOnlineMode();
        if (!proxy) {
            plugin.getLogger().severe("[Velocity] CANTALE_VELOCITY_SECRET est défini, mais Paper n'a pas "
                    + "le proxy activé (paper-global.yml proxies.velocity). Les connexions directes seront refusées. "
                    + "Le secret n'est pas écrit dans les logs.");
        } else {
            plugin.getLogger().info("[Velocity] Forwarding proxy Paper actif (online-mode=" + online
                    + "). Identité " + serverId + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        boolean enabled = plugin.getConfigManager().isVelocityEnabled();
        boolean proxy = Bukkit.getServerConfig().isProxyEnabled();
        if (VelocityForwarding.decide(enabled, proxy) != VelocityForwarding.Decision.REJECT) {
            return;
        }
        String text = ChatColor.translateAlternateColorCodes('&',
                plugin.messages().raw("fr", "velocity.rejected"));
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                LegacyComponentSerializer.legacySection().deserialize(text));
        plugin.getLogger().warning("[Velocity] Connexion refusée pour " + event.getPlayer().getName()
                + " : forwarding moderne inactif côté Paper (adresse TCP et joueur non distingués par le proxy).");
    }
}
