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
    /** Compteur de packets de mouvement dans la fenêtre actuelle */
    private int movePacketCount;
    /** Timestamp du début de la fenêtre de comptage des packets */
    private long movePacketWindowStart;

    // ── Combat ──
    /** Timestamps des clics (pour CPS) */
    private final List<Long> clickTimestamps = new ArrayList<>();
    /** UUID de la dernière entité attaquée */
    private UUID lastTargetId;
    /** Timestamp de la dernière attaque */
    private long lastAttackTimestamp;
    /** Nombre de changements de cible rapides */
    private int rapidTargetSwitches;

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
        clickTimestamps.add(timestamp);
        // Nettoyer les clics vieux de plus de 2 secondes
        clickTimestamps.removeIf(t -> timestamp - t > 2000);
    }

    /**
     * Calcule le CPS sur la dernière seconde.
     */
    public int getCPS() {
        long now = System.currentTimeMillis();
        return (int) clickTimestamps.stream().filter(t -> now - t <= 1000).count();
    }

    /**
     * Calcule l'écart-type des intervalles entre clics (en ms).
     * Un écart-type très faible indique un autoclicker.
     */
    public double getClickDeviation() {
        if (clickTimestamps.size() < 3) return Double.MAX_VALUE;

        List<Long> sorted = new ArrayList<>(clickTimestamps);
        sorted.sort(Long::compareTo);

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            intervals.add(sorted.get(i) - sorted.get(i - 1));
        }

        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = intervals.stream()
                .mapToDouble(i -> Math.pow(i - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
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
    //  Utilitaire
    // ═══════════════════════════════════════════════════

    public UUID getPlayerId() {
        return playerId;
    }
}
