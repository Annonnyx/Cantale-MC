package fr.cantale.plugin.anticheat.data;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Stocke les données anti-cheat d'un joueur.
 * Inclut : historique de positions, timings, CPS, rotations.
 * Chaque instance est liée à un joueur et nettoyée à la déconnexion.
 */
public class PlayerData {

    private final UUID playerId;

    // ── Mouvement ──
    /** Historique des 20 dernières positions */
    private final Deque<Location> positionHistory = new ArrayDeque<>(20);
    private Location lastLocation;
    private long lastMoveTimestamp;
    /** Nombre de ticks consécutifs en l'air */
    private int airTicks;
    /** Dernier Y lorsque le joueur était au sol */
    private double lastGroundY;
    /** Le joueur était-il au sol au dernier tick */
    private boolean wasOnGround;
    /** Compteur de violations consécutives pour le speed check (buffer) */
    private int speedBuffer;
    /** Compteur de violations consécutives pour le timer check (buffer) */
    private int timerBuffer;
    /** Compteur de packets de mouvement dans la fenêtre actuelle */
    private int movePacketCount;
    /** Timestamp du début de la fenêtre de comptage des packets */
    private long movePacketWindowStart;

    // ── Combat ──
    /** Timestamps des clics (pour CPS) */
    private final List<Long> clickTimestamps = new ArrayList<>();
    /** Buffer de violations AutoClicker consécutives */
    private int autoClickerBuffer;
    /** UUID de la dernière entité attaquée */
    private UUID lastTargetId;
    /** Timestamp de la dernière attaque */
    private long lastAttackTimestamp;
    /** Nombre de changements de cible rapides */
    private int rapidTargetSwitches;

    // ── Casse de blocs (FastBreak / Nuker) ──
    /** Timestamps des casses de blocs solides (non-instant) */
    private final List<Long> solidBreakTimestamps = new ArrayList<>();
    /** Buffer de violations FastBreak consécutives */
    private int fastBreakBuffer;

    // ── Rotation ──
    /** Historique des dernières rotations yaw */
    private final Deque<Float> yawHistory = new ArrayDeque<>(20);
    /** Historique des dernières rotations pitch */
    private final Deque<Float> pitchHistory = new ArrayDeque<>(20);
    private float lastYaw;
    private float lastPitch;

    // ── Inventaire ──
    /** Timestamp du dernier clic dans un inventaire */
    private long lastInventoryClick;
    /** Timestamp du dernier équipement d'armure */
    private long lastArmorEquip;

    // ── NoFall ──
    /** Distance de chute calculée manuellement */
    private double fallDistance;
    /** Le joueur est-il en train de tomber */
    private boolean falling;

    // ── Exemptions / buffers ──
    /** Jusqu'à quand ignorer les checks de mouvement (après TP, etc.) */
    private long movementExemptUntil;
    /** Buffer de violations Aim consécutives */
    private int aimBuffer;
    /** Buffer de violations Reach consécutives */
    private int reachBuffer;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        this.lastMoveTimestamp = System.currentTimeMillis();
        this.movePacketWindowStart = System.currentTimeMillis();
        this.wasOnGround = true;
        this.lastGroundY = 0;
    }

    // ═══════════════════════════════════════════════════
    //  Mouvement
    // ═══════════════════════════════════════════════════

    public void addPosition(Location location) {
        if (positionHistory.size() >= 20) {
            positionHistory.pollFirst();
        }
        positionHistory.addLast(location.clone());
        this.lastLocation = location.clone();
        this.lastMoveTimestamp = System.currentTimeMillis();
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public Deque<Location> getPositionHistory() {
        return positionHistory;
    }

    public long getLastMoveTimestamp() {
        return lastMoveTimestamp;
    }

    public int getAirTicks() {
        return airTicks;
    }

    public void setAirTicks(int airTicks) {
        this.airTicks = airTicks;
    }

    public void incrementAirTicks() {
        this.airTicks++;
    }

    public double getLastGroundY() {
        return lastGroundY;
    }

    public void setLastGroundY(double lastGroundY) {
        this.lastGroundY = lastGroundY;
    }

    public boolean wasOnGround() {
        return wasOnGround;
    }

    public void setWasOnGround(boolean wasOnGround) {
        this.wasOnGround = wasOnGround;
    }

    public int getSpeedBuffer() {
        return speedBuffer;
    }

    public void setSpeedBuffer(int speedBuffer) {
        this.speedBuffer = speedBuffer;
    }

    public void incrementSpeedBuffer() {
        this.speedBuffer++;
    }

    public void resetSpeedBuffer() {
        this.speedBuffer = 0;
    }

    public int getTimerBuffer() {
        return timerBuffer;
    }

    public void incrementTimerBuffer() {
        this.timerBuffer++;
    }

    public void resetTimerBuffer() {
        this.timerBuffer = 0;
    }

    public int getMovePacketCount() {
        return movePacketCount;
    }

    public void incrementMovePacketCount() {
        this.movePacketCount++;
    }

    public void resetMovePacketCount() {
        this.movePacketCount = 0;
        this.movePacketWindowStart = System.currentTimeMillis();
    }

    public long getMovePacketWindowStart() {
        return movePacketWindowStart;
    }

    // ═══════════════════════════════════════════════════
    //  Combat
    // ═══════════════════════════════════════════════════

    public void addClick(long timestamp) {
        // Débounce : même swing peut arriver via Interact + EntityDamage
        if (!clickTimestamps.isEmpty()) {
            long last = clickTimestamps.get(clickTimestamps.size() - 1);
            if (timestamp - last < 35L) {
                return;
            }
        }
        clickTimestamps.add(timestamp);
        // Garder un historique un peu plus long que la fenêtre CPS max
        clickTimestamps.removeIf(t -> timestamp - t > 4000);
    }

    /**
     * Calcule le CPS moyen sur la fenêtre donnée (ms), normalisé en clics/seconde.
     */
    public int getCPS(int windowMs) {
        long now = System.currentTimeMillis();
        int window = Math.max(250, windowMs);
        long count = clickTimestamps.stream().filter(t -> now - t <= window).count();
        return (int) Math.round(count * 1000.0 / window);
    }

    /**
     * Calcule le CPS sur la dernière seconde (compat).
     */
    public int getCPS() {
        return getCPS(1000);
    }

    /**
     * Calcule l'écart-type des intervalles entre clics (en ms) sur la fenêtre donnée.
     * Un écart-type très faible indique un autoclicker.
     */
    public double getClickDeviation(int windowMs) {
        long now = System.currentTimeMillis();
        int window = Math.max(250, windowMs);
        List<Long> recent = clickTimestamps.stream()
                .filter(t -> now - t <= window)
                .sorted()
                .toList();

        if (recent.size() < 5) return Double.MAX_VALUE;

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            intervals.add(recent.get(i) - recent.get(i - 1));
        }

        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = intervals.stream()
                .mapToDouble(i -> Math.pow(i - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    public double getClickDeviation() {
        return getClickDeviation(2000);
    }

    public int getAutoClickerBuffer() {
        return autoClickerBuffer;
    }

    public void incrementAutoClickerBuffer() {
        this.autoClickerBuffer++;
    }

    public void resetAutoClickerBuffer() {
        this.autoClickerBuffer = 0;
    }

    public void addSolidBreak(long timestamp, int retainMs) {
        solidBreakTimestamps.add(timestamp);
        int retain = Math.max(1000, retainMs);
        solidBreakTimestamps.removeIf(t -> timestamp - t > retain);
    }

    public int getSolidBreakCount(long now, int windowMs) {
        int window = Math.max(250, windowMs);
        return (int) solidBreakTimestamps.stream().filter(t -> now - t <= window).count();
    }

    public int getFastBreakBuffer() {
        return fastBreakBuffer;
    }

    public void incrementFastBreakBuffer() {
        this.fastBreakBuffer++;
    }

    public void resetFastBreakBuffer() {
        this.fastBreakBuffer = 0;
    }

    public UUID getLastTargetId() {
        return lastTargetId;
    }

    public void setLastTargetId(UUID lastTargetId) {
        this.lastTargetId = lastTargetId;
    }

    public long getLastAttackTimestamp() {
        return lastAttackTimestamp;
    }

    public void setLastAttackTimestamp(long lastAttackTimestamp) {
        this.lastAttackTimestamp = lastAttackTimestamp;
    }

    public int getRapidTargetSwitches() {
        return rapidTargetSwitches;
    }

    public void setRapidTargetSwitches(int rapidTargetSwitches) {
        this.rapidTargetSwitches = rapidTargetSwitches;
    }

    public void incrementRapidTargetSwitches() {
        this.rapidTargetSwitches++;
    }

    // ═══════════════════════════════════════════════════
    //  Rotation
    // ═══════════════════════════════════════════════════

    public void addRotation(float yaw, float pitch) {
        if (yawHistory.size() >= 20) {
            yawHistory.pollFirst();
        }
        if (pitchHistory.size() >= 20) {
            pitchHistory.pollFirst();
        }
        yawHistory.addLast(yaw);
        pitchHistory.addLast(pitch);
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public Deque<Float> getYawHistory() {
        return yawHistory;
    }

    public Deque<Float> getPitchHistory() {
        return pitchHistory;
    }

    // ═══════════════════════════════════════════════════
    //  Inventaire
    // ═══════════════════════════════════════════════════

    public long getLastInventoryClick() {
        return lastInventoryClick;
    }

    public void setLastInventoryClick(long lastInventoryClick) {
        this.lastInventoryClick = lastInventoryClick;
    }

    public long getLastArmorEquip() {
        return lastArmorEquip;
    }

    public void setLastArmorEquip(long lastArmorEquip) {
        this.lastArmorEquip = lastArmorEquip;
    }

    // ═══════════════════════════════════════════════════
    //  NoFall
    // ═══════════════════════════════════════════════════

    public double getFallDistance() {
        return fallDistance;
    }

    public void setFallDistance(double fallDistance) {
        this.fallDistance = fallDistance;
    }

    public boolean isFalling() {
        return falling;
    }

    public void setFalling(boolean falling) {
        this.falling = falling;
    }

    // ═══════════════════════════════════════════════════
    //  Exemptions / buffers
    // ═══════════════════════════════════════════════════

    public void exemptMovement(long durationMs) {
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        if (until > this.movementExemptUntil) {
            this.movementExemptUntil = until;
        }
    }

    public boolean isMovementExempt() {
        return System.currentTimeMillis() < movementExemptUntil;
    }

    public int getAimBuffer() {
        return aimBuffer;
    }

    public void incrementAimBuffer() {
        this.aimBuffer++;
    }

    public void resetAimBuffer() {
        this.aimBuffer = 0;
    }

    public int getReachBuffer() {
        return reachBuffer;
    }

    public void incrementReachBuffer() {
        this.reachBuffer++;
    }

    public void resetReachBuffer() {
        this.reachBuffer = 0;
    }

    // ═══════════════════════════════════════════════════
    //  Utilitaire
    // ═══════════════════════════════════════════════════

    public UUID getPlayerId() {
        return playerId;
    }
}
