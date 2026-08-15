package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mini-événements chat aléatoires (scramble / premier à casser un bloc).
 * Cap journalier configurable (8–9/jour), récompenses Cantox + stats leaderboard.
 */
public class ChatReactionManager {

    public enum ReactionType {
        SCRAMBLE,
        BLOCK_BREAK
    }

    public static final class ActiveReaction {
        private final ReactionType type;
        private final String answer;
        private final Material block;
        private final long startedAtMs;

        ActiveReaction(ReactionType type, String answer, Material block) {
            this.type = type;
            this.answer = answer;
            this.block = block;
            this.startedAtMs = System.currentTimeMillis();
        }

        public ReactionType getType() { return type; }
        public String getAnswer() { return answer; }
        public Material getBlock() { return block; }
        public long getStartedAtMs() { return startedAtMs; }
    }

    private final CantalePlugin plugin;
    private final EventsConfig config;
    private final Random random = new Random();

    private ActiveReaction active;
    private int eventsToday;
    private int dayOfYear = -1;
    private BukkitTask timeoutTask;
    private BukkitTask scheduleTask;

    public ChatReactionManager(CantalePlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isChatEnabled()) {
            plugin.getLogger().info("[ChatReactions] Désactivé dans events.yml");
            return;
        }
        scheduleNext();
        plugin.getLogger().info("[ChatReactions] Planificateur démarré (max "
                + config.getChatMaxPerDay() + "/jour)");
    }

    public void stop() {
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
        cancelTimeout();
        active = null;
    }

    public ActiveReaction getActive() {
        return active;
    }

    public boolean tryWinScramble(Player player, String message) {
        ActiveReaction current = active;
        if (current == null || current.getType() != ReactionType.SCRAMBLE) return false;
        if (message == null) return false;
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(current.getAnswer())) return false;
        return completeWin(player);
    }

    public boolean tryWinBlockBreak(Player player, Material broken) {
        ActiveReaction current = active;
        if (current == null || current.getType() != ReactionType.BLOCK_BREAK) return false;
        if (broken == null || broken != current.getBlock()) return false;
        return completeWin(player);
    }

    private boolean completeWin(Player player) {
        ActiveReaction current = active;
        if (current == null) return false;
        active = null;
        cancelTimeout();

        int reward = ThreadLocalRandom.current().nextInt(
                config.getChatRewardMin(), config.getChatRewardMax() + 1);

        plugin.getEconomyManager().deposit(player, reward);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getPlayerDAO().incrementChatReactions(player.getUniqueId()));
        if (plugin.getCollectionManager() != null) {
            plugin.getCollectionManager().onChatReaction(player.getUniqueId());
        }

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &a" + player.getName()
                        + " &7a gagné &6" + reward + " Cantox &7!"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &a+1 victoire de réaction chat enregistrée."));
        return true;
    }

    private void scheduleNext() {
        if (scheduleTask != null) {
            scheduleTask.cancel();
        }
        rollDayCounter();
        if (eventsToday >= config.getChatMaxPerDay()) {
            long ticksUntilTomorrow = ticksUntilNextWindowStart();
            scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, this::startEventOrReschedule, ticksUntilTomorrow);
            return;
        }

        int min = Math.max(1, config.getChatMinIntervalMinutes());
        int max = Math.max(min, config.getChatMaxIntervalMinutes());
        int delayMinutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        long delayTicks = delayMinutes * 60L * 20L;

        // Si hors fenêtre, attendre le prochain début de fenêtre
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        int hour = now.getHour();
        if (hour < config.getChatStartHour() || hour >= config.getChatEndHour()) {
            delayTicks = ticksUntilNextWindowStart();
        }

        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, this::startEventOrReschedule, delayTicks);
    }

    private void startEventOrReschedule() {
        rollDayCounter();
        if (!config.isChatEnabled()) return;
        if (eventsToday >= config.getChatMaxPerDay()) {
            scheduleNext();
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        int hour = now.getHour();
        if (hour < config.getChatStartHour() || hour >= config.getChatEndHour()) {
            scheduleNext();
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleNext();
            return;
        }
        startRandomEvent();
        scheduleNext();
    }

    private void startRandomEvent() {
        if (active != null) return;
        eventsToday++;

        boolean scramble = random.nextDouble() < config.getScrambleChance();
        if (scramble) {
            startScramble();
        } else {
            startBlockBreak();
        }

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&6Réaction&8] &7Personne n'a gagné… prochaine réaction bientôt !"));
                active = null;
            }
        }, config.getChatTimeoutSeconds() * 20L);
    }

    private void startScramble() {
        List<String> words = config.getScrambleWords();
        String word = words.get(random.nextInt(words.size()));
        String scrambled = scrambleWord(word);
        active = new ActiveReaction(ReactionType.SCRAMBLE, word, null);

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &eMot mélangé : &f&l" + scrambled));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &7Premier à écrire le mot correct gagne &6"
                        + config.getChatRewardMin() + "&7-&6" + config.getChatRewardMax() + " Cantox&7 !"));
    }

    private void startBlockBreak() {
        List<Material> blocks = config.getBreakBlocks();
        Material block = blocks.get(random.nextInt(blocks.size()));
        active = new ActiveReaction(ReactionType.BLOCK_BREAK, null, block);

        String nice = block.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &ePremier à casser : &f&l" + nice));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Réaction&8] &7Gagne &6"
                        + config.getChatRewardMin() + "&7-&6" + config.getChatRewardMax() + " Cantox&7 !"));
    }

    private String scrambleWord(String word) {
        if (word.length() <= 1) return word;
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) {
            chars.add(c);
        }
        String result;
        int attempts = 0;
        do {
            Collections.shuffle(chars, random);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            result = sb.toString();
            attempts++;
        } while (result.equals(word) && attempts < 20);
        return result;
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    private void rollDayCounter() {
        int today = ZonedDateTime.now(ZoneId.of("Europe/Paris")).getDayOfYear();
        if (today != dayOfYear) {
            dayOfYear = today;
            eventsToday = 0;
        }
    }

    private long ticksUntilNextWindowStart() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        ZonedDateTime next = now.withHour(config.getChatStartHour()).withMinute(0).withSecond(0).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        long seconds = java.time.Duration.between(now, next).getSeconds();
        return Math.max(20L, seconds * 20L);
    }
}
