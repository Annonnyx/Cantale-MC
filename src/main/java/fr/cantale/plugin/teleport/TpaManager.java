package fr.cantale.plugin.teleport;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.ui.chat.ChatMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demandes de téléportation joueur ↔ joueur.
 * <ul>
 *   <li>{@code /tpa} : le demandeur se tp à la cible (si elle accepte).</li>
 *   <li>{@code /tpahere} : la cible se tp au demandeur (si elle accepte).</li>
 * </ul>
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

    private static Component prefix() {
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("Cantale", NamedTextColor.GOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY));
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

        boolean here = type == Type.TPAHERE;
        Component requestLine = prefix()
            .append(Component.text(from.getName(), NamedTextColor.YELLOW))
            .append(Component.text(here
                ? " veut que tu te tp à lui."
                : " veut se tp à toi.", NamedTextColor.GRAY));
        String acceptHover = here
            ? "Tu te téléporteras à " + from.getName()
            : from.getName() + " se téléportera à toi";
        String denyHover = "Refuser la demande de " + from.getName();

        to.sendMessage(requestLine);
        to.sendMessage(prefix()
            .append(ChatMenu.button("Accepter", NamedTextColor.GREEN, "/tpyes", acceptHover))
            .append(ChatMenu.button("Refuser", NamedTextColor.RED, "/tpno", denyHover))
            .append(Component.text("60s", NamedTextColor.DARK_GRAY)));

        if (here) {
            from.sendMessage("§8[§6Cantale§8] §aDemande envoyée à §e" + to.getName()
                + "§a. Il se tp à toi s'il accepte.");
        } else {
            from.sendMessage("§8[§6Cantale§8] §aDemande envoyée à §e" + to.getName()
                + "§a. Tu te tp à lui s'il accepte.");
        }
        return true;
    }

    public void accept(Player target) {
        Request req = pendingByTarget.get(target.getUniqueId());
        if (req == null || req.expiresAtMs() < System.currentTimeMillis()) {
            pendingByTarget.remove(target.getUniqueId());
            target.sendMessage("§8[§6Cantale§8] §cAucune demande de téléportation en attente.");
            return;
        }

        Player from = Bukkit.getPlayer(req.from());
        if (from == null || !from.isOnline()) {
            pendingByTarget.remove(target.getUniqueId());
            target.sendMessage("§8[§6Cantale§8] §cLe joueur n'est plus connecté.");
            return;
        }

        if (plugin.getCombatTagManager() != null) {
            if (plugin.getCombatTagManager().isInCombat(from)) {
                pendingByTarget.remove(target.getUniqueId());
                target.sendMessage("§8[§6Cantale§8] §c" + from.getName() + " est en combat.");
                from.sendMessage("§8[§6Cantale§8] §cDemande annulée : tu es en combat.");
                return;
            }
            if (plugin.getCombatTagManager().isInCombat(target)) {
                pendingByTarget.remove(target.getUniqueId());
                target.sendMessage("§8[§6Cantale§8] §cImpossible d'accepter en combat.");
                from.sendMessage("§8[§6Cantale§8] §c" + target.getName() + " est en combat.");
                return;
            }
        }

        Player mover = req.type() == Type.TPA ? from : target;
        Player destination = req.type() == Type.TPA ? target : from;
        if (!plugin.getTeleportDelayManager().checkTeleportAllowed(mover, false)) {
            if (!mover.getUniqueId().equals(target.getUniqueId())) {
                target.sendMessage("§8[§6Cantale§8] §c" + mover.getName() + " a un cooldown de téléportation.");
            }
            return;
        }

        pendingByTarget.remove(target.getUniqueId());

        if (req.type() == Type.TPA) {
            target.sendMessage("§8[§6Cantale§8] §aDemande acceptée. §e" + from.getName() + " §ase tp à toi.");
            from.sendMessage("§8[§6Cantale§8] §a" + target.getName() + " a accepté. Tu te tp à lui.");
        } else {
            target.sendMessage("§8[§6Cantale§8] §aDemande acceptée. Tu te tp à §e" + from.getName() + "§a.");
            from.sendMessage("§8[§6Cantale§8] §a" + target.getName() + " a accepté. Il se tp à toi.");
        }

        final Player destFinal = destination;
        plugin.getTeleportDelayManager().requestTeleport(mover, destFinal.getLocation(), false, () -> {
            if (!mover.isOnline() || !destFinal.isOnline()) {
                if (mover.isOnline()) {
                    mover.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : joueur hors ligne.");
                }
                return false;
            }
            if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(mover)) {
                mover.sendMessage("§8[§6Cantale§8] §cTéléportation annulée : combat.");
                return false;
            }
            mover.teleport(destFinal.getLocation());
            mover.sendMessage("§8[§6Cantale§8] §aTéléportation effectuée.");
            return true;
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
