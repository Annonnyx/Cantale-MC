package fr.cantale.plugin.siege;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.skills.Skill;
import fr.cantale.plugin.skills.SkillService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sièges à bannière. Les blocs cassés ou posés sont enregistrés, puis reconstruits
 * progressivement 30 minutes après la fin. Un redémarrage reprend la file.
 */
public final class SiegeManager {

    public static final long BANNER_GIVE_COOLDOWN_SECONDS = 60L;

    private final CantalePlugin plugin;
    private final Map<Integer, SiegeSession> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBannerGiven = new ConcurrentHashMap<>();
    private final Set<String> missingRegenWorlds = ConcurrentHashMap.newKeySet();
    private final SiegeBreakPace pace = new SiegeBreakPace();
    private BukkitTask watcher;
    private BukkitTask regenTask;

    public SiegeManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        load();
        watcher = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSessions, 40L, 200L);
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegen, 60L, 5L);
    }

    public void stop() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public String giveBanner(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            return "siege.no-faction";
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (rank == null || rank.isLessThan(FactionRank.OFFICER)) {
            return "siege.officer-take";
        }
        long now = System.currentTimeMillis() / 1000L;
        long last = lastBannerGiven.getOrDefault(player.getUniqueId(), 0L);
        if (now < last + BANNER_GIVE_COOLDOWN_SECONDS) {
            return "siege.cooldown";
        }
        ItemStack banner = SiegeBanners.create(plugin);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(banner);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        lastBannerGiven.put(player.getUniqueId(), now);
        return null;
    }

    /**
     * @return message d'erreur (codes &amp;), ou {@code null} si la bannière peut rester posée
     */
    public String tryPlace(Player player, Claim claim, Location bannerAt, BlockState replaced) {
        Faction attacker = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (attacker == null) {
            return "siege.no-faction";
        }
        FactionRank rank = attacker.getMemberRank(player.getUniqueId());
        if (rank == null || rank.isLessThan(FactionRank.OFFICER)) {
            return "siege.officer-place";
        }
        if (claim == null) {
            return "siege.enemy";
        }
        Faction defender = plugin.getFactionManager().getFaction(claim.getFactionId());
        if (defender == null || defender.getId() == attacker.getId()) {
            return "siege.enemy";
        }
        boolean window = VulnerabilityWindow.isOpen(
                defender.getVulnStartMinute(), defender.getVulnEndMinute(),
                VulnerabilityWindow.minuteOfDay(System.currentTimeMillis()));
        boolean attackerActive = hasActiveAttacker(attacker.getId());
        boolean defenderBusy = isDefenderBusy(defender.getId());
        if (!SiegeRules.canOpen(attackerActive, defenderBusy, window, true, claim.isPasdic())) {
            if (claim.isPasdic()) {
                return "siege.pasdic";
            }
            if (!window) {
                return "siege.closed\u001f"
                        + VulnerabilityWindow.format(defender.getVulnStartMinute()) + "\u001f"
                        + VulnerabilityWindow.format(defender.getVulnEndMinute());
            }
            if (defenderBusy) {
                return "siege.defender-busy";
            }
            return "siege.attacker-busy";
        }
        if (bannerAt == null || bannerAt.getWorld() == null) {
            return "siege.bad-spot";
        }
        long now = System.currentTimeMillis() / 1000L;
        int duration = plugin.getConfigManager().getSiegeDurationSeconds();
        int id;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Siège] Démarrage refusé : pool SQL indisponible.");
                return "siege.save-failed";
            }
            id = SiegeRepository.insertSession(conn, attacker.getId(), defender.getId(),
                    bannerAt.getWorld().getName(), bannerAt.getBlockX(), bannerAt.getBlockY(), bannerAt.getBlockZ(),
                    now, now + duration);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Démarrage refusé : " + e.getMessage());
            return "siege.save-failed";
        }
        if (id <= 0) {
            return "siege.save-failed";
        }
        SiegeSession session = new SiegeSession(id, attacker.getId(), defender.getId(),
                bannerAt.getWorld().getName(), bannerAt.getBlockX(), bannerAt.getBlockY(), bannerAt.getBlockZ(),
                now, now + duration, 0L, SiegeRules.ACTIVE);
        byId.put(id, session);
        if (replaced != null && replaced.getLocation() != null
                && !recordState(session, replaced.getLocation(), replaced.getType(), replaced.getBlockData().getAsString())) {
            abortSession(id);
            return "siege.save-failed";
        }
        SiegeAlert.announce(plugin, attacker, defender, Math.max(1, duration / 60));
        return null;
    }

    public boolean isAttackerSieging(Player player, Claim claim) {
        if (player == null || claim == null || claim.isPasdic()) {
            return false;
        }
        Faction attacker = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (attacker == null || attacker.getId() == claim.getFactionId()) {
            return false;
        }
        SiegeSession session = activeOnDefender(claim.getFactionId());
        return session != null && session.attackerFactionId() == attacker.getId();
    }

    public boolean isDefenderUnderSiege(int defenderFactionId) {
        return activeOnDefender(defenderFactionId) != null;
    }

    public boolean isDefenderBusy(int defenderFactionId) {
        for (SiegeSession session : byId.values()) {
            if (session.defenderFactionId() == defenderFactionId && session.busy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} si le bloc peut casser maintenant (et il est alors enregistré)
     */
    public boolean allowBreak(Player player, Claim claim, Block block) {
        SiegeSession session = sessionFor(player, claim);
        if (session == null || block == null) {
            return false;
        }
        int architects = architectCount(claim.getFactionId());
        String key = session.id() + ":" + block.getWorld().getName()
                + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!pace.ready(key, architects)) {
            return false;
        }
        return recordState(session, block.getLocation(), block.getType(), block.getBlockData().getAsString());
    }

    /**
     * @return {@code true} si le bloc peut changer. {@code false} seulement quand il est dans un siège
     *         actif, que la base est ouverte, et que l'état d'avant n'a pas été écrit.
     */
    public boolean recordBlock(Block block) {
        if (block == null) {
            return true;
        }
        Claim claim = claimAt(block.getLocation());
        if (claim == null) {
            return true;
        }
        SiegeSession session = activeOnDefender(claim.getFactionId());
        if (session == null) {
            return true;
        }
        return recordState(session, block.getLocation(), block.getType(), block.getBlockData().getAsString());
    }

    /**
     * @return {@code false} si l'état remplacé d'un siège actif n'a pas pu être écrit alors que la base répond.
     */
    public boolean recordState(BlockState state) {
        if (state == null || state.getLocation() == null || state.getLocation().getWorld() == null) {
            return false;
        }
        Claim claim = claimAt(state.getLocation());
        if (claim == null) {
            return true;
        }
        SiegeSession session = activeOnDefender(claim.getFactionId());
        if (session == null) {
            return true;
        }
        return recordState(session, state.getLocation(), state.getType(), state.getBlockData().getAsString());
    }

    private SiegeSession sessionFor(Player player, Claim claim) {
        if (!isAttackerSieging(player, claim)) {
            return null;
        }
        return activeOnDefender(claim.getFactionId());
    }

    private SiegeSession activeOnDefender(int defenderFactionId) {
        for (SiegeSession session : byId.values()) {
            if (session.defenderFactionId() == defenderFactionId && session.active()) {
                return session;
            }
        }
        return null;
    }

    private boolean hasActiveAttacker(int attackerFactionId) {
        for (SiegeSession session : byId.values()) {
            if (session.attackerFactionId() == attackerFactionId && session.active()) {
                return true;
            }
        }
        return false;
    }

    private int architectCount(int defenderFactionId) {
        SkillService skills = plugin.getSkillService();
        if (skills == null) {
            return 0;
        }
        Faction defender = plugin.getFactionManager().getFaction(defenderFactionId);
        return skills.count(defender, Skill.ARCHITECTE);
    }

    /**
     * @return {@code true} si l'état est écrit, ou si le pool est fermé (déjà loggé : la casse peut suivre).
     *         {@code false} si la base est disponible et que l'écriture a échoué.
     */
    private boolean recordState(SiegeSession session, Location location, Material material, String data) {
        if (session == null || !session.active() || location == null || location.getWorld() == null) {
            return false;
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Siège] Bloc non enregistré : pool SQL indisponible.");
                return true;
            }
            SiegeRepository.insertBlockIfAbsent(conn, session.id(), location.getWorld().getName(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                    material.name(), data);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Bloc non enregistré : " + e.getMessage());
            return false;
        }
    }

    private void abortSession(int id) {
        byId.remove(id);
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Siège] Siège #" + id + " non annulé : pool SQL indisponible.");
                return;
            }
            SiegeRepository.deleteSession(conn, id);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Siège #" + id + " non annulé : " + e.getMessage());
        }
    }

    private void tickSessions() {
        long now = System.currentTimeMillis() / 1000L;
        for (SiegeSession session : new ArrayList<>(byId.values())) {
            if (session.active() && now >= session.endsAt()) {
                endSession(session, now);
            }
        }
    }

    private void endSession(SiegeSession session, long now) {
        int delay = plugin.getConfigManager().getSiegeRegenDelaySeconds();
        long regenAt = SiegeRules.regenEpoch(now, delay);
        session.markEnded(regenAt);
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[Siège] Fin non écrite pour #" + session.id() + " : pool SQL indisponible.");
            } else {
                SiegeRepository.updateSession(conn, session.id(), SiegeRules.ENDED, regenAt);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Fin non écrite pour #" + session.id() + " : " + e.getMessage());
        }
        Faction defender = plugin.getFactionManager().getFaction(session.defenderFactionId());
        String name = defender == null ? null : defender.getName();
        int minutes = Math.max(1, delay / 60);
        for (org.bukkit.entity.Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            plugin.messages().send(viewer, "siege.ended",
                    name == null ? plugin.messages().raw(viewer, "siege.someone") : name, minutes);
        }
    }

    private void tickRegen() {
        long now = System.currentTimeMillis() / 1000L;
        for (SiegeSession session : new ArrayList<>(byId.values())) {
            if (!SiegeRules.regenDue(now, session.regenAt(), session.status())) {
                continue;
            }
            restoreBatch(session);
        }
    }

    private void restoreBatch(SiegeSession session) {
        if (!SiegeRules.REGENERATING.equals(session.status())) {
            session.markRegenerating();
            writeStatus(session, SiegeRules.REGENERATING, session.regenAt());
        }
        List<SiegeRepository.BlockRow> rows;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return;
            }
            rows = SiegeRepository.nextUnrestored(conn, session.id(), SiegeRules.REGEN_BLOCKS_PER_BATCH);
            if (rows.isEmpty()) {
                session.markDone();
                SiegeRepository.updateSession(conn, session.id(), SiegeRules.DONE, session.regenAt());
                byId.remove(session.id());
                plugin.getLogger().info("[Siège] Terrain du siège #" + session.id() + " restauré.");
                return;
            }
            for (SiegeRepository.BlockRow row : rows) {
                if (!restore(row)) {
                    continue;
                }
                SiegeRepository.markRestored(conn, row.id());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Régénération #" + session.id() + " : " + e.getMessage());
        }
    }

    /** {@code false} si le monde n'est pas chargé : la ligne reste à restaurer. */
    private boolean restore(SiegeRepository.BlockRow row) {
        World world = Bukkit.getWorld(row.world());
        if (world == null) {
            if (missingRegenWorlds.add(row.world())) {
                plugin.getLogger().warning("[Siège] Monde introuvable pour restaurer " + row.world()
                        + ". Nouvel essai quand il sera chargé.");
            }
            return false;
        }
        missingRegenWorlds.remove(row.world());
        Block block = world.getBlockAt(row.x(), row.y(), row.z());
        try {
            BlockData data = Bukkit.createBlockData(row.data());
            block.setBlockData(data, false);
            return true;
        } catch (IllegalArgumentException ex) {
            Material material = Material.matchMaterial(row.material());
            if (material != null) {
                block.setType(material, false);
                return true;
            }
            plugin.getLogger().warning("[Siège] Bloc illisible " + row.material() + " en "
                    + row.x() + "," + row.y() + "," + row.z() + ".");
            return true;
        }
    }

    private void writeStatus(SiegeSession session, String status, long regenAt) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                return;
            }
            SiegeRepository.updateSession(conn, session.id(), status, regenAt);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Statut non écrit pour #" + session.id() + " : " + e.getMessage());
        }
    }

    private void load() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) {
                plugin.getLogger().warning("[Siège] Reprise ignorée : pool SQL indisponible.");
                return;
            }
            long now = System.currentTimeMillis() / 1000L;
            for (SiegeSession session : SiegeRepository.loadOpen(conn)) {
                byId.put(session.id(), session);
                if (session.active() && now >= session.endsAt()) {
                    endSession(session, now);
                }
            }
            plugin.getLogger().info("[Siège] " + byId.size() + " siège(s) repris.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Siège] Reprise échouée : " + e.getMessage());
        }
    }

    private Claim claimAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return plugin.getFactionManager().getClaimAt(
                location.getWorld().getName(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4);
    }
}
