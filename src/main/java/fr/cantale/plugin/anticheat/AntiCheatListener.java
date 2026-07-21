package fr.cantale.plugin.anticheat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère le cycle de vie des données joueur au niveau de l'AntiCheat.
 * Libère la mémoire lors de la déconnexion d'un joueur.
 */
public class AntiCheatListener implements Listener {

    private final AntiCheatManager manager;

    public AntiCheatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.removePlayer(event.getPlayer().getUniqueId());
    }
}
