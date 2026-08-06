package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événements faction périodiques (HARVEST / MINING / PVP) avec scoreboard sidebar.
 */
public class FactionEventManager {

    private final CantalePlugin plugin;
    private final EventsConfig config;
    private final FactionEventScoreboard scoreboard;

    private FactionEventType activeType;
    private long endsAtMs;
    private final Map<Integer, Integer> factionPoints = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> participants = new ConcurrentHashMap<>();
    private BukkitTask endTask;
    private BukkitTask scheduleTask;
    private BukkitTask scoreboardTask;

    private final List<ZonedDateTime> plannedStarts = new ArrayList<>();
    private int plannedWeek = -1;

    public FactionEventManager(CantalePlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.scoreboard = new FactionEventScoreboard(plugin);
    }

    public void start() {
        if (!config.isFactionEnabled()) {
            plugin.getLogger().info("[FactionEvents] Désactivé dans events.yml");
            return;
        }
        planWeekIfNeeded();
        scheduleNextCheck();
        plugin.getLogger().info("[FactionEvents] Planificateur démarré ("
                + config.getFactionPerWeekMin() + "-" + config.getFactionPerWeekMax() + "/semaine)");
    }

    public void stop() {
        if (scheduleTask != null) scheduleTask.cancel();
        if (endTask != null) endTask.cancel();
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (isActive()) {
            scoreboard.clearAll();
        }
        activeType = null;
    }

    public boolean isActive() {
        return activeType != null && System.currentTimeMillis() < endsAtMs;
    }

    public FactionEventType getActiveType() {
        return isActive() ? activeType : null;
    }

    public long getEndsAtMs() {
        return endsAtMs;
    }

    public Map<Integer, Integer> getFactionPointsSnapshot() {
        return new HashMap<>(factionPoints);
    }

    public boolean isHarvestActive() {
        return isActive() && activeType == FactionEventType.HARVEST;
    }

    /**
     * Dépose des items de récolte. Retourne les points gagnés, ou -1 si inactif.
     */
    public int depositHarvest(Player player, ItemStack[] contents) {
        if (!isHarvestActive()) return -1;
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Récolte&8] &cTu dois être dans une faction pour déposer."));
            return 0;
        }

        int points = 0;
        Map<Material, Integer> rates = config.getHarvestItems();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;
            Integer rate = rates.get(stack.getType());
            if (rate == null) continue;
            points += rate * stack.getAmount() * config.getHarvestPointsPerItem();
            contents[i] = null;
        }

        if (points > 0) {
            addPoints(faction, player.getUniqueId(), points);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Récolte&8] &a+" + points + " points pour &e" + faction.getName() + "&a !"));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Récolte&8] &7Aucun item de récolte valide déposé."));
        }
        return points;
    }

    public void onOreMined(Player player, Material ore) {
        if (!isActive() || activeType != FactionEventType.MINING) return;
        if (!config.getMiningOres().contains(ore)) return;
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) return;
        addPoints(faction, player.getUniqueId(), config.getMiningPointsPerOre());
    }

    public void onFactionKill(Player killer, Player victim) {
        if (!isActive() || activeType != FactionEventType.PVP) return;
        if (killer == null || victim == null) return;
        Faction killerFaction = plugin.getFactionManager().getPlayerFaction(killer.getUniqueId());
        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victim.getUniqueId());
        if (killerFaction == null) return;
        if (victimFaction != null && victimFaction.getId() == killerFaction.getId()) return;
        addPoints(killerFaction, killer.getUniqueId(), config.getPvpPointsPerKill());
        killer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Event&8] &a+" + config.getPvpPointsPerKill()
                        + " points PVP pour &e" + killerFaction.getName() + "&a !"));
    }

    private void addPoints(Faction faction, UUID playerUuid, int amount) {
        factionPoints.merge(faction.getId(), amount, Integer::sum);
        participants.computeIfAbsent(faction.getId(), id -> ConcurrentHashMap.newKeySet()).add(playerUuid);
    }

    private void scheduleNextCheck() {
        if (scheduleTask != null) scheduleTask.cancel();
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            planWeekIfNeeded();
            if (isActive()) return;
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
            ZonedDateTime due = null;
            for (ZonedDateTime start : plannedStarts) {
                if (!start.isAfter(now) && ChronoUnit.MINUTES.between(start, now) < 2) {
                    due = start;
                    break;
                }
            }
            if (due != null) {
                plannedStarts.remove(due);
                beginEvent();
            }
        }, 20L * 30, 20L * 60);
    }

    private void planWeekIfNeeded() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = now.get(WeekFields.ISO.weekBasedYear());
        int weekKey = year * 100 + week;
        if (weekKey == plannedWeek) return;

        plannedWeek = weekKey;
        plannedStarts.clear();

        int count = ThreadLocalRandom.current().nextInt(
                config.getFactionPerWeekMin(), config.getFactionPerWeekMax() + 1);

        ZonedDateTime monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        Set<String> usedSlots = new HashSet<>();
        int attempts = 0;
        while (plannedStarts.size() < count && attempts < 80) {
            attempts++;
            int dayOffset = ThreadLocalRandom.current().nextInt(0, 7);
            int hour = ThreadLocalRandom.current().nextInt(
                    config.getFactionStartHour(), Math.min(23, config.getFactionEndHour()) + 1);
            int minute = ThreadLocalRandom.current().nextInt(0, 4) * 15;
            ZonedDateTime candidate = monday.plusDays(dayOffset)
                    .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
            if (candidate.isBefore(now)) continue;
            String slotKey = candidate.getDayOfYear() + "-" + hour;
            if (!usedSlots.add(slotKey)) continue;
            plannedStarts.add(candidate);
        }
        plannedStarts.sort(Comparator.naturalOrder());

        plugin.getLogger().info("[FactionEvents] Semaine planifiée : " + plannedStarts.size()
                + " événement(s) — " + plannedStarts);
    }

    private void beginEvent() {
        if (isActive()) return;
        List<FactionEventType> types = config.getFactionTypes();
        activeType = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        factionPoints.clear();
        participants.clear();
        endsAtMs = System.currentTimeMillis() + config.getFactionDurationMinutes() * 60_000L;

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Event Faction&8] &e" + activeType.getDisplayName()
                        + " &7— &f" + activeType.getDescription()));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Event Faction&8] &7Durée : &e" + config.getFactionDurationMinutes()
                        + " min&7. Top 5 récompensés !"));

        scoreboard.showForAll(this);
        if (scoreboardTask != null) scoreboardTask.cancel();
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isActive()) return;
            scoreboard.updateAll(this);
        }, 40L, 40L);

        if (endTask != null) endTask.cancel();
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::endEvent,
                config.getFactionDurationMinutes() * 60L * 20L);
    }

    private void endEvent() {
        if (activeType == null) return;
        FactionEventType type = activeType;
        activeType = null;
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
        scoreboard.clearAll();

        List<Map.Entry<Integer, Integer>> ranking = new ArrayList<>(factionPoints.entrySet());
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Event Faction&8] &eFin de l'événement &f" + type.getDisplayName() + "&e !"));

        List<Integer> powerRewards = config.getRewardPower();
        List<Integer> cantoxRewards = config.getRewardCantox();
        int top = Math.min(5, ranking.size());

        for (int i = 0; i < top; i++) {
            Map.Entry<Integer, Integer> entry = ranking.get(i);
            Faction faction = plugin.getFactionManager().getFaction(entry.getKey());
            if (faction == null) continue;

            int power = i < powerRewards.size() ? powerRewards.get(i) : 0;
            int cantox = i < cantoxRewards.size() ? cantoxRewards.get(i) : 0;

            if (power > 0) {
                faction.addPower(power);
                plugin.getFactionManager().saveFaction(faction);
            }

            Set<UUID> members = participants.getOrDefault(faction.getId(), Set.of());
            for (UUID uuid : members) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    if (cantox > 0) {
                        plugin.getEconomyManager().deposit(online, cantox);
                    }
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&6Event Faction&8] &a#" + (i + 1) + " &7— &6+" + cantox
                                    + " Cantox &7+ &b+" + power + " pouvoir &7pour "
                                    + faction.getName()));
                } else if (cantox > 0) {
                    plugin.getEconomyManager().addBalance(uuid, cantox);
                }
            }

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Event Faction&8] &e#" + (i + 1) + " &f" + faction.getName()
                            + " &7— &a" + entry.getValue() + " pts &7(&b+" + power + " power&7)"));
        }

        if (top == 0) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Event Faction&8] &7Aucune faction n'a marqué de points."));
        }

        factionPoints.clear();
        participants.clear();
    }

    public String getFactionName(int factionId) {
        Faction f = plugin.getFactionManager().getFaction(factionId);
        return f != null ? f.getName() : ("#" + factionId);
    }

    public void showScoreboard(Player player) {
        if (isActive()) {
            scoreboard.show(player, this);
        }
    }
}
