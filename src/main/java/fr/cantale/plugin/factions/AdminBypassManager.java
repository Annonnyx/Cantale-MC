package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bypass admin des protections de territoire ({@code /f bypass}).
 *
 * Session uniquement : l'état vit en mémoire, n'est jamais persisté et la
 * déconnexion le coupe. Chaque activation/désactivation est loguée en console
 * (audit staff).
 *
 * Portée : UNIQUEMENT les protections de claims (build, casse, conteneurs,
 * portes, interactions, PvP de claim, PASDIC, spawners/hoppers/runes en claim
 * étranger). Aucun effet sur les invariants de données (pouvoir max, économie),
 * les cooldowns de téléportation ou l'anti-cheat.
 */
public class AdminBypassManager implements Listener {

    /** Permission requise pour {@code /f bypass} (déclarée dans plugin.yml, enfant de cantale.admin). */
    public static final String PERMISSION = "cantale.faction.bypass";

    private final CantalePlugin plugin;
    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();

    public AdminBypassManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBypassing(Player player) {
        return player != null && bypassing.contains(player.getUniqueId());
    }

    /**
     * Bascule l'état de bypass du joueur.
     *
     * @return {@code true} si le bypass est désormais actif
     */
    public boolean toggle(Player player) {
        if (isBypassing(player)) {
            disable(player);
            return false;
        }
        bypassing.add(player.getUniqueId());
        plugin.getLogger().info("[Bypass] " + player.getName() + " a activé le mode bypass admin");
        return true;
    }

    public void disable(Player player) {
        if (player == null) return;
        if (bypassing.remove(player.getUniqueId())) {
            plugin.getLogger().info("[Bypass] " + player.getName() + " a désactivé le mode bypass admin");
        }
    }

    /** Session uniquement : la déconnexion coupe toujours le bypass. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disable(event.getPlayer());
    }
}
