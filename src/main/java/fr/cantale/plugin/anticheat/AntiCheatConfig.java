package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.check.CheckType;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Charge et expose la configuration anti-cheat depuis config.yml (section "anticheat").
 * Tous les paramètres sont accessibles via des getters typés.
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
    private int banThreshold;
    private int banDurationMinutes;
    private boolean autoPunish;

    // ── Alertes ──
    private int alertCooldownSeconds;
    private boolean discordEnabled;
    private String discordChannelId;

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
    private int minEquipTicks;
    private int autoArmorTrustPenalty;

    private boolean chestStealerEnabled;
    private int minClickTicks;
    private int chestStealerTrustPenalty;

    private boolean fastUseEnabled;
    private int fastUseTrustPenalty;

    // ── Packets ──
    private boolean packetsEnabled;

    public AntiCheatConfig(CantalePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Recharge toute la configuration anti-cheat depuis config.yml.
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
        this.banThreshold = config.getInt("anticheat.trust.ban-threshold", 10);
        this.banDurationMinutes = config.getInt("anticheat.trust.ban-duration-minutes", 30);
        this.autoPunish = config.getBoolean("anticheat.trust.auto-punish", false);

        // Alertes
        this.alertCooldownSeconds = config.getInt("anticheat.alerts.cooldown-seconds", 5);
        this.discordEnabled = config.getBoolean("anticheat.alerts.discord-enabled", false);
        this.discordChannelId = config.getString("anticheat.alerts.discord-channel-id", "");

        // Movement — Speed
        this.speedEnabled = config.getBoolean("anticheat.movement.speed.enabled", true);
        this.maxSpeed = config.getDouble("anticheat.movement.speed.max-speed", 0.45);
        this.speedBuffer = config.getInt("anticheat.movement.speed.buffer", 3);
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
        this.timerEnabled = config.getBoolean("anticheat.movement.timer.enabled", true);
        this.maxPacketsPerSecond = config.getInt("anticheat.movement.timer.max-packets-per-second", 22);
        this.timerTrustPenalty = config.getInt("anticheat.movement.timer.trust-penalty", 4);

        // Combat — Reach
        this.reachEnabled = config.getBoolean("anticheat.combat.reach.enabled", true);
        this.maxReach = config.getDouble("anticheat.combat.reach.max-reach", 3.5);
        this.reachTrustPenalty = config.getInt("anticheat.combat.reach.trust-penalty", 4);

        // Combat — KillAura
        this.killAuraEnabled = config.getBoolean("anticheat.combat.killaura.enabled", true);
        this.minTargetSwitchTicks = config.getInt("anticheat.combat.killaura.min-target-switch-ticks", 3);
        this.killAuraTrustPenalty = config.getInt("anticheat.combat.killaura.trust-penalty", 5);

        // Combat — AutoClicker
        this.autoClickerEnabled = config.getBoolean("anticheat.combat.autoclicker.enabled", true);
        this.maxCPS = config.getInt("anticheat.combat.autoclicker.max-cps", 20);
        this.autoClickerTrustPenalty = config.getInt("anticheat.combat.autoclicker.trust-penalty", 3);

        // Combat — Aim
        this.aimEnabled = config.getBoolean("anticheat.combat.aim.enabled", true);
        this.minRotationDeviation = config.getDouble("anticheat.combat.aim.min-rotation-deviation", 1.5);
        this.aimTrustPenalty = config.getInt("anticheat.combat.aim.trust-penalty", 3);

        // Rotation
        this.rotationEnabled = config.getBoolean("anticheat.rotation.enabled", true);
        this.snapAngleThreshold = config.getDouble("anticheat.rotation.snap-angle-threshold", 5.0);
        this.rotationTrustPenalty = config.getInt("anticheat.rotation.trust-penalty", 2);

        // Inventory — AutoArmor
        this.autoArmorEnabled = config.getBoolean("anticheat.inventory.autoarmor.enabled", false);
        this.minEquipTicks = config.getInt("anticheat.inventory.autoarmor.min-equip-ticks", 2);
        this.autoArmorTrustPenalty = config.getInt("anticheat.inventory.autoarmor.trust-penalty", 2);

        // Inventory — ChestStealer
        this.chestStealerEnabled = config.getBoolean("anticheat.inventory.cheststealer.enabled", false);
        this.minClickTicks = config.getInt("anticheat.inventory.cheststealer.min-click-ticks", 1);
        this.chestStealerTrustPenalty = config.getInt("anticheat.inventory.cheststealer.trust-penalty", 2);

        // Inventory — FastUse
        this.fastUseEnabled = config.getBoolean("anticheat.inventory.fastuse.enabled", false);
        this.fastUseTrustPenalty = config.getInt("anticheat.inventory.fastuse.trust-penalty", 2);

        // Packets
        this.packetsEnabled = config.getBoolean("anticheat.packets.enabled", false);
    }

    // ═══════════════════════════════════════════════════
    //  Getters dynamiques par CheckType
    // ═══════════════════════════════════════════════════

    /**
     * Vérifie si un check spécifique est activé.
     */
    public boolean isCheckEnabled(CheckType type) {
        if (!enabled) return false;
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
            case BLINK, BAD_PACKETS -> packetsEnabled;
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
            case BLINK, BAD_PACKETS -> 3;
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
    public int getBanThreshold() { return banThreshold; }
    public int getBanDurationMinutes() { return banDurationMinutes; }
    public boolean isAutoPunish() { return autoPunish; }

    public int getAlertCooldownSeconds() { return alertCooldownSeconds; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordChannelId() { return discordChannelId; }

    public double getMaxSpeed() { return maxSpeed; }
    public int getSpeedBuffer() { return speedBuffer; }
    public int getMaxAirTicks() { return maxAirTicks; }
    public double getMinFallDistance() { return minFallDistance; }
    public double getMaxStepHeight() { return maxStepHeight; }
    public int getMaxPacketsPerSecond() { return maxPacketsPerSecond; }

    public double getMaxReach() { return maxReach; }
    public int getMinTargetSwitchTicks() { return minTargetSwitchTicks; }
    public int getMaxCPS() { return maxCPS; }
    public double getMinRotationDeviation() { return minRotationDeviation; }

    public double getSnapAngleThreshold() { return snapAngleThreshold; }

    public int getMinEquipTicks() { return minEquipTicks; }
    public int getMinClickTicks() { return minClickTicks; }
}
