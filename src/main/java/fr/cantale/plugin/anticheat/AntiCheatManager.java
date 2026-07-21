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
import fr.cantale.plugin.anticheat.data.ViolationManager;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager principal du système Anti-Cheat.
 * Initialise tous les composants, gère les cycles de vie,
 * planifie les tâches périodiques de decay/recovery, et traite les violations.
 */
public class AntiCheatManager {

    private final CantalePlugin plugin;
    private final AntiCheatConfig config;
    private final CheckRegistry checkRegistry;
    private final ViolationManager violationManager;
    private final TrustManager trustManager;
    private final AlertManager alertManager;
    private final PunishmentManager punishmentManager;

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private BukkitRunnable backgroundTask;

    public AntiCheatManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.config = new AntiCheatConfig(plugin);
        this.checkRegistry = new CheckRegistry();
        this.violationManager = new ViolationManager();
        this.trustManager = new TrustManager(config.getDefaultTrust(), config.getRecoveryPerMinute());
        this.alertManager = new AlertManager(plugin, this);
        this.punishmentManager = new PunishmentManager(plugin, this);

        init();
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

        // Enregistrer les listeners de détection
        Bukkit.getPluginManager().registerEvents(new MovementListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new RotationListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new AntiCheatListener(this), plugin);

        // Lancer la tâche de decay/recovery (toutes les minutes)
        startBackgroundTask();

        plugin.getLogger().info("[AntiCheat] Système de protection initialisé avec succès !");
    }

    /**
     * Traite une violation de check signalée par un listener.
     */
    public void handleViolation(Player player, CheckResult result, Check check) {
        UUID uuid = player.getUniqueId();
        
        // Ajouter la violation
        int violations = violationManager.addViolation(uuid, check.getType());
        
        // Calculer la pénalité de trust
        int penalty = check.getTrustPenalty();
        int currentTrust = trustManager.removeTrust(uuid, penalty);

        // Envoyer l'alerte
        alertManager.sendAlert(player, result, check, violations, currentTrust);

        // Evaluer la sanction si nécessaire
        punishmentManager.evaluate(player, currentTrust, check);
    }

    private void startBackgroundTask() {
        this.backgroundTask = new BukkitRunnable() {
            private int minutesPassed = 0;

            @Override
            public void run() {
                if (!config.isEnabled()) return;

                // Decay des violations chaque minute (20 ticks * 60 = 1200 ticks = 1 minute)
                violationManager.decayAll();

                // Récupération de trust chaque minute
                trustManager.recoverAll();
            }
        };
        // Exécuter toutes les 1200 ticks (60 secondes)
        this.backgroundTask.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
    }

    /**
     * Récupère ou crée les données de triche associées à un joueur.
     */
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, PlayerData::new);
    }

    /**
     * Supprime toutes les données temporaires liées à un joueur déconnecté.
     */
    public void removePlayer(UUID playerId) {
        playerDataMap.remove(playerId);
        violationManager.removePlayer(playerId);
        trustManager.removePlayer(playerId);
        alertManager.clearCooldowns(playerId);
    }

    public void shutdown() {
        if (backgroundTask != null) {
            backgroundTask.cancel();
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
}
