package fr.cantale.plugin.world;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionBalanceRepository;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Capture du KOTH au spawn de {@code world_warzone}. La récompense crédite la banque
 * de faction par {@code balance = balance + ?}, avec plafond journalier.
 */
public final class KothController {

    private final CantalePlugin plugin;
    private final Map<Integer, Integer> progress = new HashMap<>();
    private BukkitTask task;

    public KothController(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 80L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        World world = Bukkit.getWorld(WorldTopology.WARZONE);
        if (world == null) {
            return;
        }
        Location center = world.getSpawnLocation();
        Map<Integer, Integer> present = new HashMap<>();
        for (Player player : world.getPlayers()) {
            Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            if (faction == null) {
                continue;
            }
            Location at = player.getLocation();
            if (!KothRules.inside(at.getX() - center.getX(), at.getY() - center.getY(), at.getZ() - center.getZ())) {
                continue;
            }
            present.merge(faction.getId(), 1, Integer::sum);
        }
        Integer lead = KothRules.leading(present);
        boolean empty = present.isEmpty();
        boolean contested = !empty && lead == null;
        for (Integer factionId : present.keySet()) {
            progress.putIfAbsent(factionId, 0);
        }
        Iterator<Map.Entry<Integer, Integer>> it = progress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            boolean leading = lead != null && lead.equals(entry.getKey());
            int next = KothRules.nextProgress(entry.getValue(), leading, contested, empty);
            if (next <= 0 && !present.containsKey(entry.getKey())) {
                it.remove();
                continue;
            }
            entry.setValue(next);
            if (leading && KothRules.captureComplete(next)) {
                resolveCapture(entry.getKey());
                progress.clear();
                return;
            }
        }
    }

    private void resolveCapture(int factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        String name = faction == null ? ("#" + factionId) : faction.getName();
        long now = System.currentTimeMillis() / 1000L;
        long day = LocalDate.ofInstant(Instant.ofEpochMilli(now * 1000L), ZoneId.systemDefault()).toEpochDay();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().warning("[KOTH] Récompense non versée : pool SQL indisponible.");
                plugin.messages().broadcast("koth.deferred", name);
                return;
            }
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                if (!KothRepository.claimSlot(conn, factionId, day, KothRules.MAX_CAPTURES_PER_FACTION_PER_DAY,
                        now, KothRules.REWARD_COOLDOWN_SECONDS)) {
                    conn.rollback();
                    plugin.messages().broadcast("koth.capped", name);
                    return;
                }
                if (!FactionBalanceRepository.applyDelta(conn, factionId, KothRules.REWARD_CANTOX)) {
                    conn.rollback();
                    plugin.getLogger().warning("[KOTH] Crédit refusé pour la faction " + factionId + ".");
                    return;
                }
                double updated = FactionBalanceRepository.readBalance(conn, factionId);
                conn.commit();
                if (faction != null) {
                    faction.setBalance(updated);
                }
                plugin.messages().broadcast("koth.reward", name, (int) KothRules.REWARD_CANTOX);
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("[KOTH] Récompense échouée : " + e.getMessage());
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[KOTH] Récompense échouée : " + e.getMessage());
        }
    }
}
