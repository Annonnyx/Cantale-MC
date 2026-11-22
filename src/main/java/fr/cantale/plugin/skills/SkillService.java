package fr.cantale.plugin.skills;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerBalanceRepository;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un métier par joueur, persisté. Les effets lisent {@link #count(Faction, Skill)}.
 */
public final class SkillService {

    private final CantalePlugin plugin;
    private final Map<UUID, SkillRepository.StoredSkill> skills = new ConcurrentHashMap<>();

    public SkillService(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        skills.clear();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().warning("[Métiers] Chargement ignoré : pool SQL indisponible.");
                return;
            }
            skills.putAll(SkillRepository.loadAll(conn));
            plugin.getLogger().info("[Métiers] " + skills.size() + " spécialisation(s) chargée(s).");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Métiers] Chargement échoué : " + e.getMessage());
        }
    }

    public Skill skillOf(UUID playerId) {
        SkillRepository.StoredSkill stored = skills.get(playerId);
        return stored == null ? null : stored.skill();
    }

    public long changedAt(UUID playerId) {
        SkillRepository.StoredSkill stored = skills.get(playerId);
        return stored == null ? 0L : stored.changedAt();
    }

    public int count(Faction faction, Skill skill) {
        if (faction == null || skill == null) {
            return 0;
        }
        int total = 0;
        for (UUID member : faction.getMembers()) {
            if (skill == skillOf(member)) {
                total++;
            }
        }
        return total;
    }

    public int countFactionOf(UUID playerId, Skill skill) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(playerId);
        return count(faction, skill);
    }

    /**
     * @return {@code null} si le métier est posé, sinon le refus
     */
    public SkillChangeRules.Deny change(UUID playerId, Skill skill, boolean inCombat) {
        Skill current = skillOf(playerId);
        boolean same = current == skill;
        long now = System.currentTimeMillis() / 1000L;
        double balance = new PlayerDAO(plugin).getBalance(playerId);
        SkillChangeRules.Deny deny = SkillChangeRules.evaluate(
                inCombat, current != null, same, now, changedAt(playerId), balance);
        if (deny != SkillChangeRules.Deny.NONE) {
            return deny;
        }
        boolean charge = current != null;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Métiers] Changement refusé : pool SQL indisponible.");
                return SkillChangeRules.Deny.FUNDS;
            }
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                if (charge && !PlayerBalanceRepository.applyDelta(conn, playerId, -SkillFormulas.CHANGE_COST)) {
                    conn.rollback();
                    return SkillChangeRules.Deny.FUNDS;
                }
                SkillRepository.upsert(conn, playerId, skill, now, plugin.getDatabaseManager().isSQLite());
                conn.commit();
                skills.put(playerId, new SkillRepository.StoredSkill(skill, now));
                return null;
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("[Métiers] Changement échoué : " + e.getMessage());
                return SkillChangeRules.Deny.FUNDS;
            } finally {
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Métiers] Changement échoué : " + e.getMessage());
            return SkillChangeRules.Deny.FUNDS;
        }
    }
}
