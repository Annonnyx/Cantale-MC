package fr.cantale.plugin.ui.bossbar;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.combat.CombatTagManager;
import fr.cantale.plugin.events.ChatReactionManager;
import fr.cantale.plugin.events.EventMaterialLabels;
import fr.cantale.plugin.events.EventSystemsManager;
import fr.cantale.plugin.events.EventsConfig;
import fr.cantale.plugin.events.FactionEventManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.leaderboard.LeaderboardManager;
import fr.cantale.plugin.wanted.WantedManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossBar personnelle tournante : solde, vies, prime, événements actifs, combat.
 * Une seule barre par joueur — le titre / couleur / progression changent à chaque rotation.
 */
public class RotatingBossBarManager implements Listener {

    private final CantalePlugin plugin;
    private final Map<UUID, PlayerBarState> states = new ConcurrentHashMap<>();
    private BukkitTask rotateTask;

    private boolean enabled = true;
    private int intervalSeconds = 5;

    private LeaderboardManager leaderboardManager;
    private Map<String, Integer> factionPowerRankCache = new HashMap<>();
    private long factionPowerRankCachedAtMs = 0L;
    private static final long FACTION_RANK_CACHE_MS = 60_000L;

    public RotatingBossBarManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        applyConfig();
        if (!enabled) {
            plugin.getLogger().info("[BossBar] Désactivée dans events.yml");
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            attach(player);
        }

        long period = Math.max(1, intervalSeconds) * 20L;
        rotateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, period, period);
        plugin.getLogger().info("[BossBar] Infos joueur rotatives démarrées (intervalle "
                + intervalSeconds + "s)");
    }

    public void stop() {
        if (rotateTask != null) {
            rotateTask.cancel();
            rotateTask = null;
        }
        for (UUID uuid : new ArrayList<>(states.keySet())) {
            detach(uuid);
        }
        states.clear();
    }

    /** Recharge l'intervalle / enabled depuis EventsConfig (après reload events.yml). */
    public void reload() {
        stop();
        start();
    }

    private void applyConfig() {
        EventSystemsManager events = plugin.getEventSystemsManager();
        if (events == null) {
            enabled = true;
            intervalSeconds = 5;
            return;
        }
        EventsConfig config = events.getConfig();
        enabled = config.isBossBarEnabled();
        intervalSeconds = Math.max(2, config.getBossBarIntervalSeconds());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && enabled && isPreferred(player.getUniqueId())) {
                attach(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId());
    }

    /** Applique la préférence joueur (toggle depuis /profile). */
    public void setPlayerEnabled(Player player, boolean preferEnabled) {
        if (!enabled) return;
        if (preferEnabled) {
            attach(player);
        } else {
            detach(player.getUniqueId());
        }
    }

    private boolean isPreferred(UUID uuid) {
        return plugin.getPlayerDAO().isBossBarEnabled(uuid);
    }

    private void attach(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isPreferred(uuid)) {
            return;
        }
        detach(uuid);

        BossBar bar = Bukkit.createBossBar(
                color("&8[&6Cantale&8] &7Chargement…"),
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        bar.setProgress(1.0);
        bar.setVisible(true);
        bar.addPlayer(player);

        PlayerBarState state = new PlayerBarState(bar, 0);
        states.put(uuid, state);
        applySlide(state, buildSlides(player));
    }

    private void detach(UUID uuid) {
        PlayerBarState state = states.remove(uuid);
        if (state != null) {
            state.bar.removeAll();
            state.bar.setVisible(false);
        }
    }

    private void tickAll() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPreferred(player.getUniqueId())) {
                if (states.containsKey(player.getUniqueId())) {
                    detach(player.getUniqueId());
                }
                continue;
            }
            PlayerBarState state = states.get(player.getUniqueId());
            if (state == null) {
                attach(player);
                continue;
            }
            List<Slide> slides = buildSlides(player);
            if (slides.isEmpty()) {
                state.bar.setTitle(color("&8[&6Cantale&8] &7Bienvenue sur Cantale"));
                state.bar.setColor(BarColor.WHITE);
                state.bar.setProgress(1.0);
                continue;
            }
            state.index = (state.index + 1) % slides.size();
            applySlide(state, slides);
        }
    }

    private void applySlide(PlayerBarState state, List<Slide> slides) {
        if (slides.isEmpty()) {
            state.bar.setTitle(color("&8[&6Cantale&8] &7Bienvenue sur Cantale"));
            state.bar.setColor(BarColor.WHITE);
            state.bar.setProgress(1.0);
            return;
        }
        if (state.index >= slides.size()) {
            state.index = 0;
        }
        Slide slide = slides.get(state.index);
        state.bar.setTitle(slide.title);
        state.bar.setColor(slide.color);
        state.bar.setProgress(clamp(slide.progress));
    }

    /**
     * Ordre de rotation :
     * 1. Solde Cantox
     * 2. Vies
     * 3. Faction (nom, pouvoir, classement — si membre)
     * 4. Prime Wanted (si &gt; 0)
     * 5. Événement faction (si actif)
     * 6. Réaction chat (si active)
     * 7. Combat (si tagué)
     */
    private List<Slide> buildSlides(Player player) {
        List<Slide> slides = new ArrayList<>(7);
        UUID uuid = player.getUniqueId();

        double balance = plugin.getEconomyManager().getBalance(uuid);
        slides.add(new Slide(
                color("&8[&6Cantox&8] &7Solde : &6" + formatAmount(balance) + " Cantox"),
                BarColor.YELLOW,
                1.0
        ));

        int lives = plugin.getLifeManager().getLives(uuid);
        boolean unlimited = player.hasPermission("cantale.admin");
        String livesText = unlimited
                ? color("&8[&cVies&8] &7Vies : &aIllimitées &8(admin)")
                : color("&8[&cVies&8] &7Vies restantes : &c" + lives + "&7/&c10");
        BarColor livesColor = unlimited ? BarColor.GREEN
                : (lives <= 2 ? BarColor.RED : (lives <= 5 ? BarColor.YELLOW : BarColor.PINK));
        double livesProgress = unlimited ? 1.0 : clamp(lives / 10.0);
        slides.add(new Slide(livesText, livesColor, livesProgress));

        Faction faction = plugin.getFactionManager().getPlayerFaction(uuid);
        if (faction != null) {
            int power = faction.getPower();
            int rank = getCachedFactionPowerRank(faction.getName());
            String rankText = rank > 0 ? "#" + rank : "—";
            slides.add(new Slide(
                    color("&8[&bFaction&8] &f" + faction.getName()
                            + " &7— &bPouvoir: &f" + power
                            + " &7| &eClassement: &f" + rankText),
                    BarColor.BLUE,
                    clamp(power / 200.0)
            ));
        }

        WantedManager wanted = plugin.getWantedManager();
        if (wanted != null) {
            double bounty = wanted.getTotalBounty(uuid);
            if (bounty > 0) {
                slides.add(new Slide(
                        color("&8[&cWanted&8] &7Prime sur toi : &6" + formatAmount(bounty) + " Cantox"),
                        BarColor.RED,
                        1.0
                ));
            }
        }

        FactionEventManager factionEvents = plugin.getFactionEventManager();
        if (factionEvents != null && factionEvents.isActive()) {
            String title = factionEvents.getEventTitle();
            long endsAt = factionEvents.getEndsAtMs();
            long remainingMs = Math.max(0L, endsAt - System.currentTimeMillis());
            int minutes = (int) Math.ceil(remainingMs / 60_000.0);
            EventSystemsManager systems = plugin.getEventSystemsManager();
            int durationMin = systems != null
                    ? Math.max(1, systems.getConfig().getFactionDurationMinutes())
                    : 60;
            double progress = clamp(remainingMs / (durationMin * 60_000.0));
            slides.add(new Slide(
                    color("&8[&6Event&8] &e" + title + " &7— &f" + minutes + " min"),
                    BarColor.BLUE,
                    progress
            ));
        }

        EventSystemsManager systems = plugin.getEventSystemsManager();
        if (systems != null) {
            ChatReactionManager.ActiveReaction reaction = systems.getChatReactionManager().getActive();
            if (reaction != null) {
                String reactionTitle;
                if (reaction.getType() == ChatReactionManager.ReactionType.SCRAMBLE) {
                    reactionTitle = color("&8[&6Réaction&8] &eMot mélangé en cours — tape le mot !");
                } else {
                    String blockLabel = reaction.getBlock() != null
                            ? EventMaterialLabels.french(reaction.getBlock())
                            : "bloc";
                    reactionTitle = color("&8[&6Réaction&8] &ePremier à casser : &f" + blockLabel);
                }
                slides.add(new Slide(reactionTitle, BarColor.PURPLE, 1.0));
            }
        }

        CombatTagManager combat = plugin.getCombatTagManager();
        if (combat != null && combat.isInCombat(uuid)) {
            int remaining = combat.getRemainingSeconds(uuid);
            String opponent = combat.getOpponentName(uuid);
            String combatTitle = opponent != null
                    ? color("&8[&cCombat&8] &c⚔ En combat avec &e" + opponent + " &7(&e" + remaining + "s&7)")
                    : color("&8[&cCombat&8] &c⚔ En combat &7(&e" + remaining + "s&7)");
            double combatProgress = clamp(remaining / 15.0);
            slides.add(new Slide(combatTitle, BarColor.RED, combatProgress));
        }

        return slides;
    }

    private LeaderboardManager getLeaderboardManager() {
        if (leaderboardManager == null) {
            leaderboardManager = new LeaderboardManager(plugin);
        }
        return leaderboardManager;
    }

    private int getCachedFactionPowerRank(String factionName) {
        long now = System.currentTimeMillis();
        if (now - factionPowerRankCachedAtMs >= FACTION_RANK_CACHE_MS) {
            factionPowerRankCachedAtMs = now;
            factionPowerRankCache = new HashMap<>();
            for (LeaderboardManager.FactionLeaderboardEntry entry
                    : getLeaderboardManager().getTopFactionsByPower(500)) {
                factionPowerRankCache.put(entry.getName().toLowerCase(Locale.ROOT), entry.getRank());
            }
        }
        Integer rank = factionPowerRankCache.get(factionName.toLowerCase(Locale.ROOT));
        return rank != null ? rank : -1;
    }

    private static String formatAmount(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.001) {
            return String.format(Locale.FRANCE, "%,.0f", amount);
        }
        return String.format(Locale.FRANCE, "%,.2f", amount);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static final class PlayerBarState {
        private final BossBar bar;
        private int index;

        private PlayerBarState(BossBar bar, int index) {
            this.bar = bar;
            this.index = index;
        }
    }

    private static final class Slide {
        private final String title;
        private final BarColor color;
        private final double progress;

        private Slide(String title, BarColor color, double progress) {
            this.title = title;
            this.color = color;
            this.progress = progress;
        }
    }
}
