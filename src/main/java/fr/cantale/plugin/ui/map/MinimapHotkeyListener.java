package fr.cantale.plugin.ui.map;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.ui.inventory.WorldMapUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Raccourci « grande carte » sur la minimap tenue en main.
 *
 * Une vraie touche (M) est impossible côté serveur : le client vanilla n'envoie
 * pas les touches lettres. Le geste serveur le plus proche est donc sur l'item :
 * clic droit ou touche F (swap de mains) avec une minimap Cantale → ouvre la
 * carte du monde (équivalent de {@code /carte}).
 */
public class MinimapHotkeyListener implements Listener {

    private static final long ANTI_SPAM_MS = 500L;

    private final CantalePlugin plugin;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public MinimapHotkeyListener(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.getMapUI() == null || !plugin.getMapUI().isMinimap(event.getItem())) return;
        event.setCancelled(true);
        openWorldMap(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.getMapUI() == null) return;
        if (!plugin.getMapUI().isMinimap(event.getMainHandItem())
                && !plugin.getMapUI().isMinimap(event.getOffHandItem())) return;
        event.setCancelled(true);
        openWorldMap(event.getPlayer());
    }

    private void openWorldMap(Player player) {
        // Défaut false : module dormant (carte fournie par le mod client).
        if (!plugin.getConfig().getBoolean("map.enabled", false)) {
            player.sendMessage("§8[§6Carte§8] §cLa carte est désactivée.");
            return;
        }
        // Carte déjà ouverte : le GUI détient une session, ignorer le geste
        if (plugin.getMapUI().getSession(player.getUniqueId()) != null) return;
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(player.getUniqueId());
        if (last != null && now - last < ANTI_SPAM_MS) return;
        lastOpen.put(player.getUniqueId(), now);
        plugin.getUIManager().getNavigationManager().openUI(player, new WorldMapUI(plugin, player));
    }
}
