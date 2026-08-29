package fr.cantale.plugin.factions;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import fr.cantale.plugin.factions.permissions.FactionPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Warps de faction publics ({@code /fw}), stockés dans la table historique {@code faction_warps}.
 * Quota : même formule que {@code /f setwarp} — 1 si pouvoir &lt; 60, sinon 1 + (pouvoir − 60) / 30.
 * Create/delete : officiers et plus. TP : tout le serveur.
 */
public class FactionWarpManager {

    public static final String PREFIX = "§8[§6Cantale§8] ";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,24}$");
    private static final Set<String> RESERVED = Set.of(
            "create", "set", "delete", "del", "remove", "list", "ls", "help", "tp"
    );

    private final CantalePlugin plugin;
    private final FactionWarpDAO dao;

    public FactionWarpManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.dao = new FactionWarpDAO(plugin);
    }

    public List<FactionWarp> getAll() {
        return dao.findAll();
    }

    public List<FactionWarp> getByFaction(int factionId) {
        return dao.findByFaction(factionId);
    }

    public Optional<FactionWarp> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return dao.findByName(name.trim());
    }

    public boolean canManage(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        return canManage(player, faction);
    }

    public boolean canManage(Player player, Faction faction) {
        if (player == null) return false;
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;
        if (faction == null) return false;
        if (faction.isLeader(player.getUniqueId())) return true;
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        return rank != null && rank.isAtLeast(FactionRank.OFFICER)
                && FactionPermissions.has(rank, FactionPermission.FWARP);
    }

    private void refreshMapMarkers() {
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().refreshMarkersForAll();
        }
    }

    public String validateName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Indiquez un nom (2 à 24 caractères, lettres, chiffres, _ ou -).";
        }
        String name = raw.trim();
        if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            return "Ce nom est réservé (" + String.join(", ", RESERVED) + ").";
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            return "Nom invalide. Utilisez 2 à 24 caractères : lettres, chiffres, _ ou -.";
        }
        return null;
    }

    public boolean create(Player player, String rawName) {
        String error = validateName(rawName);
        if (error != null) {
            player.sendMessage(PREFIX + "§c" + error);
            return false;
        }
        String name = rawName.trim();

        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(PREFIX + "§cVous n'êtes pas dans une faction.");
            return false;
        }
        if (!canManage(player, faction)) {
            player.sendMessage(PREFIX + "§cSeuls les officiers et le chef peuvent créer un warp de faction public.");
            return false;
        }

        Location loc = player.getLocation();
        org.bukkit.Chunk chunk = loc.getChunk();
        Claim claim = plugin.getFactionManager().getClaimAt(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(PREFIX + "§cVous devez être dans un claim de votre faction.");
            return false;
        }

        Optional<FactionWarp> existing = dao.findByName(name);
        if (existing.isPresent()) {
            FactionWarp warp = existing.get();
            if (warp.getFactionId() != faction.getId()) {
                player.sendMessage(PREFIX + "§cLe nom §e" + warp.getName()
                        + " §cest déjà pris par la faction §e" + warp.getFactionName() + "§c. Les noms de FW sont uniques sur tout le serveur.");
                return false;
            }
            if (!dao.updateLocation(warp.getId(), loc)) {
                player.sendMessage(PREFIX + "§cErreur lors du déplacement du warp.");
                return false;
            }
            refreshMapMarkers();
            player.sendMessage(PREFIX + "§aWarp public §e" + warp.getName() + " §adéplacé ici. Tout le serveur peut s'y TP avec §e/fw "
                    + warp.getName() + "§a.");
            return true;
        }

        int maxWarps = faction.getMaxWarps();
        int current = dao.countByFaction(faction.getId());
        if (current >= maxWarps) {
            player.sendMessage(PREFIX + "§cQuota de warps publics atteint (§e" + current + "§c/§e" + maxWarps
                    + "§c). Formule : 1 warp jusqu'à 60 de pouvoir, puis +1 tous les 30."
                    + " Pouvoir actuel : §e" + faction.getPower()
                    + "§c, prochain slot à §e" + faction.powerRequiredForNextWarp() + "§c.");
            return false;
        }

        if (!dao.insert(faction.getId(), name, loc)) {
            player.sendMessage(PREFIX + "§cImpossible de créer ce warp (nom déjà pris ou erreur base).");
            return false;
        }
        refreshMapMarkers();
        player.sendMessage(PREFIX + "§aWarp public §e" + name + " §acréé (§e" + (current + 1) + "§a/§e" + maxWarps
                + "§a). Tout le serveur peut s'y TP avec §e/fw " + name + "§a.");
        return true;
    }

    public boolean delete(Player player, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            player.sendMessage(PREFIX + "§cUsage : /fw delete <nom>");
            return false;
        }
        Optional<FactionWarp> existing = dao.findByName(rawName.trim());
        if (existing.isEmpty()) {
            player.sendMessage(PREFIX + "§cWarp public introuvable.");
            return false;
        }
        FactionWarp warp = existing.get();
        boolean admin = player.isOp() || player.hasPermission("cantale.admin");
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (!admin) {
            if (faction == null) {
                player.sendMessage(PREFIX + "§cVous n'êtes pas dans une faction.");
                return false;
            }
            if (!canManage(player, faction)) {
                player.sendMessage(PREFIX + "§cSeuls les officiers et le chef peuvent supprimer un warp de faction public.");
                return false;
            }
            if (warp.getFactionId() != faction.getId()) {
                player.sendMessage(PREFIX + "§cCe warp appartient à la faction §e" + warp.getFactionName() + "§c.");
                return false;
            }
        }
        if (!dao.deleteById(warp.getId())) {
            player.sendMessage(PREFIX + "§cErreur lors de la suppression.");
            return false;
        }
        refreshMapMarkers();
        player.sendMessage(PREFIX + "§aWarp public §e" + warp.getName() + " §asupprimé.");
        return true;
    }

    public boolean teleport(Player player, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            player.sendMessage(PREFIX + "§cUsage : /fw <nom>");
            return false;
        }
        Optional<FactionWarp> existing = dao.findByName(rawName.trim());
        if (existing.isEmpty()) {
            player.sendMessage(PREFIX + "§cWarp de faction §e" + rawName.trim() + " §cinexistant. §7Tapez §e/fw §7pour la liste.");
            return false;
        }
        FactionWarp warp = existing.get();
        Location dest = warp.toLocation();
        if (dest == null) {
            player.sendMessage(PREFIX + "§cLe monde de ce warp est introuvable (déchargé ?).");
            return false;
        }
        return plugin.getTeleportDelayManager().requestTeleport(player, dest, false, () ->
                player.sendMessage(PREFIX + "§aTéléporté au warp de faction §e" + warp.getName()
                        + " §7(" + warp.getFactionName() + ")§a."));
    }

    public void listAll(Player player) {
        List<FactionWarp> warps = dao.findAll();
        if (warps.isEmpty()) {
            player.sendMessage(PREFIX + "§cAucun warp de faction public. Les officiers en créent avec §e/fw create <nom>§c dans un claim.");
            return;
        }
        Component header = Component.text(PREFIX + "§7=== Warps de faction publics ===")
                .append(Component.newline())
                .append(Component.text("§8Cliquez un nom pour vous y téléporter (warmup comme /warp).")
                        .append(Component.newline()));
        for (FactionWarp warp : warps) {
            Component line = Component.text("  • ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(warp.getName())
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/fw " + warp.getName()))
                            .hoverEvent(Component.text("§aCliquez pour téléporter — /fw " + warp.getName())))
                    .append(Component.text(" §7(" + warp.getFactionName() + ")")
                            .color(NamedTextColor.GRAY))
                    .append(Component.newline());
            header = header.append(line);
        }
        player.sendMessage(header);
    }

    public void listOwnFaction(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(PREFIX + "§cVous n'êtes pas dans une faction. §7Liste publique : §e/fw");
            return;
        }
        List<FactionWarp> warps = dao.findByFaction(faction.getId());
        player.sendMessage(PREFIX + "§6=== FW de " + faction.getName() + " §7("
                + warps.size() + "/" + faction.getMaxWarps() + ") §6===");
        if (warps.isEmpty()) {
            player.sendMessage(PREFIX + "§7Aucun warp. Officiers : §e/fw create <nom> §7dans un claim.");
            return;
        }
        Component header = Component.empty();
        for (FactionWarp warp : warps) {
            Component line = Component.text("  • ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(warp.getName())
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/fw " + warp.getName()))
                            .hoverEvent(Component.text("§aCliquez pour téléporter")))
                    .append(Component.newline());
            header = header.append(line);
        }
        player.sendMessage(header);
        player.sendMessage(PREFIX + "§7Publics : tout le serveur peut faire §e/fw <nom>§7. Liste globale : §e/fw");
    }
}
