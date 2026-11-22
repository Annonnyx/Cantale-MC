package fr.cantale.plugin.trade;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener du système d'échange :
 *  - saisie du montant d'argent dans le chat (bouton « argent » du GUI) ;
 *  - fermeture du GUI (ÉCHAP) → annulation de l'échange ;
 *  - déconnexion → annulation avec retour des items ;
 *  - connexion → restitution des items d'un échange annulé hors ligne.
 */
public class TradeListener implements Listener {

    private final CantalePlugin plugin;
    private final TradeManager tradeManager;

    public TradeListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.tradeManager = plugin.getTradeManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!tradeManager.isAwaitingMoneyInput(player.getUniqueId())) return;
        event.setCancelled(true);
        String message = event.getMessage();
        // La saisie est async : le traitement touche l'API Bukkit → retour sur le thread principal.
        Bukkit.getScheduler().runTask(plugin, () -> tradeManager.handleMoneyInput(player, message));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (tradeManager.getSession(player.getUniqueId()) == null) return;
        if (tradeManager.isAwaitingMoneyInput(player.getUniqueId())) return;
        if (!tradeManager.isTradeInventory(player, event.getInventory())) return;

        // Différé : laisser une éventuelle transition (saisie argent, refresh) se terminer
        // avant de conclure que le joueur a vraiment fermé la fenêtre.
        Bukkit.getScheduler().runTask(plugin, () -> {
            TradeSession session = tradeManager.getSession(player.getUniqueId());
            if (session == null) return;
            if (tradeManager.isAwaitingMoneyInput(player.getUniqueId())) return;
            if (tradeManager.isTradeInventory(player, player.getOpenInventory().getTopInventory())) return;
            tradeManager.cancelTrade(session, "trade.closed", player.getName());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tradeManager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tradeManager.handleJoin(event.getPlayer());
    }
}
