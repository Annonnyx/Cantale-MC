package fr.cantale.plugin.factions.raid;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.FactionDAO;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.secret.SecretTaxManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des raids : démarrage, durée, cooldown, protections progressives,
 * règles d'interaction dans les claims.
 */
public class RaidManager {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final DiplomacyManager diplomacyManager;
    private final SecretTaxManager secretTaxManager;
    private final FactionDAO factionDAO;

    private final int powerSmall;
    private final int powerLarge;
    private final int durationSmall;
    private final int durationLarge;
    private final int cooldownSmall;
    private final int cooldownLarge;
    private final int maxConcurrentSmall;
    private final int maxConcurrentLarge;
    private final int protectionSmall;
    private final int protectionLarge;

    private final Map<Integer, Raid> activeRaids = new ConcurrentHashMap<>();

    public RaidManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.diplomacyManager = plugin.getDiplomatieManager();
        this.secretTaxManager = plugin.getSecretTaxManager();
        this.factionDAO = plugin.getFactionDAO();

        this.powerSmall = plugin.getConfigManager().getRaidPowerSmall();
        this.powerLarge = plugin.getConfigManager().getRaidPowerLarge();
        this.durationSmall = plugin.getConfigManager().getRaidDurationMinutesSmall() * 60;
        this.durationLarge = plugin.getConfigManager().getRaidDurationMinutesLarge() * 60;
        this.cooldownSmall = plugin.getConfigManager().getRaidCooldownMinutesSmall() * 60;
        this.cooldownLarge = plugin.getConfigManager().getRaidCooldownMinutesLarge() * 60;
        this.maxConcurrentSmall = plugin.getConfigManager().getRaidMaxConcurrentSmall();
        this.maxConcurrentLarge = plugin.getConfigManager().getRaidMaxConcurrentLarge();
        this.protectionSmall = plugin.getConfigManager().getRaidProtectionHoursSmall() * 3600;
        this.protectionLarge = plugin.getConfigManager().getRaidProtectionHoursLarge() * 3600;
    }

    public void startTracking() {
        loadActiveRaids();
    }

    public int getRaidDurationSeconds(int power) {
        return scale(power, durationSmall, durationLarge);
    }

    public int getRaidCooldownSeconds(int power) {
        return scale(power, cooldownSmall, cooldownLarge);
    }

    public int getMaxConcurrentRaids(int power) {
        return scale(power, maxConcurrentSmall, maxConcurrentLarge);
    }

    public int getProtectionSeconds(int power) {
        return scale(power, protectionSmall, protectionLarge);
    }

    private int scale(int power, int smallValue, int largeValue) {
        if (powerSmall >= powerLarge) {
            return smallValue;
        }
        double t = (double) (power - powerSmall) / (powerLarge - powerSmall);
        t = Math.max(0.0, Math.min(1.0, t));
        double v = smallValue + (largeValue - smallValue) * t;
        return (int) Math.round(v);
    }

    public String canStartRaid(Player player, Claim targetClaim) {
        Faction attacker = factionManager.getPlayerFaction(player.getUniqueId());
        Faction defender = targetClaim == null ? null : factionManager.getFaction(targetClaim.getFactionId());
        if (attacker == null) return "Tu n'as pas de faction.";
        if (defender == null) return "Ce chunk n'appartient à aucune faction.";
        if (attacker.getId() == defender.getId()) return "Tu ne peux pas raider ta propre faction.";
        FactionRank rank = attacker.getMemberRank(player.getUniqueId());
        if (rank == null || rank.isLessThan(FactionRank.OFFICER)) return "Rang insuffisant (Officier requis).";
        // La guerre n'est PAS requise pour lancer un raid (elle ouvre la pénétration
        // permanente des claims ennemis — voir isAttackingInRaid).
        long now = System.currentTimeMillis() / 1000;

        // Protection progressive du défenseur après un raid subi.
        int defenderPower = defender.getPower();
        int protection = getProtectionSeconds(defenderPower);
        if (defender.getLastRaidAt() > 0 && now < defender.getLastRaidAt() + protection) {
            return "Cette faction est encore protégée après son dernier raid.";
        }

        // Nombre max de raids simultanés subis par le défenseur (progressif selon son pouvoir).
        int defenderMaxConcurrent = getMaxConcurrentRaids(defenderPower);
        if (countActiveRaidsOn(defender.getId()) >= defenderMaxConcurrent) {
            return "Cette faction subit déjà le nombre maximum de raids simultanés.";
        }

        // Cooldown de l'attaquant : une faction ne peut pas spammer les raids.
        int attackerPower = attacker.getPower();
        int cooldown = getRaidCooldownSeconds(attackerPower);
        long lastAttackerRaid = getLastAttackerRaidTime(attacker.getId());
        if (lastAttackerRaid > 0 && now < lastAttackerRaid + cooldown) {
            return "Votre faction est encore en cooldown de raid.";
        }

        // Nombre max de raids simultanés menés par l'attaquant (progressif selon son pouvoir).
        int attackerMaxConcurrent = getMaxConcurrentRaids(attackerPower);
        if (countActiveRaidsByAttacker(attacker.getId()) >= attackerMaxConcurrent) {
            return "Votre faction mène déjà le nombre maximum de raids simultanés.";
        }

        if (targetClaim.isPasdic()) return "Un PASDIC ne peut pas être raider.";
        return null;
    }

    public boolean startRaid(Player player, Claim targetClaim) {
        String error = canStartRaid(player, targetClaim);
        if (error != null) {
            player.sendMessage("§8[§6Cantale§8] §c" + error);
            return false;
        }
        Faction attacker = factionManager.getPlayerFaction(player.getUniqueId());
        Faction defender = factionManager.getFaction(targetClaim.getFactionId());
        long now = System.currentTimeMillis() / 1000;
        int duration = getRaidDurationSeconds(defender.getPower());
        long endsAt = now + duration;
        int id = insertRaid(attacker.getId(), defender.getId(), targetClaim.getWorld(),
                targetClaim.getChunkX(), targetClaim.getChunkZ(), now, endsAt);
        if (id <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cErreur lors du démarrage du raid.");
            return false;
        }
        Raid raid = new Raid(id, attacker.getId(), defender.getId(), targetClaim.getWorld(),
                targetClaim.getChunkX(), targetClaim.getChunkZ(), now, endsAt);
        activeRaids.put(id, raid);
        scheduleEnd(raid);

        String msg = "§8[§6Cantale§8] §cUn raid a été déclaré par §e" + attacker.getName()
                + " §csur §e" + defender.getName() + " §c("
                + (duration / 60) + " min).";
        Bukkit.broadcastMessage(msg);
        return true;
    }

    public void endRaid(Raid raid) {
        if (raid == null) return;
        activeRaids.remove(raid.getId());
        updateRaidActive(raid.getId(), false);
        Faction defender = factionManager.getFaction(raid.getDefenderFactionId());
        Faction attacker = factionManager.getFaction(raid.getAttackerFactionId());
        long now = System.currentTimeMillis() / 1000;
        if (defender != null) {
            defender.setLastRaidAt(now);
            if (secretTaxManager != null) {
                secretTaxManager.grantFreeSecretAfterRaid(defender);
            }
            factionManager.saveFaction(defender);
        }
        String msg = "§8[§6Cantale§8] §7Le raid" + (attacker != null ? " de " + attacker.getName() : "")
                + " sur " + (defender != null ? defender.getName() : "une faction") + " est terminé.";
        Bukkit.broadcastMessage(msg);
    }

    /**
     * {@code true} si les règles de raid s'appliquent à ce claim : raid actif sur ce chunk
     * précis, ou faction propriétaire en guerre (la guerre ouvre les claims des deux
     * belligérants en permanence, sans {@code /raid}).
     */
    public boolean isRaidActive(Claim claim) {
        if (claim == null) return false;
        long now = System.currentTimeMillis() / 1000;
        for (Raid raid : activeRaids.values()) {
            if (!raid.isActive(now)) continue;
            if (raid.getDefenderFactionId() == claim.getFactionId()
                    && raid.getWorld().equals(claim.getWorld())
                    && raid.getChunkX() == claim.getChunkX()
                    && raid.getChunkZ() == claim.getChunkZ()) {
                return true;
            }
        }
        // Guerre : explosions libres dans les claims du belligérant (dans les deux sens)
        return diplomacyManager != null && diplomacyManager.hasAnyWar(claim.getFactionId());
    }

    /**
     * {@code true} si le joueur peut agir dans ce claim comme un assaillant de raid :
     * raid actif de SA faction sur ce chunk, ou guerre entre sa faction et celle du claim
     * (accès permanent dans les deux sens, mêmes permissions qu'en raid : casse, pose,
     * TNT, seaux…). Les PASDIC restent exclus (géré dans ClaimListener).
     */
    public boolean isAttackingInRaid(Player player, Claim claim) {
        if (claim == null) return false;
        Faction attacker = factionManager.getPlayerFaction(player.getUniqueId());
        if (attacker == null) return false;
        Faction defender = factionManager.getFaction(claim.getFactionId());
        if (defender == null || attacker.getId() == defender.getId()) return false;
        boolean atWar = diplomacyManager != null
                && diplomacyManager.isAtWar(attacker.getId(), defender.getId());
        long now = System.currentTimeMillis() / 1000;
        for (Raid raid : activeRaids.values()) {
            if (!raid.isActive(now)) continue;
            if (raid.getAttackerFactionId() == attacker.getId()
                    && raid.getDefenderFactionId() == claim.getFactionId()
                    && raid.getWorld().equals(claim.getWorld())
                    && raid.getChunkX() == claim.getChunkX()
                    && raid.getChunkZ() == claim.getChunkZ()) {
                return true;
            }
        }
        // Guerre : pénétration permanente des claims ennemis (même chemin que le raid)
        return atWar;
    }

    public int countActiveRaidsOn(int defenderFactionId) {
        long now = System.currentTimeMillis() / 1000;
        int count = 0;
        for (Raid raid : activeRaids.values()) {
            if (raid.isActive(now) && raid.getDefenderFactionId() == defenderFactionId) count++;
        }
        return count;
    }

    public int countActiveRaidsByAttacker(int attackerFactionId) {
        long now = System.currentTimeMillis() / 1000;
        int count = 0;
        for (Raid raid : activeRaids.values()) {
            if (raid.isActive(now) && raid.getAttackerFactionId() == attackerFactionId) count++;
        }
        return count;
    }

    /**
     * Dernière date de fin d'un raid mené par la faction attaquante.
     * Utilisé pour le cooldown attaquant (progressif selon le pouvoir de l'attaquant).
     */
    public long getLastAttackerRaidTime(int attackerFactionId) {
        long last = 0;
        String sql = "SELECT MAX(ends_at) AS last_ends_at FROM faction_raids " +
                "WHERE attacker_faction_id = ? AND active = 0";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attackerFactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    last = rs.getLong("last_ends_at");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[RaidManager] Erreur getLastAttackerRaidTime : " + e.getMessage());
        }
        return last;
    }

    private void scheduleEnd(Raid raid) {
        long delayTicks = Math.max(1, (raid.getEndsAt() - System.currentTimeMillis() / 1000) * 20);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeRaids.containsKey(raid.getId())) {
                    endRaid(raid);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private void loadActiveRaids() {
        long now = System.currentTimeMillis() / 1000;
        String sql = "SELECT id, attacker_faction_id, defender_faction_id, world, chunk_x, chunk_z, started_at, ends_at " +
                "FROM faction_raids WHERE active = 1 AND ends_at > ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, now);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Raid raid = new Raid(
                            rs.getInt("id"),
                            rs.getInt("attacker_faction_id"),
                            rs.getInt("defender_faction_id"),
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z"),
                            rs.getLong("started_at"),
                            rs.getLong("ends_at")
                    );
                    activeRaids.put(raid.getId(), raid);
                    scheduleEnd(raid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[RaidManager] Erreur chargement raids : " + e.getMessage());
        }
    }

    private int insertRaid(int attackerId, int defenderId, String world, int chunkX, int chunkZ, long startedAt, long endsAt) {
        String sql = "INSERT INTO faction_raids (attacker_faction_id, defender_faction_id, world, chunk_x, chunk_z, started_at, ends_at, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, attackerId);
            stmt.setInt(2, defenderId);
            stmt.setString(3, world);
            stmt.setInt(4, chunkX);
            stmt.setInt(5, chunkZ);
            stmt.setLong(6, startedAt);
            stmt.setLong(7, endsAt);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[RaidManager] Erreur insertRaid : " + e.getMessage());
        }
        return -1;
    }

    private void updateRaidActive(int raidId, boolean active) {
        String sql = "UPDATE faction_raids SET active = ? WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, active ? 1 : 0);
            stmt.setInt(2, raidId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[RaidManager] Erreur updateRaidActive : " + e.getMessage());
        }
    }

    public Map<Integer, Raid> getActiveRaids() {
        return activeRaids;
    }
}
