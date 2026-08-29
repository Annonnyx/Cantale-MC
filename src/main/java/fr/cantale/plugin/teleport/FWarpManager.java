package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Warps publics de faction (/fw) : créés par les officiers+, ouverts à tous,
 * sans zone protégée dédiée — seule la protection de claims classique s'applique.
 */
public class FWarpManager {

    private final CantalePlugin plugin;
    private final FWarpDAO fwarpDAO;
    private final Map<String, FWarp> fwarps;

    public FWarpManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.fwarpDAO = new FWarpDAO(plugin);
        this.fwarps = new HashMap<>();
    }

    public FWarp findFWarp(String name) {
        for (Map.Entry<String, FWarp> entry : fwarps.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String findFWarpKey(String name) {
        for (String key : fwarps.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /** Crée un fw (nom unique, insensible à la casse) à la position du créateur. */
    public boolean createFWarp(String name, Faction faction, Player creator) {
        if (findFWarp(name) != null) {
            return false;
        }

        FWarp fwarp = new FWarp(name, faction.getId(), creator.getUniqueId(), creator.getLocation());
        int id = fwarpDAO.createFWarp(fwarp);
        if (id > 0) {
            fwarp.setId(id);
            fwarps.put(name, fwarp);
            // Nouveau marqueur sur la carte des clients du mod Cantale
            if (plugin.getClientMapBridge() != null) {
                plugin.getClientMapBridge().refreshMarkersForAll();
            }
            return true;
        }
        return false;
    }

    public boolean deleteFWarp(String name) {
        String key = findFWarpKey(name);
        if (key == null) {
            return false;
        }

        fwarpDAO.deleteFWarp(fwarps.get(key).getId());
        fwarps.remove(key);
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().refreshMarkersForAll();
        }
        return true;
    }

    /** Officier+ de la faction propriétaire, ou admin. */
    public boolean canManage(Player player, FWarp fwarp) {
        if (player.hasPermission("cantale.admin")) {
            return true;
        }
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        return faction != null
                && faction.getId() == fwarp.getFactionId()
                && faction.isOfficier(player.getUniqueId());
    }

    public boolean teleportFWarp(Player player, String name) {
        FWarp fwarp = findFWarp(name);
        if (fwarp == null) {
            player.sendMessage("§8[§6Cantale§8] §cWarp de faction §e" + name + " §cinexistant.");
            return false;
        }

        Location dest = fwarp.getLocation();
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage("§8[§6Cantale§8] §cLe monde de ce warp est introuvable (déchargé ?).");
            return false;
        }

        return plugin.getTeleportDelayManager().requestTeleport(player, dest, false, () ->
                player.sendMessage("§8[§6Cantale§8] §aTéléporté au warp §e" + fwarp.getName() + "§a."));
    }

    public List<FWarp> getFWarps() {
        return List.copyOf(fwarps.values());
    }

    public void listFWarps(Player player) {
        if (fwarps.isEmpty()) {
            player.sendMessage("§8[§6Cantale§8] §cAucun warp de faction disponible.");
            return;
        }

        Component header = Component.text("§8[§6Cantale§8] §7=== Warps de faction ===")
                .append(Component.newline());

        List<FWarp> sorted = fwarps.values().stream()
                .sorted(Comparator.comparing(w -> w.getName().toLowerCase()))
                .toList();

        for (FWarp fwarp : sorted) {
            Faction faction = plugin.getFactionManager().getFaction(fwarp.getFactionId());
            String factionName = faction != null ? faction.getName() : "faction dissoute";
            Location loc = fwarp.getLocation();

            Component line = Component.text("  • ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(fwarp.getName())
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/fw " + fwarp.getName()))
                            .hoverEvent(Component.text("§aCliquez pour téléporter")))
                    .append(Component.text(" §7par §f" + factionName
                                    + (loc != null && loc.getWorld() != null ? " §8(" + loc.getWorld().getName() + ")" : ""))
                            .color(NamedTextColor.GRAY))
                    .append(Component.newline());

            header = header.append(line);
        }

        player.sendMessage(header);
    }

    public void loadFWarps() {
        List<FWarp> loaded = fwarpDAO.getAllFWarps();
        for (FWarp fwarp : loaded) {
            fwarps.put(fwarp.getName(), fwarp);
        }
    }
}
