package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarpManager {

    /** Demi-côté de la zone protégée autour d'un warp public : 8 chunks → carré de 16x16 chunks. */
    public static final int PROTECTION_CHUNK_RADIUS = 8;

    private final CantalePlugin plugin;
    private final WarpDAO warpDAO;
    private final Map<String, Warp> warps;

    public WarpManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.warpDAO = new WarpDAO(plugin);
        this.warps = new HashMap<>();
    }

    /**
     * Recherche un warp par nom en ignorant la casse.
     */
    private Warp findWarp(String name) {
        for (Map.Entry<String, Warp> entry : warps.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Recherche la clé exacte d'un warp par nom en ignorant la casse.
     */
    private String findWarpKey(String name) {
        for (String key : warps.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    public boolean createWarp(String name, Location location, boolean isEvent) {
        if (findWarp(name) != null) {
            return false;
        }

        Warp warp = new Warp(name, location, isEvent);
        int id = warpDAO.createWarp(warp);
        if (id > 0) {
            warp.setId(id);
            warps.put(name, warp);
            // Nouveau marqueur warp sur la carte des clients du mod Cantale
            if (plugin.getClientMapBridge() != null) {
                plugin.getClientMapBridge().refreshMarkersForAll();
            }
            return true;
        }
        return false;
    }

    public boolean deleteWarp(String name) {
        String key = findWarpKey(name);
        if (key == null) {
            return false;
        }

        warpDAO.deleteWarp(warps.get(key).getId());
        warps.remove(key);
        // Retirer le marqueur warp de la carte des clients du mod Cantale
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().refreshMarkersForAll();
        }
        return true;
    }

    public boolean teleportWarp(Player player, String name) {
        Warp warp = findWarp(name);
        if (warp == null) {
            player.sendMessage("§8[§6Cantale§8] §cWarp §e" + name + " §cinexistant.");
            return false;
        }

        if (warp.isEvent() && !warp.isActive()) {
            player.sendMessage("§8[§6Cantale§8] §cCe warp d'événement est désactivé.");
            return false;
        }

        Location dest = warp.getLocation();
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage("§8[§6Cantale§8] §cLe monde de ce warp est introuvable (déchargé ?).");
            return false;
        }

        return plugin.getTeleportDelayManager().requestTeleport(player, dest, false, () ->
                player.sendMessage("§8[§6Cantale§8] §aTéléporté au warp §e" + warp.getName() + "§a."));
    }

    public List<Warp> getWarps() {
        return List.copyOf(warps.values());
    }

    /**
     * Renvoie le warp public actif dont la zone protégée couvre {@code location}, sinon null.
     * La zone est un carré de 16x16 chunks : de 8 chunks avant à 7 chunks après le chunk
     * du warp sur chaque axe (le chunk du warp est inclus).
     */
    public Warp getProtectedWarpAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (Warp warp : warps.values()) {
            if (!warp.isActive()) {
                continue;
            }
            Location warpLoc = warp.getLocation();
            if (warpLoc == null || warpLoc.getWorld() == null
                    || !warpLoc.getWorld().getName().equals(location.getWorld().getName())) {
                continue;
            }
            int warpChunkX = warpLoc.getBlockX() >> 4;
            int warpChunkZ = warpLoc.getBlockZ() >> 4;
            if (chunkX >= warpChunkX - PROTECTION_CHUNK_RADIUS && chunkX < warpChunkX + PROTECTION_CHUNK_RADIUS
                    && chunkZ >= warpChunkZ - PROTECTION_CHUNK_RADIUS && chunkZ < warpChunkZ + PROTECTION_CHUNK_RADIUS) {
                return warp;
            }
        }
        return null;
    }

    public boolean isProtectedZone(Location location) {
        return getProtectedWarpAt(location) != null;
    }

    public void listWarps(Player player) {
        if (warps.isEmpty()) {
            player.sendMessage("§8[§6Cantale§8] §cAucun warp disponible.");
            return;
        }

        Component header = Component.text("§8[§6Cantale§8] §7=== Warps ===")
                .append(Component.newline());
        
        for (Warp warp : warps.values()) {
            String eventTag = warp.isEvent() ? (warp.isActive() ? " [§aEVENT§7]" : " [§cDÉSACTIVÉ§7]") : "";
            Location loc = warp.getLocation();
            
            Component warpLine = Component.text("  • ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(warp.getName())
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/warp " + warp.getName()))
                            .hoverEvent(Component.text("§aCliquez pour téléporter")))
                    .append(Component.text(eventTag + " (" + loc.getWorld().getName() + ")")
                            .color(NamedTextColor.GRAY))
                    .append(Component.newline());
            
            header = header.append(warpLine);
        }
        
        player.sendMessage(header);
    }

    public void listEvents(Player player) {
        List<Warp> activeEvents = warps.values().stream()
                .filter(w -> w.isEvent() && w.isActive())
                .toList();

        if (activeEvents.isEmpty()) {
            player.sendMessage("§8[§6Cantale§8] §cAucun événement actif pour le moment.");
            return;
        }

        Component header = Component.text("§8[§6Cantale§8] §7=== Événements Actifs ===")
                .append(Component.newline());

        for (Warp warp : activeEvents) {
            Component warpLine = Component.text("  • ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(warp.getName())
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/warp " + warp.getName()))
                            .hoverEvent(Component.text("§aCliquez pour rejoindre l'événement")))
                    .append(Component.newline());

            header = header.append(warpLine);
        }

        player.sendMessage(header);
    }

    public void setWarpActive(String name, boolean active) {
        Warp warp = findWarp(name);
        if (warp != null && warp.isEvent()) {
            warp.setActive(active);
            warpDAO.updateWarp(warp);
            // Un warp d'événement (dés)activé apparaît/disparaît de la carte des clients du mod
            if (plugin.getClientMapBridge() != null) {
                plugin.getClientMapBridge().refreshMarkersForAll();
            }
        }
    }

    public void loadWarps() {
        List<Warp> loaded = warpDAO.getAllWarps();
        for (Warp warp : loaded) {
            warps.put(warp.getName(), warp);
        }
    }
}
