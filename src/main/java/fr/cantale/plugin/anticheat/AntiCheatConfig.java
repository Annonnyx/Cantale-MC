package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.check.CheckType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Charge et expose la configuration anti-cheat depuis config.yml (section "anticheat").
 * Tous les paramètres sont accessibles via des getters typés.
 *
 * Gère aussi les overrides runtime (toggle /ac, fallback natif quand Grim est absent).
 * Ces overrides sont volontairement non persistés sur disque.
 */
public class AntiCheatConfig {

    private final CantalePlugin plugin;

    // ── Global ──
    private boolean enabled;
    private String bypassPermission;
    private String alertsPermission;

    // ── Trust ──
    private int defaultTrust;
    private int recoveryPerMinute;
    private int warnThreshold;
    private int kickThreshold;
    private int banDurationMinutes;
    private boolean autoPunish;

    // ── Sanctions progressives ──
    private int kickCooldownMinutes;
    private int maxTrustLossPerMinute;
    private boolean banAuto;
    private int kicksForTempban;

    // ── Compensation lag ──
    private int lagMaxPingMs;
    private double lagMinTps;
    private long velocityExemptMs;

    // ── Alertes ──
    private int alertCooldownSeconds;
    private boolean discordEnabled;
    private String discordChannelId;

    // ── Intégration Grim ──
    private boolean grimIntegrationEnabled;
    private int grimDefaultTrustPenalty;
    private Map<String, Integer> grimTrustPenalties = new HashMap<>();

    // ── Movement checks ──
    private boolean speedEnabled;
    private double maxSpeed;
    private int speedBuffer;
    private int speedTrustPenalty;

    private boolean flyEnabled;
    private int maxAirTicks;
    private int flyTrustPenalty;

    private boolean noFallEnabled;
    private double minFallDistance;
    private int noFallTrustPenalty;

    private boolean stepEnabled;
    private double maxStepHeight;
    private int stepTrustPenalty;

    private boolean timerEnabled;
    private int maxPacketsPerSecond;
    private int timerTrustPenalty;

    // ── Combat checks ──
    private boolean reachEnabled;
    private double maxReach;
    private int reachTrustPenalty;

    private boolean killAuraEnabled;
    private int minTargetSwitchTicks;
    private int killAuraTrustPenalty;

    private boolean autoClickerEnabled;
    private int maxCPS;
    private int cpsWindowMs;
    private int autoClickerBuffer;
    private int autoClickerTrustPenalty;

    private boolean aimEnabled;
    private double minRotationDeviation;
    private int aimTrustPenalty;

    // ── Rotation ──
    private boolean rotationEnabled;
    private double snapAngleThreshold;
    private int rotationTrustPenalty;

    // ── Inventory checks ──
    private boolean autoArmorEnabled;
    private int autoArmorTrustPenalty;

    private boolean chestStealerEnabled;
    private int chestStealerTrustPenalty;

    private boolean fastUseEnabled;
    private int fastUseTrustPenalty;

    // ── World / FastBreak ──
    private boolean fastBreakEnabled;
    private int maxSolidBreaks;
    private int fastBreakWindowMs;
    private int fastBreakBuffer;
    private int fastBreakTrustPenalty;

    // ── Packets ──
    private boolean packetsEnabled;

    // ── Runtime (non persisté) ──
    /** Checks désactivés à chaud via /ac toggle ou par la détection de Grim. */
    private final Set<CheckType> runtimeDisabled = EnumSet.noneOf(CheckType.class);
    /** Checks réactivés à chaud (fallback quand Grim est absent). */
    private final Set<CheckType> runtimeEnabled = EnumSet.noneOf(CheckType.class);
    private volatile boolean grimDetected;
    private volatile boolean nativeFallbackActive;

    /** Checks natifs couverts par Grim (désactivés quand Grim est présent). */
    private static final Set<CheckType> GRIM_COVERED = EnumSet.of(
            CheckType.SPEED, CheckType.FLY, CheckType.NOFALL, CheckType.STEP, CheckType.TIMER,
            CheckType.REACH, CheckType.KILLAURA, CheckType.AIM, CheckType.ROTATION);

    public AntiCheatConfig(CantalePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Recharge toute la configuration anti-cheat depuis config.yml.
     * Les overrides runtime (toggles) sont préservés.
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();

        // Global
        this.enabled = config.getBoolean("anticheat.enabled", true);
        this.bypassPermission = config.getString("anticheat.bypass-permission", "cantale.anticheat.bypass");
        this.alertsPermission = config.getString("anticheat.alerts-permission", "cantale.anticheat.alerts");

        // Trust
        this.defaultTrust = config.getInt("anticheat.trust.default", 100);
        this.recoveryPerMinute = config.getInt("anticheat.trust.recovery-per-minute", 1);
        this.warnThreshold = config.getInt("anticheat.trust.warn-threshold", 50);
        this.kickThreshold = config.getInt("anticheat.trust.kick-threshold", 25);
        this.banDurationMinutes = config.getInt("anticheat.trust.ban-duration-minutes", 30);
        this.autoPunish = config.getBoolean("anticheat.trust.auto-punish", true);

        // Sanctions progressives
        this.kickCooldownMinutes = config.getInt("anticheat.punishments.kick-cooldown-minutes", 5);
        this.maxTrustLossPerMinute = config.getInt("anticheat.punishments.max-trust-loss-per-minute", 10);
        this.banAuto = config.getBoolean("anticheat.punishments.ban-auto", false);
        this.kicksForTempban = config.getInt("anticheat.punishments.kicks-for-tempban", 3);

        // Compensation lag
        this.lagMaxPingMs = config.getInt("anticheat.lag.max-ping-ms", 350);
        this.lagMinTps = config.getDouble("anticheat.lag.min-tps", 18.5);
        this.velocityExemptMs = config.getLong("anticheat.lag.velocity-exempt-ms", 500);

        // Alertes
        this.alertCooldownSeconds = config.getInt("anticheat.alerts.cooldown-seconds", 5);
        this.discordEnabled = config.getBoolean("anticheat.alerts.discord-enabled", false);
        this.discordChannelId = config.getString("anticheat.alerts.discord-channel-id", "");

        // Intégration Grim
        this.grimIntegrationEnabled = config.getBoolean("anticheat.grim-integration.enabled", true);
        this.grimDefaultTrustPenalty = config.getInt("anticheat.grim-integration.default-trust-penalty", 2);
        this.grimTrustPenalties = new HashMap<>();
        ConfigurationSection grimSection = config.getConfigurationSection("anticheat.grim-integration.trust-penalty-per-check");
        if (grimSection != null) {
            for (String key : grimSection.getKeys(false)) {
                this.grimTrustPenalties.put(key.toLowerCase(), grimSection.getInt(key));
            }
        }

        // Movement — Speed
        this.speedEnabled = config.getBoolean("anticheat.movement.speed.enabled", true);
        this.maxSpeed = config.getDouble("anticheat.movement.speed.max-speed", 0.50);
        this.speedBuffer = config.getInt("anticheat.movement.speed.buffer", 5);
        this.speedTrustPenalty = config.getInt("anticheat.movement.speed.trust-penalty", 3);

        // Movement — Fly
        this.flyEnabled = config.getBoolean("anticheat.movement.fly.enabled", true);
        this.maxAirTicks = config.getInt("anticheat.movement.fly.max-air-ticks", 40);
        this.flyTrustPenalty = config.getInt("anticheat.movement.fly.trust-penalty", 5);

        // Movement — NoFall
        this.noFallEnabled = config.getBoolean("anticheat.movement.nofall.enabled", true);
        this.minFallDistance = config.getDouble("anticheat.movement.nofall.min-fall-distance", 3.0);
        this.noFallTrustPenalty = config.getInt("anticheat.movement.nofall.trust-penalty", 4);

        // Movement — Step
        this.stepEnabled = config.getBoolean("anticheat.movement.step.enabled", true);
        this.maxStepHeight = config.getDouble("anticheat.movement.step.max-step-height", 0.6);
        this.stepTrustPenalty = config.getInt("anticheat.movement.step.trust-penalty", 3);

        // Movement — Timer
        this.timerEnabled = config.getBoolean("anticheat.movement.timer.enabled", false);
        this.maxPacketsPerSecond = config.getInt("anticheat.movement.timer.max-packets-per-second", 25);
        this.timerTrustPenalty = config.getInt("anticheat.movement.timer.trust-penalty", 4);

        // Combat — Reach
        this.reachEnabled = config.getBoolean("anticheat.combat.reach.enabled", true);
        this.maxReach = config.getDouble("anticheat.combat.reach.max-reach", 3.15);
        this.reachTrustPenalty = config.getInt("anticheat.combat.reach.trust-penalty", 4);

        // Combat — KillAura
        this.killAuraEnabled = config.getBoolean("anticheat.combat.killaura.enabled", true);
        this.minTargetSwitchTicks = config.getInt("anticheat.combat.killaura.min-target-switch-ticks", 3);
        this.killAuraTrustPenalty = config.getInt("anticheat.combat.killaura.trust-penalty", 5);

        // Combat — AutoClicker
        // max-cps mesuré sur cps-window-ms (défaut 2s) ; clics dig soft exclus en code
        this.autoClickerEnabled = config.getBoolean("anticheat.combat.autoclicker.enabled", true);
        this.maxCPS = config.getInt("anticheat.combat.autoclicker.max-cps", 24);
        this.cpsWindowMs = config.getInt("anticheat.combat.autoclicker.cps-window-ms", 2000);
        this.autoClickerBuffer = config.getInt("anticheat.combat.autoclicker.buffer", 6);
        this.autoClickerTrustPenalty = config.getInt("anticheat.combat.autoclicker.trust-penalty", 2);

        // Combat — Aim
        this.aimEnabled = config.getBoolean("anticheat.combat.aim.enabled", true);
        this.minRotationDeviation = config.getDouble("anticheat.combat.aim.min-rotation-deviation", 0.25);
        this.aimTrustPenalty = config.getInt("anticheat.combat.aim.trust-penalty", 3);

        // Rotation
        this.rotationEnabled = config.getBoolean("anticheat.rotation.enabled", true);
        this.snapAngleThreshold = config.getDouble("anticheat.rotation.snap-angle-threshold", 5.0);
        this.rotationTrustPenalty = config.getInt("anticheat.rotation.trust-penalty", 2);

        // Inventory — AutoArmor
        this.autoArmorEnabled = config.getBoolean("anticheat.inventory.autoarmor.enabled", false);
        this.autoArmorTrustPenalty = config.getInt("anticheat.inventory.autoarmor.trust-penalty", 2);

        // Inventory — ChestStealer
        this.chestStealerEnabled = config.getBoolean("anticheat.inventory.cheststealer.enabled", false);
        this.chestStealerTrustPenalty = config.getInt("anticheat.inventory.cheststealer.trust-penalty", 2);

        // Inventory — FastUse
        this.fastUseEnabled = config.getBoolean("anticheat.inventory.fastuse.enabled", false);
        this.fastUseTrustPenalty = config.getInt("anticheat.inventory.fastuse.trust-penalty", 2);

        // World — FastBreak / Nuker (blocs solides uniquement ; soft/instant exclus)
        this.fastBreakEnabled = config.getBoolean("anticheat.world.fastbreak.enabled", true);
        this.maxSolidBreaks = config.getInt("anticheat.world.fastbreak.max-solid-breaks", 14);
        this.fastBreakWindowMs = config.getInt("anticheat.world.fastbreak.window-ms", 2000);
        this.fastBreakBuffer = config.getInt("anticheat.world.fastbreak.buffer", 4);
        this.fastBreakTrustPenalty = config.getInt("anticheat.world.fastbreak.trust-penalty", 2);

        // Packets
        this.packetsEnabled = config.getBoolean("anticheat.packets.enabled", false);
    }

    // ═══════════════════════════════════════════════════
    //  Runtime toggles & fallback Grim
    // ═══════════════════════════════════════════════════

    /**
     * Active/désactive un check à chaud (runtime uniquement, jamais écrit sur disque).
     */
    public void setCheckRuntimeEnabled(CheckType type, boolean active) {
        if (active) {
            runtimeDisabled.remove(type);
            runtimeEnabled.add(type);
        } else {
            runtimeEnabled.remove(type);
            runtimeDisabled.add(type);
        }
    }

    /**
     * Fallback : Grim absent → réactive à chaud les checks natifs mouvement/combat/rotation.
     */
    public void enableNativeFallback() {
        runtimeEnabled.addAll(GRIM_COVERED);
        runtimeDisabled.removeAll(GRIM_COVERED);
        this.nativeFallbackActive = true;
    }

    /**
     * Grim détecté → désactive à chaud les doublons natifs.
     */
    public void disableNativeDuplicates() {
        runtimeDisabled.addAll(GRIM_COVERED);
        runtimeEnabled.removeAll(GRIM_COVERED);
        this.nativeFallbackActive = false;
    }

    public boolean isGrimDetected() { return grimDetected; }
    public void setGrimDetected(boolean grimDetected) { this.grimDetected = grimDetected; }
    public boolean isNativeFallbackActive() { return nativeFallbackActive; }

    public static Set<CheckType> getGrimCoveredChecks() {
        return Collections.unmodifiableSet(GRIM_COVERED);
    }

    /**
     * Pénalité trust pour un flag Grim, par nom de check Grim (insensible à la casse).
     */
    public int getGrimTrustPenalty(String grimCheckName) {
        if (grimCheckName == null) return grimDefaultTrustPenalty;
        return grimTrustPenalties.getOrDefault(grimCheckName.toLowerCase(), grimDefaultTrustPenalty);
    }

    // ═══════════════════════════════════════════════════
    //  Getters dynamiques par CheckType
    // ═══════════════════════════════════════════════════

    /**
     * Vérifie si un check spécifique est activé (config + overrides runtime).
     */
    public boolean isCheckEnabled(CheckType type) {
        if (!enabled) return false;
        if (runtimeDisabled.contains(type)) return false;
        if (runtimeEnabled.contains(type)) return true;
        return switch (type) {
            case SPEED -> speedEnabled;
            case FLY -> flyEnabled;
            case NOFALL -> noFallEnabled;
            case STEP -> stepEnabled;
            case TIMER -> timerEnabled;
            case REACH -> reachEnabled;
            case KILLAURA -> killAuraEnabled;
            case AUTOCLICKER -> autoClickerEnabled;
            case AIM -> aimEnabled;
            case ROTATION -> rotationEnabled;
            case AUTO_ARMOR -> autoArmorEnabled;
            case CHEST_STEALER -> chestStealerEnabled;
            case FAST_USE -> fastUseEnabled;
            case FAST_BREAK -> fastBreakEnabled;
            case BLINK, BAD_PACKETS -> packetsEnabled;
            case GRIM -> grimIntegrationEnabled && grimDetected;
        };
    }

    /**
     * Retourne la pénalité de trust pour un type de check.
     */
    public int getTrustPenalty(CheckType type) {
        return switch (type) {
            case SPEED -> speedTrustPenalty;
            case FLY -> flyTrustPenalty;
            case NOFALL -> noFallTrustPenalty;
            case STEP -> stepTrustPenalty;
            case TIMER -> timerTrustPenalty;
            case REACH -> reachTrustPenalty;
            case KILLAURA -> killAuraTrustPenalty;
            case AUTOCLICKER -> autoClickerTrustPenalty;
            case AIM -> aimTrustPenalty;
            case ROTATION -> rotationTrustPenalty;
            case AUTO_ARMOR -> autoArmorTrustPenalty;
            case CHEST_STEALER -> chestStealerTrustPenalty;
            case FAST_USE -> fastUseTrustPenalty;
            case FAST_BREAK -> fastBreakTrustPenalty;
            case BLINK, BAD_PACKETS -> 3;
            case GRIM -> grimDefaultTrustPenalty;
        };
    }

    // ═══════════════════════════════════════════════════
    //  Getters individuels
    // ═══════════════════════════════════════════════════

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBypassPermission() { return bypassPermission; }
    public String getAlertsPermission() { return alertsPermission; }

    public int getDefaultTrust() { return defaultTrust; }
    public int getRecoveryPerMinute() { return recoveryPerMinute; }
    public int getWarnThreshold() { return warnThreshold; }
    public int getKickThreshold() { return kickThreshold; }
    public int getBanDurationMinutes() { return banDurationMinutes; }
    public boolean isAutoPunish() { return autoPunish; }

    public int getKickCooldownMinutes() { return kickCooldownMinutes; }
    public int getMaxTrustLossPerMinute() { return maxTrustLossPerMinute; }
    public boolean isBanAuto() { return banAuto; }
    public int getKicksForTempban() { return kicksForTempban; }

    public int getLagMaxPingMs() { return lagMaxPingMs; }
    public double getLagMinTps() { return lagMinTps; }
    public long getVelocityExemptMs() { return velocityExemptMs; }

    public int getAlertCooldownSeconds() { return alertCooldownSeconds; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordChannelId() { return discordChannelId; }

    public boolean isGrimIntegrationEnabled() { return grimIntegrationEnabled; }
    public int getGrimDefaultTrustPenalty() { return grimDefaultTrustPenalty; }

    public double getMaxSpeed() { return maxSpeed; }
    public int getSpeedBuffer() { return speedBuffer; }
    public int getMaxAirTicks() { return maxAirTicks; }
    public double getMinFallDistance() { return minFallDistance; }
    public double getMaxStepHeight() { return maxStepHeight; }
    public int getMaxPacketsPerSecond() { return maxPacketsPerSecond; }

    public double getMaxReach() { return maxReach; }
    public int getMinTargetSwitchTicks() { return minTargetSwitchTicks; }
    public int getMaxCPS() { return maxCPS; }
    public int getCpsWindowMs() { return cpsWindowMs; }
    public int getAutoClickerBuffer() { return autoClickerBuffer; }
    public double getMinRotationDeviation() { return minRotationDeviation; }

    public double getSnapAngleThreshold() { return snapAngleThreshold; }

    public int getMaxSolidBreaks() { return maxSolidBreaks; }
    public int getFastBreakWindowMs() { return fastBreakWindowMs; }
    public int getFastBreakBuffer() { return fastBreakBuffer; }
}
