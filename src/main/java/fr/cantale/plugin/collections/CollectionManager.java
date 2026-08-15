package fr.cantale.plugin.collections;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.quests.QuestRewards;
import fr.cantale.plugin.vote.VoteDAO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CollectionManager {

    private final CantalePlugin plugin;
    private final CollectionConfig config;
    private final CollectionDAO dao;
    private final Map<String, Long> amounts = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> claimed = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public CollectionManager(CantalePlugin plugin, CollectionConfig config, CollectionDAO dao) {
        this.plugin = plugin;
        this.config = config;
        this.dao = dao;
    }

    public CollectionConfig config() { return config; }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 15, 20L * 15);
    }

    public void stop() {
        flush();
    }

    public void add(UUID uuid, String category, String key, long delta) {
        if (delta <= 0) return;
        String id = uuid + ":" + category + ":" + key;
        amounts.merge(id, delta, Long::sum);
        dirty.add(id);
    }

    public void set(UUID uuid, String category, String key, long value) {
        String id = uuid + ":" + category + ":" + key;
        Long old = amounts.get(id);
        if (old != null && old >= value) return;
        amounts.put(id, value);
        dirty.add(id);
    }

    public long get(UUID uuid, String category, String key) {
        return amounts.getOrDefault(uuid + ":" + category + ":" + key, 0L);
    }

    public boolean isClaimed(UUID uuid, String category, String key, int tier) {
        return claimed.getOrDefault(uuid, Set.of()).contains(category + ":" + key + ":" + tier);
    }

    public boolean claim(Player player, String category, String key, int tierIndex) {
        CollectionCategory cat = config.categories().get(category);
        if (cat == null || tierIndex < 0 || tierIndex >= cat.tiers().size()) return false;
        long need = cat.tiers().get(tierIndex);
        if (get(player.getUniqueId(), category, key) < need) return false;
        String claimKey = category + ":" + key + ":" + tierIndex;
        Set<String> set = claimed.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        if (!set.add(claimKey)) return false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> dao.claim(player.getUniqueId(), category, key, tierIndex));
        CollectionReward reward = tierIndex < cat.rewards().size()
                ? cat.rewards().get(tierIndex) : cat.rewards().get(cat.rewards().size() - 1);
        if (reward.cantox() > 0) plugin.getEconomyManager().deposit(player, reward.cantox());
        if (reward.lives() > 0) plugin.getLifeManager().addLife(player.getUniqueId(), reward.lives());
        if (reward.key() != null) QuestRewards.giveCrate(plugin, player, reward.key());
        if (reward.spawner() != null) {
            ItemStack sp = plugin.getSpawnerManager().rollSpawnerOfRarity(reward.spawner());
            if (sp != null) {
                var leftover = player.getInventory().addItem(sp);
                leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            }
        }
        player.sendMessage("§8[§6Collections§8] §aPalier " + (tierIndex + 1) + " réclamé !");
        return true;
    }

    public void loadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dao.loadAmounts(uuid).forEach((k, v) -> amounts.put(uuid + ":" + k, v));
            claimed.put(uuid, ConcurrentHashMap.newKeySet());
            claimed.get(uuid).addAll(dao.loadClaimed(uuid));
            syncDerived(uuid);
        });
    }

    public void syncDerived(UUID uuid) {
        syncDerived(uuid, null);
    }

    public void syncDerived(UUID uuid, Runnable after) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerDAO players = plugin.getPlayerDAO();
            set(uuid, "chat", "TOTAL", players.getChatReactions(uuid));
            set(uuid, "playtime", "HOURS", players.getPlaytime(uuid) / 3600L);
            set(uuid, "money", "PEAK", (long) players.getBalancePeak(uuid));
            VoteDAO votes = plugin.getVoteDAO();
            if (votes != null) {
                VoteDAO.VoteStats stats = votes.getStats(uuid);
                if (stats != null) set(uuid, "votes", "TOTAL", stats.totalVotes());
            }
            if (after != null) {
                Bukkit.getScheduler().runTask(plugin, after);
            }
        });
    }

    public void onCrateOpened(UUID uuid) {
        add(uuid, "keys", "TOTAL", 1);
    }

    public void onChatReaction(UUID uuid) {
        add(uuid, "chat", "TOTAL", 1);
    }

    public void onQuestCompleted(UUID uuid) {
        add(uuid, "quests", "TOTAL", 1);
    }

    private void flush() {
        Set<String> snap = Set.copyOf(dirty);
        dirty.removeAll(snap);
        for (String id : snap) {
            String[] p = id.split(":", 3);
            if (p.length < 3) continue;
            try {
                UUID uuid = UUID.fromString(p[0]);
                dao.saveAmount(uuid, p[1], p[2], amounts.getOrDefault(id, 0L));
            } catch (Exception ignored) {
            }
        }
    }
}
