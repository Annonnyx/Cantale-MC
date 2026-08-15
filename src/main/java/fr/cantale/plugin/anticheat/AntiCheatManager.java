package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.alert.AlertManager;
import fr.cantale.plugin.anticheat.alert.PunishmentManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckRegistry;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.combat.AimCheck;
import fr.cantale.plugin.anticheat.combat.AutoClickerCheck;
import fr.cantale.plugin.anticheat.combat.CombatListener;
import fr.cantale.plugin.anticheat.combat.KillAuraCheck;
import fr.cantale.plugin.anticheat.combat.ReachCheck;
import fr.cantale.plugin.anticheat.data.PlayerData;
import fr.cantale.plugin.anticheat.data.TrustManager;
import fr.cantale.plugin.anticheat.data.ViolationLogger;
import fr.cantale.plugin.anticheat.data.ViolationManager;
import fr.cantale.plugin.anticheat.grim.GrimBridge;
import fr.cantale.plugin.anticheat.inventory.AutoArmorCheck;
import fr.cantale.plugin.anticheat.inventory.ChestStealerCheck;
import fr.cantale.plugin.anticheat.inventory.FastUseCheck;
import fr.cantale.plugin.anticheat.inventory.InventoryListener;
import fr.cantale.plugin.anticheat.movement.FlyCheck;
import fr.cantale.plugin.anticheat.movement.MovementListener;
import fr.cantale.plugin.anticheat.movement.NoFallCheck;
import fr.cantale.plugin.anticheat.movement.SpeedCheck;
import fr.cantale.plugin.anticheat.movement.StepCheck;
import fr.cantale.plugin.anticheat.movement.TimerCheck;
import fr.cantale.plugin.anticheat.rotation.RotationCheck;
import fr.cantale.plugin.anticheat.rotation.RotationListener;
import fr.cantale.plugin.anticheat.world.BlockBreakListener;
import fr.cantale.plugin.anticheat.world.FastBreakCheck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager principal du système Anti-Cheat.
 * Initialise tous les composants, gère les cycles de vie,
 * planifie les tâches périodiques de decay/recovery, et traite les violations.
 *
 * Les violations (natives ou Grim) passent par un pipeline unique :
 * plafond anti-cascade (1 pénalité/tick, X points/minute) → violation + trust →
 * alertes staff/Discord → sanctions progressives → log BDD.
 */
public class AntiCheatManager {

    private final CantalePlugin plugin;
    private final AntiCheatConfig config;
    private final CheckRegistry checkRegistry;
    private final ViolationManager violationManager;
    private final TrustManager trustManager;
    private final AlertManager alertManager;
    private final PunishmentManager punishmentManager;
    private final ViolationLogger violationLogger;
    private final GrimBridge grimBridge;

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    // ── Plafonds anti-cascade de trust ──
    /** Dernier tick (bucket de 50 ms) où une pénalité a été appliquée */
    private final Map<UUID, Long> lastPenaltyTick = new ConcurrentHashMap<>();
    /** Pénalité déjà appliquée sur ce tick (on garde la plus grosse) */
    private final Map<UUID, Integer> penaltyAppliedThisTick = new ConcurrentHashMap<>();
    /** Fenêtre glissante des pertes de trust [timestamp, points] sur 60 s */
    private final Map<UUID, Deque<long[]>> trustLossWindows = new ConcurrentHashMap<>();

    private BukkitRunnable backgroundTask;

    public AntiCheatManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.config = new AntiCheatConfig(plugin);
        this.checkRegistry = new CheckRegistry();
        this.violationManager = new ViolationManager();
        this.trustManager = new TrustManager(config.getDefaultTrust(), config.getRecoveryPerMinute());
        this.alertManager = new AlertManager(plugin, this);
        this.punishmentManager = new PunishmentManager(plugin, this);
        this.violationLogger = new ViolationLogger(plugin);

        init();

        // Intégration Grim : détection, bridge violations, fallback natif si absent.
        // Placé après init() pour que les checks soient enregistrés.
        this.grimBridge = new GrimBridge(this);
        this.grimBridge.enable();
    }

    private void init() {
        // Enregistrer les checks dans le registre
        checkRegistry.register(new SpeedCheck(config));
        checkRegistry.register(new FlyCheck(config));
        checkRegistry.register(new NoFallCheck(config));
        checkRegistry.register(new StepCheck(config));
        checkRegistry.register(new TimerCheck(config));

        checkRegistry.register(new ReachCheck(config));
        checkRegistry.register(new KillAuraCheck(config));
        checkRegistry.register(new AutoClickerCheck(config));
        checkRegistry.register(new AimCheck(config));

        checkRegistry.register(new RotationCheck(config));

        checkRegistry.register(new AutoArmorCheck(config));
        checkRegistry.register(new ChestStealerCheck(config));
        checkRegistry.register(new FastUseCheck(config));

        checkRegistry.register(new FastBreakCheck(config, plugin.getCustomItemManager()));

        // Enregistrer les listeners de détection
        Bukkit.getPluginManager().registerEvents(new MovementListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new RotationListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new BlockBreakListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new AntiCheatListener(this), plugin);

        // Lancer la tâche de decay/recovery (toutes les minutes)
        startBackgroundTask();

        plugin.getLogger().info("[AntiCheat] Système de protection initialisé avec succès !");
    }

    /**
     * Traite une violation de check natif signalée par un listener.
     */
    public void handleViolation(Player player, CheckResult result, Check check) {
        processViolation(player, check.getType(), check.getType().getDisplayName(),
                result.getDetails(), check.getTrustPenalty(), "NATIVE");
    }

    /**
     * Traite un flag remonté par Grim (via GrimBridge, déjà sur le thread principal).
     */
    public void handleGrimViolation(Player player, String grimCheckName, String verbose) {
        if (!config.isCheckEnabled(CheckType.GRIM)) {
            return;
        }
        int penalty = config.getGrimTrustPenalty(grimCheckName);
        String details = (verbose == null || verbose.isEmpty()) ? "flag grim" : verbose;
        processViolation(player, CheckType.GRIM, "Grim:" + grimCheckName, details, penalty, "GRIM");
    }

    /**
     * Pipeline unique de traitement d'une violation.
     */
    private void processViolation(Player player, CheckType vlType, String display,
                                  String details, int rawPenalty, String source) {
        UUID uuid = player.getUniqueId();

        // Plafonds anti-cascade : la pénalité effective peut être réduite à 0
        int penalty = applyTickCap(uuid, rawPenalty);
        penalty = applyMinuteCap(uuid, penalty);

        int violations = violationManager.addViolation(uuid, vlType);
        int currentTrust = penalty > 0
                ? trustManager.removeTrust(uuid, penalty)
                : trustManager.getTrust(uuid);

        alertManager.sendAlert(player, display, details, violations, currentTrust);
        punishmentManager.evaluate(player, currentTrust, display);
        violationLogger.logAsync(player, source, display, violations, currentTrust, details);
    }

    /**
     * Plafond par tick : une seule pénalité de trust par joueur et par tick
     * (la plus grosse gagne). Évite les cascades multi-checks sur un même tick.
     */
    private int applyTickCap(UUID uuid, int penalty) {
        if (penalty <= 0) return 0;
        long bucket = System.currentTimeMillis() / 50L;
        Long last = lastPenaltyTick.get(uuid);

        if (last == null || last != bucket) {
            lastPenaltyTick.put(uuid, bucket);
            penaltyAppliedThisTick.put(uuid, penalty);
            return penalty;
        }

        int alreadyApplied = penaltyAppliedThisTick.getOrDefault(uuid, 0);
        if (penalty <= alreadyApplied) {
            return 0;
        }
        penaltyAppliedThisTick.put(uuid, penalty);
        return penalty - alreadyApplied;
    }

    /**
     * Plafond par minute glissante : au plus {@code max-trust-loss-per-minute}
     * points de trust perdus par joueur. {@code 0} = désactivé.
     */
    private int applyMinuteCap(UUID uuid, int penalty) {
        if (penalty <= 0) return 0;
        int max = config.getMaxTrustLossPerMinute();
        if (max <= 0) return penalty;

        long now = System.currentTimeMillis();
        Deque<long[]> window = trustLossWindows.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst()[0] > 60_000L) {
                window.pollFirst();
            }
            int sum = 0;
            for (long[] entry : window) {
                sum += (int) entry[1];
            }
            int budget = Math.max(0, max - sum);
            int applied = Math.min(penalty, budget);
            if (applied > 0) {
                window.addLast(new long[]{now, applied});
            }
            return applied;
        }
    }

    /**
     * Porte anti-lag : true si les mesures de mouvement ne sont pas fiables
     * pour ce joueur (ping très haut) ou pour le serveur (TPS sous le seuil).
     */
    public boolean isLagCompromised(Player player) {
        if (player.getPing() > config.getLagMaxPingMs()) {
            return true;
        }
        try {
            return Bukkit.getTPS()[0] < config.getLagMinTps();
        } catch (Throwable t) {
            return false;
        }
    }

    private void startBackgroundTask() {
        this.backgroundTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!config.isEnabled()) return;

                // Decay des violations chaque minute (thread principal : thread-safe avec les ajouts)
                violationManager.decayAll();

                // Récupération de trust chaque minute
                trustManager.recoverAll();
            }
        };
        // Exécuter toutes les 1200 ticks (60 secondes) — sync pour éviter les races
        this.backgroundTask.runTaskTimer(plugin, 1200L, 1200L);
    }

    /**
     * Récupère ou crée les données de triche associées à un joueur.
     */
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, PlayerData::new);
    }

    /**
     * Supprime toutes les données temporaires liées à un joueur déconnecté.
     * L'historique de kicks (récidive 24 h) est volontairement conservé.
     */
    public void removePlayer(UUID playerId) {
        playerDataMap.remove(playerId);
        violationManager.removePlayer(playerId);
        trustManager.removePlayer(playerId);
        alertManager.clearCooldowns(playerId);
        lastPenaltyTick.remove(playerId);
        penaltyAppliedThisTick.remove(playerId);
        trustLossWindows.remove(playerId);
    }

    public void shutdown() {
        if (backgroundTask != null) {
            backgroundTask.cancel();
        }
        if (grimBridge != null) {
            grimBridge.disable();
        }
        playerDataMap.clear();
    }

    /**
     * Détermine si un joueur doit bypasser entièrement les détections anti-cheat.
     * Les admins/owners bypassent toujours.
     * Les modérateurs bypassent uniquement en mode modération (Vanish ou Trace).
     */
    public boolean shouldBypass(Player player) {
        if (player.isOp()) {
            return true;
        }

        // Si permission explicite de bypass
        if (player.hasPermission(config.getBypassPermission())) {
            return true;
        }

        // Vérifier le grade RankManager
        if (plugin.getRankManager() != null) {
            fr.cantale.plugin.rank.PlayerRank rank = plugin.getRankManager().getRank(player);
            if (rank == fr.cantale.plugin.rank.PlayerRank.ADMIN || rank == fr.cantale.plugin.rank.PlayerRank.OWNER) {
                return true;
            }
        }

        // Vérifier si le modérateur est en mode modération (Vanish ou Trace)
        if (player.hasPermission("cantale.moderator")) {
            if (plugin.getModerationCommand() != null) {
                var vanishMgr = plugin.getModerationCommand().getVanishManager();
                var traceMgr = plugin.getModerationCommand().getTraceManager();

                boolean vanished = vanishMgr != null && vanishMgr.isVanished(player.getUniqueId());
                boolean tracing = traceMgr != null && traceMgr.isInTrace(player.getUniqueId());

                if (vanished || tracing) {
                    return true;
                }
            }
        }

        return false;
    }

    public CantalePlugin getPlugin() { return plugin; }
    public AntiCheatConfig getConfig() { return config; }
    public CheckRegistry getCheckRegistry() { return checkRegistry; }
    public ViolationManager getViolationManager() { return violationManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public AlertManager getAlertManager() { return alertManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public ViolationLogger getViolationLogger() { return violationLogger; }
    public GrimBridge getGrimBridge() { return grimBridge; }
}
