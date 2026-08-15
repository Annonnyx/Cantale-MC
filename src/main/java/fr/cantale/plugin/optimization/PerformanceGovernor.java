package fr.cantale.plugin.optimization;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gouverneur de performance adaptatif.
 *
 * Échantillonne TPS (1 min) + MSPT moyen toutes les {@code check-interval-seconds}
 * et fait basculer le serveur entre 3 modes, avec hystérésis (temps minimum par
 * mode + période saine continue exigée avant de remonter) pour éviter le flapping :
 *
 * - NORMAL   : distances de base (captées au boot), spawners et stacking nominaux.
 * - DEGRADED : simulation-distance réduite, spawners ralentis, stacking plus fréquent.
 * - CRITICAL : distances minimales, spawners très ralentis, cleanup d'urgence +
 *              alerte staff/Discord (cooldown 5 min).
 *
 * Tout est borné et restauré proprement à la remontée de mode et à l'arrêt du plugin.
 */
public class PerformanceGovernor {

    public enum Mode {NORMAL, DEGRADED, CRITICAL}

    private final CantalePlugin plugin;
    private BukkitTask task;
    private BukkitTask healthLogTask;

    private boolean enabled;
    private int checkIntervalSeconds;
    private long minModeMillis;
    private long recoveryMillis;
    private double degradedTps;
    private double degradedMspt;
    private double criticalTps;
    private double criticalMspt;
    private int simDropDegraded;
    private int simDropCritical;
    private int simMin;
    private boolean viewEnabled;
    private int viewDropCritical;
    private int viewMin;
    private double spawnerMultDegraded;
    private double spawnerMultCritical;
    private int stackIntervalDegradedSeconds;
    private boolean notifyOnCritical;

    private Mode mode = Mode.NORMAL;
    /** Override manuel via /optimize mode (null = auto). */
    private Mode forcedMode = null;
    private long lastModeChangeAt = System.currentTimeMillis();
    /** Début de la période saine continue (0 = pas sain actuellement). */
    private long healthySince = 0L;
    private long lastCriticalNotifyAt = 0L;

    private final Map<UUID, Integer> baseSimDistance = new HashMap<>();
    private final Map<UUID, Integer> baseViewDistance = new HashMap<>();

    public PerformanceGovernor(CantalePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("optimization.governor.enabled", true);
        this.checkIntervalSeconds = Math.max(2, plugin.getConfig().getInt("optimization.governor.check-interval-seconds", 5));
        this.minModeMillis = Math.max(5, plugin.getConfig().getInt("optimization.governor.min-mode-seconds", 30)) * 1000L;
        this.recoveryMillis = Math.max(10, plugin.getConfig().getInt("optimization.governor.recovery-seconds", 60)) * 1000L;
        this.degradedTps = plugin.getConfig().getDouble("optimization.governor.degraded.tps-below", 18.0);
        this.degradedMspt = plugin.getConfig().getDouble("optimization.governor.degraded.mspt-above", 40.0);
        this.criticalTps = plugin.getConfig().getDouble("optimization.governor.critical.tps-below", 15.0);
        this.criticalMspt = plugin.getConfig().getDouble("optimization.governor.critical.mspt-above", 47.5);
        this.simDropDegraded = Math.max(0, plugin.getConfig().getInt("optimization.governor.sim-distance.degraded-drop", 2));
        this.simDropCritical = Math.max(0, plugin.getConfig().getInt("optimization.governor.sim-distance.critical-drop", 3));
        this.simMin = Math.max(2, plugin.getConfig().getInt("optimization.governor.sim-distance.min", 3));
        this.viewEnabled = plugin.getConfig().getBoolean("optimization.governor.view-distance.enabled", false);
        this.viewDropCritical = Math.max(0, plugin.getConfig().getInt("optimization.governor.view-distance.critical-drop", 1));
        this.viewMin = Math.max(2, plugin.getConfig().getInt("optimization.governor.view-distance.min", 6));
        this.spawnerMultDegraded = Math.max(1.0, plugin.getConfig().getDouble("optimization.governor.spawner-slowdown.degraded", 1.5));
        this.spawnerMultCritical = Math.max(1.0, plugin.getConfig().getDouble("optimization.governor.spawner-slowdown.critical", 2.0));
        this.stackIntervalDegradedSeconds = Math.max(5, plugin.getConfig().getInt("optimization.governor.stack-interval-degraded-seconds", 5));
        this.notifyOnCritical = plugin.getConfig().getBoolean("optimization.governor.notify-on-critical", true);
    }

    public void start() {
        stop();
        if (!enabled) {
            plugin.getLogger().info("[Governor] Désactivé (optimization.governor.enabled=false)");
            return;
        }
        captureBaseDistances();
        long period = checkIntervalSeconds * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        // Log de santé périodique (5 min, une ligne)
        this.healthLogTask = Bukkit.getScheduler().runTaskTimer(plugin, this::logHealth, 20L * 300, 20L * 300);
        plugin.getLogger().info("[Governor] Actif — check " + checkIntervalSeconds + "s, seuils DEGRADED TPS<"
                + degradedTps + "/MSPT>" + degradedMspt + ", CRITICAL TPS<" + criticalTps + "/MSPT>" + criticalMspt);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (healthLogTask != null) {
            healthLogTask.cancel();
            healthLogTask = null;
        }
    }

    /** Une ligne de santé toutes les 5 min : TPS, MSPT, entités, chunks, mode. */
    private void logHealth() {
        ServerOptimizer optimizer = plugin.getServerOptimizer();
        if (optimizer == null) return;
        Map<String, Object> stats = optimizer.getStats();
        plugin.getLogger().info(String.format(
                "[Santé] TPS %.1f/%.1f/%.1f (1m/5m/15m) · MSPT %.1f · entités %d · chunks %d · mémoire %d/%dMB · mode %s",
                (double) stats.getOrDefault("tps", 20.0),
                (double) stats.getOrDefault("tps_5m", 20.0),
                (double) stats.getOrDefault("tps_15m", 20.0),
                (double) stats.getOrDefault("mspt", -1.0),
                (int) stats.getOrDefault("total_entities", 0),
                (int) stats.getOrDefault("loaded_chunks", 0),
                (long) stats.getOrDefault("used_memory_mb", 0L),
                (long) stats.getOrDefault("max_memory_mb", 0L),
                mode));
    }

    /** Restaure distances de base et multiplicateurs nominaux (onDisable). */
    public void shutdown() {
        stop();
        restoreDistances();
        applySpawnerMultiplier(1.0);
        applyStackInterval(null);
        this.mode = Mode.NORMAL;
    }

    private void captureBaseDistances() {
        for (World world : Bukkit.getWorlds()) {
            baseSimDistance.putIfAbsent(world.getUID(), world.getSimulationDistance());
            baseViewDistance.putIfAbsent(world.getUID(), world.getViewDistance());
        }
    }

    private void tick() {
        Mode target = forcedMode != null ? forcedMode : computeDesiredMode();
        if (target == mode) return;

        long now = System.currentTimeMillis();
        if (now - lastModeChangeAt < minModeMillis) return;

        if (isEscalation(target)) {
            setMode(target);
            return;
        }
        // Désescalade : période saine continue exigée, on remonte d'un cran à la fois
        if (healthySince != 0L && now - healthySince >= recoveryMillis) {
            Mode step = (mode == Mode.CRITICAL && target == Mode.NORMAL) ? Mode.DEGRADED : target;
            setMode(step);
        }
    }

    private boolean isEscalation(Mode target) {
        return target.ordinal() > mode.ordinal();
    }

    private Mode computeDesiredMode() {
        double tps = currentTps();
        double mspt = currentMspt();
        boolean msptUsable = mspt >= 0;

        if (tps < criticalTps || (msptUsable && mspt > criticalMspt)) {
            healthySince = 0L;
            return Mode.CRITICAL;
        }
        if (tps < degradedTps || (msptUsable && mspt > degradedMspt)) {
            healthySince = 0L;
            return Mode.DEGRADED;
        }
        // Sain = nettement au-dessus des seuils (marge d'hystérésis)
        boolean healthy = tps >= degradedTps + 1.0 && (!msptUsable || mspt <= degradedMspt - 2.0);
        if (healthy) {
            if (healthySince == 0L) healthySince = System.currentTimeMillis();
        } else {
            healthySince = 0L;
        }
        return Mode.NORMAL;
    }

    private void setMode(Mode newMode) {
        Mode old = this.mode;
        this.mode = newMode;
        this.lastModeChangeAt = System.currentTimeMillis();
        applyMode(newMode);
        plugin.getLogger().info("[Governor] Mode " + old + " -> " + newMode
                + " (TPS " + String.format("%.1f", currentTps())
                + ", MSPT " + String.format("%.1f", currentMspt()) + ")");
        if (newMode == Mode.CRITICAL) {
            notifyCritical();
        }
    }

    private void applyMode(Mode m) {
        applyDistances(m);
        double spawnerMult = switch (m) {
            case CRITICAL -> spawnerMultCritical;
            case DEGRADED -> spawnerMultDegraded;
            case NORMAL -> 1.0;
        };
        applySpawnerMultiplier(spawnerMult);
        applyStackInterval(m == Mode.NORMAL ? null : stackIntervalDegradedSeconds);
        if (m == Mode.CRITICAL && plugin.getServerOptimizer() != null) {
            plugin.getServerOptimizer().emergencyCleanup();
        }
    }

    private void applyDistances(Mode m) {
        int simDrop = switch (m) {
            case CRITICAL -> simDropCritical;
            case DEGRADED -> simDropDegraded;
            case NORMAL -> 0;
        };
        int viewDrop = (viewEnabled && m == Mode.CRITICAL) ? viewDropCritical : 0;

        for (World world : Bukkit.getWorlds()) {
            int baseSim = baseSimDistance.computeIfAbsent(world.getUID(), k -> world.getSimulationDistance());
            int targetSim = Math.max(simMin, baseSim - simDrop);
            if (world.getSimulationDistance() != targetSim) {
                try {
                    world.setSimulationDistance(targetSim);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Governor] setSimulationDistance(" + targetSim + ") refusé sur "
                            + world.getName() + " : " + e.getMessage());
                }
            }

            if (!viewEnabled) continue;
            int baseView = baseViewDistance.computeIfAbsent(world.getUID(), k -> world.getViewDistance());
            // La view-distance ne descend jamais sous la simulation-distance visée
            int targetView = Math.max(Math.max(viewMin, baseView - viewDrop), targetSim);
            if (world.getViewDistance() != targetView) {
                try {
                    world.setViewDistance(targetView);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Governor] setViewDistance(" + targetView + ") refusé sur "
                            + world.getName() + " : " + e.getMessage());
                }
            }
        }
    }

    private void restoreDistances() {
        for (World world : Bukkit.getWorlds()) {
            Integer baseSim = baseSimDistance.get(world.getUID());
            if (baseSim != null && world.getSimulationDistance() != baseSim) {
                try {
                    world.setSimulationDistance(baseSim);
                } catch (Exception ignored) {
                }
            }
            Integer baseView = baseViewDistance.get(world.getUID());
            if (baseView != null && world.getViewDistance() != baseView) {
                try {
                    world.setViewDistance(baseView);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void applySpawnerMultiplier(double multiplier) {
        var spawners = plugin.getSpawnerManager();
        if (spawners != null) {
            spawners.setRateMultiplier(multiplier);
        }
    }

    private void applyStackInterval(Integer seconds) {
        MobStackManager stacks = plugin.getMobStackManager();
        if (stacks != null) {
            stacks.setIntervalOverrideSeconds(seconds);
        }
    }

    private void notifyCritical() {
        if (!notifyOnCritical) return;
        long now = System.currentTimeMillis();
        if (now - lastCriticalNotifyAt < 300_000L) return; // max 1 alerte / 5 min
        lastCriticalNotifyAt = now;

        String detail = "TPS " + String.format("%.1f", currentTps())
                + " · MSPT " + String.format("%.1f", currentMspt())
                + " · distances réduites, spawners ralentis, cleanup d'urgence";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("cantale.moderator")) {
                player.sendMessage("§8[§6Cantale§8] §cPerformance CRITICAL §7— " + detail);
            }
        }
        if (plugin.getDiscordBot() != null) {
            try {
                long channelId = plugin.getDiscordBot().getConfig().getModLogsChannelId();
                if (channelId != 0L) {
                    plugin.getDiscordBot().sendEmbedToChannel(channelId, new Color(0xE74C3C),
                            "Performance serveur CRITICAL", detail);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Governor] Alerte Discord impossible : " + e.getMessage());
            }
        }
    }

    private double currentTps() {
        return plugin.getServerOptimizer() != null ? plugin.getServerOptimizer().getTPS() : 20.0;
    }

    private double currentMspt() {
        return plugin.getServerOptimizer() != null ? plugin.getServerOptimizer().getAverageTickTime() : -1;
    }

    /** Force un mode (null = retour en auto). Application immédiate. */
    public void setForcedMode(Mode forced) {
        this.forcedMode = forced;
        Mode target = forced != null ? forced : computeDesiredMode();
        if (target != this.mode) {
            setMode(target);
        }
    }

    public Mode getForcedMode() {
        return forcedMode;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isRunning() {
        return task != null;
    }

    /** Résumé multi-lignes pour /optimize governor. */
    public String[] getStatusLines() {
        int baseSim = baseSimDistance.values().stream().findFirst().orElse(-1);
        int baseView = baseViewDistance.values().stream().findFirst().orElse(-1);
        return new String[]{
                "&7État : " + (isRunning() ? "&aactif" : "&cinactif")
                        + (forcedMode != null ? " &8(&eforcé " + forcedMode + "&8)" : " &8(&7auto&8)"),
                "&7Mode courant : " + modeColor(mode) + mode,
                "&7Seuils : &eDEGRADED &7TPS<" + degradedTps + " ou MSPT>" + degradedMspt
                        + " · &cCRITICAL &7TPS<" + criticalTps + " ou MSPT>" + criticalMspt,
                "&7Distances base : &esim " + (baseSim < 0 ? "?" : baseSim)
                        + " / view " + (baseView < 0 ? "?" : baseView)
                        + " &8(view dynamique : " + (viewEnabled ? "on" : "off") + ")",
                "&7Spawners : &ex" + (mode == Mode.CRITICAL ? spawnerMultCritical
                        : mode == Mode.DEGRADED ? spawnerMultDegraded : 1.0) + " &7intervalle en mode dégradé+"
        };
    }

    private String modeColor(Mode m) {
        return switch (m) {
            case NORMAL -> "&a";
            case DEGRADED -> "&e";
            case CRITICAL -> "&c";
        };
    }
}
