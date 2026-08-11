package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demandes de téléportation joueur ↔ joueur (/tpa, /tpahere).
 */
public class TpaManager {

    public enum Type { TPA, TPAHERE }

    public record Request(UUID from, UUID to, Type type, long expiresAtMs) {}

    private static final long REQUEST_TTL_MS = 60_000L;

    private final CantalePlugin plugin;
    /** Clé = destinataire (celui qui doit accepter). */
    private final Map<UUID, Request> pendingByTarget = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public TpaManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 20L * 15, 20L * 15);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        pendingByTarget.clear();
    }

    public boolean sendRequest(Player from, Player to, Type type) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage("§8[§6Cantale§8] §cTu ne peux pas te téléporter à toi-même.");
            return false;
        }
        if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(from)) {
            from.sendMessage("§8[§6Cantale§8] §cImpossible d'envoyer une demande en combat ! ("
                + plugin.getCombatTagManager().getRemainingSeconds(from.getUniqueId()) + "s)");
            return false;
        }

        // Remplace une éventuelle demande précédente vers ce destinataire
        pendingByTarget.put(to.getUniqueId(),
            new Request(from.getUniqueId(), to.getUniqueId(), type, System.currentTimeMillis() + REQUEST_TTL_MS));

        String verb = type == Type.TPA
            ? "§e" + from.getName() + " §7veut se téléporter vers toi"
            : "§e" + from.getName() + " §7veut te téléporter vers lui";

        to.sendMessage("§8[§6Cantale§8] " + verb + "§7.");
        to.sendMessage("§8[§6Cantale§8] §a/tpyes §7pour accepter · §c/tpno §7pour refuser §8(60s)");
        from.sendMessage("§8[§6Cantale§8] §aDemande envoyée à §e" + to.getName() + "§a.");
        return true;
    }

    public void accept(Player target) {
        Request req = pendingByTarget.remove(target.getUniqueId());
        if (req == null || req.expiresAtMs() < System.currentTimeMillis()) {
            target.sendMessage("§8[§6Cantale§8] §cAucune demande de téléportation en attente.");
            return;
        }

        Player from = Bukkit.getPlayer(req.from());
        if (from == null || !from.isOnline()) {
            target.sendMessage("§8[§6Cantale§8] §cLe joueur n'est plus connecté.");
            return;
        }

        if (plugin.getCombatTagManager() != null) {
            if (plugin.getCombatTagManager().isInCombat(from)) {
                target.sendMessage("§8[§6Cantale§8] §c" + from.getName() + " est en combat.");
                from.sendMessage("§8[§6Cantale§8] §cDemande annulée : tu es en combat.");
                return;
            }
            if (plugin.getCombatTagManager().isInCombat(target)) {
                target.sendMessage("§8[§6Cantale§8] §cImpossible d'accepter en combat.");
                from.sendMessage("§8[§6Cantale§8] §c" + target.getName() + " est en combat.");
                return;
            }
        }

        Player mover;
        Player destination;
        if (req.type() == Type.TPA) {
            mover = from;
            destination = target;
        } else {
            mover = target;
            destination = from;
        }

        target.sendMessage("§8[§6Cantale§8] §aDemande acceptée.");
        from.sendMessage("§8[§6Cantale§8] §a" + target.getName() + " a accepté ta demande.");

        final Player destFinal = destination;
        plugin.getTeleportDelayManager().delayTeleport(mover, () -> {
            if (!mover.isOnline() || !destFinal.isOnline()) {
                mover.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : joueur hors ligne.");
                return;
            }
            if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(mover)) {
                mover.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : combat.");
                return;
            }
            mover.teleport(destFinal.getLocation());
            mover.sendMessage("§8[§6Cantale§8] §aTéléportation effectuée.");
        });
    }

    public void deny(Player target) {
        Request req = pendingByTarget.remove(target.getUniqueId());
        if (req == null || req.expiresAtMs() < System.currentTimeMillis()) {
            target.sendMessage("§8[§6Cantale§8] §cAucune demande de téléportation en attente.");
            return;
        }
        target.sendMessage("§8[§6Cantale§8] §cDemande refusée.");
        Player from = Bukkit.getPlayer(req.from());
        if (from != null && from.isOnline()) {
            from.sendMessage("§8[§6Cantale§8] §c" + target.getName() + " a refusé ta demande.");
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> it = pendingByTarget.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Request> e = it.next();
            if (e.getValue().expiresAtMs() < now) {
                it.remove();
            }
        }
    }
}
