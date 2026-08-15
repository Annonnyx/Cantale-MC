package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.spawners.SpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class QuestEngine {

    private final CantalePlugin plugin;
    private final QuestConfig config;
    private final QuestDAO dao;
    private final SeasonManager seasons;

    private final Map<String, List<GeneratedQuest>> pools = new ConcurrentHashMap<>();
    private final Map<UUID, Map<QuestPeriod, String>> accepted = new ConcurrentHashMap<>();
    private final Map<String, Integer> amounts = new ConcurrentHashMap<>();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();
    private final Set<String> setClaimed = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> winners = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> contributions = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public QuestEngine(CantalePlugin plugin, QuestConfig config, QuestDAO dao, SeasonManager seasons) {
        this.plugin = plugin;
        this.config = config;
        this.dao = dao;
        this.seasons = seasons;
    }

    public QuestConfig config() { return config; }
    public SeasonManager seasons() { return seasons; }

    public void start() {
        seasons.loadOrStart();
        winners.putAll(dao.loadWinners());
        for (QuestDAO.ProgressRow row : dao.loadProgress("f:")) {
            amounts.put(row.ownerId() + "|" + row.instanceId(), row.amount());
            if (row.completed()) completed.add(row.ownerId() + "|" + row.instanceId());
        }
        for (QuestPeriod period : QuestPeriod.values()) {
            ensurePool(QuestScope.SOLO, period);
            ensurePool(QuestScope.FACTION, period);
        }
        for (QuestPeriod period : QuestPeriod.values()) {
            for (GeneratedQuest q : pool(QuestScope.FACTION, period)) {
                contributions.computeIfAbsent(q.id(), id -> new ConcurrentHashMap<>())
                        .putAll(dao.loadContributions(q.id()));
            }
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 10, 20L * 10);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPools, 20L * 60, 20L * 60);
        plugin.getLogger().info("[Quêtes] Moteur démarré.");
    }

    public void stop() {
        flush();
    }

    public List<GeneratedQuest> pool(QuestScope scope, QuestPeriod period) {
        return pools.getOrDefault(poolId(scope, period), List.of());
    }

    public GeneratedQuest byId(String instanceId) {
        for (List<GeneratedQuest> list : pools.values()) {
            for (GeneratedQuest q : list) {
                if (q.id().equals(instanceId)) return q;
            }
        }
        return null;
    }

    public String acceptedId(UUID uuid, QuestPeriod period) {
        Map<QuestPeriod, String> map = accepted.get(uuid);
        return map == null ? null : map.get(period);
    }

    public int progress(String ownerId, String instanceId) {
        return amounts.getOrDefault(ownerKey(ownerId, instanceId), 0);
    }

    public boolean isCompleted(String ownerId, String instanceId) {
        return completed.contains(ownerKey(ownerId, instanceId));
    }

    public Integer winner(String instanceId) {
        return winners.get(instanceId);
    }

    public Map<UUID, Integer> contributions(String instanceId) {
        return contributions.getOrDefault(instanceId, Map.of());
    }

    public boolean acceptSolo(Player player, String instanceId) {
        GeneratedQuest quest = byId(instanceId);
        if (quest == null || quest.scope() != QuestScope.SOLO) return false;
        UUID uuid = player.getUniqueId();
        String current = acceptedId(uuid, quest.period());
        if (current != null && !current.equals(instanceId) && !isCompleted(uuid.toString(), current)) {
            player.sendMessage("§8[§6Quêtes§8] §cTu as déjà une quête " + quest.period().french().toLowerCase() + " en cours.");
            return false;
        }
        accepted.computeIfAbsent(uuid, id -> new EnumMap<>(QuestPeriod.class)).put(quest.period(), instanceId);
        markDirty(uuid.toString(), instanceId, true, false);
        player.sendMessage("§8[§6Quêtes§8] §aQuête acceptée : §e" + quest.title()
                + " §7(§f" + quest.amount() + "§7).");
        return true;
    }

    public void addProgress(Player player, QuestObjectiveType type, String targetKey, int amount) {
        if (player == null || amount <= 0) return;
        UUID uuid = player.getUniqueId();
        for (QuestPeriod period : QuestPeriod.values()) {
            String acceptedId = acceptedId(uuid, period);
            if (acceptedId != null) {
                GeneratedQuest q = byId(acceptedId);
                if (q != null && matches(q, type, targetKey) && !isCompleted(uuid.toString(), q.id())) {
                    bump(uuid.toString(), q, amount, player);
                }
            }
        }
        Faction fac = plugin.getFactionManager().getPlayerFaction(uuid);
        if (fac == null) return;
        String facOwner = "f:" + fac.getId();
        for (QuestPeriod period : QuestPeriod.values()) {
            for (GeneratedQuest q : pool(QuestScope.FACTION, period)) {
                if (winners.containsKey(q.id())) continue;
                if (!matches(q, type, targetKey)) continue;
                bump(facOwner, q, amount, player);
                contributions.computeIfAbsent(q.id(), id -> new ConcurrentHashMap<>())
                        .merge(uuid, amount, Integer::sum);
                dirty.add("c:" + q.id() + ":" + uuid);
            }
        }
    }

    private boolean matches(GeneratedQuest q, QuestObjectiveType type, String targetKey) {
        return q.objective() == type && q.targetKey().equalsIgnoreCase(targetKey);
    }

    private void bump(String ownerId, GeneratedQuest quest, int amount, Player actor) {
        String key = ownerKey(ownerId, quest.id());
        int next = Math.min(quest.amount(), amounts.merge(key, amount, Integer::sum));
        amounts.put(key, next);
        boolean justDone = next >= quest.amount() && completed.add(key);
        markDirty(ownerId, quest.id(), ownerId.indexOf('-') >= 0, justDone);
        if (actor != null && justDone && quest.scope() == QuestScope.SOLO) {
            completeSolo(actor, quest);
        }
        if (justDone && quest.scope() == QuestScope.FACTION && ownerId.startsWith("f:")) {
            int facId = Integer.parseInt(ownerId.substring(2));
            completeFaction(quest, facId);
        }
    }

    private void completeSolo(Player player, GeneratedQuest quest) {
        QuestRewards.givePeriodKey(plugin, player, quest.period());
        if (quest.bonusCantox() > 0) {
            plugin.getEconomyManager().deposit(player, quest.bonusCantox());
            player.sendMessage("§8[§6Quêtes§8] §aBonus : §e" + quest.bonusCantox() + " Cantox");
        }
        if (quest.bonusLives() > 0) {
            plugin.getLifeManager().addLife(player.getUniqueId(), quest.bonusLives());
            player.sendMessage("§8[§6Quêtes§8] §aBonus : §e+" + quest.bonusLives() + " vie");
        }
        player.sendMessage("§8[§6Quêtes§8] §aQuête terminée : §e" + quest.title());
        if (plugin.getCollectionManager() != null) {
            plugin.getCollectionManager().add(player.getUniqueId(), "quests", "TOTAL", 1);
        }
        maybeClaimSetBonus(player, quest.period());
    }

    private void maybeClaimSetBonus(Player player, QuestPeriod period) {
        String poolKey = poolId(QuestScope.SOLO, period);
        List<GeneratedQuest> list = pool(QuestScope.SOLO, period);
        if (list.isEmpty()) return;
        for (GeneratedQuest q : list) {
            if (!isCompleted(player.getUniqueId().toString(), q.id())) return;
        }
        String claimKey = player.getUniqueId() + ":SET:" + poolKey;
        if (!setClaimed.add(claimKey)) return;
        dirty.add("s:" + claimKey);
        CrateType extra = switch (period) {
            case DAILY -> CrateType.EPIC;
            case WEEKLY -> CrateType.MYTHIC;
            case SEASONAL -> CrateType.LEGENDARY;
        };
        QuestRewards.giveCrate(plugin, player, extra);
        player.sendMessage("§8[§6Quêtes§8] §6Toutes les quêtes " + period.french().toLowerCase()
                + "s sont finies — tu reçois une clé bonus !");
    }

    private void completeFaction(GeneratedQuest quest, int factionId) {
        if (winners.putIfAbsent(quest.id(), factionId) != null) return;
        dirty.add("w:" + quest.id());
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return;
        int power = config.powerFor(quest.period());
        faction.addPower(power);
        plugin.getFactionManager().saveFaction(faction);
        Bukkit.broadcastMessage("§8[§6Quêtes Fac§8] §e" + faction.getName()
                + " §agagne §f" + quest.title() + " §7(+" + power + " pouvoir).");

        Map<UUID, Integer> contrib = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : contributions.getOrDefault(quest.id(), Map.of()).entrySet()) {
            Faction memberFac = plugin.getFactionManager().getPlayerFaction(e.getKey());
            if (memberFac != null && memberFac.getId() == factionId) {
                contrib.put(e.getKey(), e.getValue());
            }
        }
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(contrib.entrySet());
        ranked.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        for (UUID uuid : contrib.keySet()) {
            plugin.getEconomyManager().addBalance(uuid, config.participantCantox());
            if (plugin.getCollectionManager() != null) {
                plugin.getCollectionManager().add(uuid, "quests", "TOTAL", 1);
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§8[§6Quêtes Fac§8] §a+" + config.participantCantox()
                        + " Cantox pour ta participation.");
            }
        }
        List<Integer> topC = config.topCantox();
        List<CrateType> topK = config.topKeys();
        for (int i = 0; i < ranked.size() && i < topC.size(); i++) {
            UUID uuid = ranked.get(i).getKey();
            plugin.getEconomyManager().addBalance(uuid, topC.get(i));
            Player p = Bukkit.getPlayer(uuid);
            if (i < topK.size() && p != null) {
                QuestRewards.giveCrate(plugin, p, topK.get(i));
            } else if (i < topK.size()) {
                // offline: skip key, cantox already added
            }
            if (p != null) {
                p.sendMessage("§8[§6Quêtes Fac§8] §6Top " + (i + 1) + " contributeur : §e+"
                        + topC.get(i) + " Cantox");
            }
        }
        if (quest.period() == QuestPeriod.WEEKLY
                && ThreadLocalRandom.current().nextDouble() * 100 < config.weeklySpawnerChance()) {
            Player best = ranked.isEmpty() ? null : Bukkit.getPlayer(ranked.get(0).getKey());
            if (best != null) {
                SpawnerManager sm = plugin.getSpawnerManager();
                ItemStack spawner = sm.rollCrateSpawner(CrateType.RARE);
                if (spawner != null) {
                    var leftover = best.getInventory().addItem(spawner);
                    leftover.values().forEach(it -> best.getWorld().dropItemNaturally(best.getLocation(), it));
                    best.sendMessage("§8[§6Quêtes Fac§8] §dTu reçois un spawner (bonus hebdo) !");
                }
            }
        }
    }

    public void addDistance(Player player, int blocks) {
        addProgress(player, QuestObjectiveType.DISTANCE, "WALK", blocks);
    }

    private void ensurePool(QuestScope scope, QuestPeriod period) {
        String id = poolId(scope, period);
        if (pools.containsKey(id)) return;
        String stored = dao.loadPool(id);
        if (stored != null && !stored.isBlank()) {
            List<GeneratedQuest> loaded = new ArrayList<>();
            for (String line : stored.split("\n")) {
                GeneratedQuest q = GeneratedQuest.deserialize(line);
                if (q != null) loaded.add(q);
            }
            if (!loaded.isEmpty()) {
                pools.put(id, loaded);
                return;
            }
        }
        List<GeneratedQuest> generated = generate(scope, period, id);
        pools.put(id, generated);
        StringBuilder sb = new StringBuilder();
        for (GeneratedQuest q : generated) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(q.serialize());
        }
        dao.savePool(id, sb.toString());
    }

    public void refreshPools() {
        seasons.tickAuto();
        pruneStalePools();
        for (QuestScope scope : QuestScope.values()) {
            for (QuestPeriod period : QuestPeriod.values()) {
                ensurePool(scope, period);
            }
        }
    }

    private void pruneStalePools() {
        Set<String> current = new HashSet<>();
        for (QuestScope scope : QuestScope.values()) {
            for (QuestPeriod period : QuestPeriod.values()) {
                current.add(poolId(scope, period));
            }
        }
        pools.keySet().removeIf(k -> !current.contains(k));
        for (Map<QuestPeriod, String> map : accepted.values()) {
            map.entrySet().removeIf(e -> byId(e.getValue()) == null);
        }
    }

    private List<GeneratedQuest> generate(QuestScope scope, QuestPeriod period, String poolId) {
        return generate(scope, period, poolId, poolId.hashCode());
    }

    private List<GeneratedQuest> generate(QuestScope scope, QuestPeriod period, String poolId, long seed) {
        List<QuestTemplate> source = new ArrayList<>(config.templates());
        java.util.Random rng = new java.util.Random(seed);
        java.util.Collections.shuffle(source, rng);
        int n = Math.min(config.poolSize(scope, period), source.size());
        List<GeneratedQuest> out = new ArrayList<>();
        double mult = config.amountMultiplier(period, scope);
        for (int i = 0; i < n; i++) {
            QuestTemplate t = source.get(i);
            int base = Math.max(1, (int) Math.round(t.dailyAmount() * mult));
            int var = config.variancePercent();
            if (var > 0) {
                int delta = Math.max(1, base * var / 100);
                base = Math.max(1, base + rng.nextInt(delta * 2 + 1) - delta);
            }
            int bonusC = 0;
            int bonusL = 0;
            if (scope == QuestScope.SOLO && rng.nextDouble() * 100 < config.bonusChance()) {
                if (rng.nextDouble() * 100 < config.bonusLifeChance()) {
                    bonusL = 1;
                } else {
                    bonusC = rng.nextInt(config.bonusCantoxMin(), config.bonusCantoxMax() + 1);
                }
            }
            String instId = poolId + ":" + t.id();
            out.add(new GeneratedQuest(instId, t.id(), t.title(), t.objective(), t.targetKey(),
                    base, period, scope, bonusC, bonusL));
        }
        return out;
    }

    private String poolId(QuestScope scope, QuestPeriod period) {
        return scope.name() + ":" + period.name() + ":" + seasons.poolKey(period);
    }

    private static String ownerKey(String ownerId, String instanceId) {
        return ownerId + "|" + instanceId;
    }

    private void markDirty(String ownerId, String instanceId, boolean acceptedFlag, boolean completedFlag) {
        dirty.add("p:" + ownerId + "|" + instanceId + "|" + (acceptedFlag ? 1 : 0) + "|" + (completedFlag ? 1 : 0));
    }

    public void loadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (QuestDAO.ProgressRow row : dao.loadProgress(uuid.toString())) {
                amounts.put(ownerKey(row.ownerId(), row.instanceId()), row.amount());
                if (row.completed()) completed.add(ownerKey(row.ownerId(), row.instanceId()));
                if (row.accepted() && row.instanceId().contains(":")) {
                    GeneratedQuest q = byId(row.instanceId());
                    if (q != null && q.scope() == QuestScope.SOLO) {
                        accepted.computeIfAbsent(uuid, id -> new EnumMap<>(QuestPeriod.class))
                                .put(q.period(), q.id());
                    }
                }
                if (row.instanceId().startsWith("SET:")) {
                    setClaimed.add(uuid + ":SET:" + row.instanceId().substring(4));
                }
            }
        });
    }

    public void adminReroll(QuestScope scope, QuestPeriod period) {
        String id = poolId(scope, period);
        pools.remove(id);
        List<GeneratedQuest> generated = generate(scope, period, id, System.nanoTime());
        pools.put(id, generated);
        StringBuilder sb = new StringBuilder();
        for (GeneratedQuest q : generated) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(q.serialize());
        }
        dao.savePool(id, sb.toString());
    }

    private void flush() {
        Set<String> snapshot = new HashSet<>(dirty);
        dirty.removeAll(snapshot);
        for (String token : snapshot) {
            try {
                if (token.startsWith("p:")) {
                    String[] p = token.substring(2).split("\\|", -1);
                    if (p.length < 4) continue;
                    String owner = p[0];
                    String inst = p[1];
                    boolean acc = "1".equals(p[2]);
                    boolean done = "1".equals(p[3]);
                    int amt = amounts.getOrDefault(ownerKey(owner, inst), 0);
                    dao.saveProgress(owner, inst, amt, acc, done);
                } else if (token.startsWith("c:")) {
                    int last = token.lastIndexOf(':');
                    String uuidPart = token.substring(last + 1);
                    String instId = token.substring(2, last);
                    UUID uuid = UUID.fromString(uuidPart);
                    int amt = contributions.getOrDefault(instId, Map.of()).getOrDefault(uuid, 0);
                    dao.saveContribution(instId, uuid, amt);
                } else if (token.startsWith("w:")) {
                    String inst = token.substring(2);
                    Integer fac = winners.get(inst);
                    if (fac != null) dao.saveWinner(inst, fac);
                } else if (token.startsWith("s:")) {
                    String rest = token.substring(2);
                    int idx = rest.indexOf(":SET:");
                    if (idx > 0) {
                        String uuid = rest.substring(0, idx);
                        String pool = rest.substring(idx + 5);
                        dao.saveProgress(uuid, "SET:" + pool, 1, false, true);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Quêtes] flush " + token + " : " + e.getMessage());
            }
        }
    }

    public List<Map.Entry<Integer, Integer>> factionRanking(String instanceId) {
        List<Map.Entry<Integer, Integer>> ranks = new ArrayList<>();
        String suffix = "|" + instanceId;
        for (Map.Entry<String, Integer> e : amounts.entrySet()) {
            if (!e.getKey().endsWith(suffix) || !e.getKey().startsWith("f:")) continue;
            String owner = e.getKey().substring(0, e.getKey().length() - suffix.length());
            try {
                int facId = Integer.parseInt(owner.substring(2));
                ranks.add(Map.entry(facId, e.getValue()));
            } catch (NumberFormatException ignored) {
            }
        }
        ranks.sort(Comparator.comparingInt((Map.Entry<Integer, Integer> e) -> e.getValue()).reversed());
        return ranks;
    }
}
