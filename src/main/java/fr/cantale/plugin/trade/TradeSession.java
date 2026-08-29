package fr.cantale.plugin.trade;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session d'échange entre deux joueurs.
 * Les offres sont indexées par position dans la grille du GUI (0..OFFER_SLOTS-1).
 * L'argent n'est jamais prélevé avant la complétion : seule une valeur convenue.
 */
public class TradeSession {

    public static final int OFFER_SLOTS = 24; // 6 lignes x 4 colonnes par joueur

    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private final Map<Integer, ItemStack> player1Items = new HashMap<>();
    private final Map<Integer, ItemStack> player2Items = new HashMap<>();
    private double player1Money;
    private double player2Money;
    private boolean player1Confirmed;
    private boolean player2Confirmed;
    private final long createdAt;
    private long lastActivity;
    private long countdownEndsAt = -1; // -1 = pas de compte à rebours en cours

    public TradeSession(UUID player1Uuid, UUID player2Uuid) {
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
    }

    public UUID getPlayer1Uuid() {
        return player1Uuid;
    }

    public UUID getPlayer2Uuid() {
        return player2Uuid;
    }

    public boolean isPlayerInTrade(UUID uuid) {
        return player1Uuid.equals(uuid) || player2Uuid.equals(uuid);
    }

    public UUID getOtherPlayer(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player2Uuid;
        if (player2Uuid.equals(uuid)) return player1Uuid;
        return null;
    }

    public Map<Integer, ItemStack> getItems(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player1Items;
        if (player2Uuid.equals(uuid)) return player2Items;
        return null;
    }

    public List<ItemStack> getItemList(UUID uuid) {
        Map<Integer, ItemStack> items = getItems(uuid);
        return items == null ? new ArrayList<>() : new ArrayList<>(items.values());
    }

    /** Premier index d'offre libre pour ce joueur, -1 si sa grille est pleine. */
    public int firstFreeSlot(UUID uuid) {
        Map<Integer, ItemStack> items = getItems(uuid);
        if (items == null) return -1;
        for (int i = 0; i < OFFER_SLOTS; i++) {
            if (!items.containsKey(i)) return i;
        }
        return -1;
    }

    /** Pose (clone) ou retire (item == null) un item à l'index donné de l'offre du joueur. */
    public void setItem(UUID uuid, int index, ItemStack item) {
        if (index < 0 || index >= OFFER_SLOTS) return;
        Map<Integer, ItemStack> items = getItems(uuid);
        if (items == null) return;
        if (item == null) {
            items.remove(index);
        } else {
            items.put(index, item.clone());
        }
        updateActivity();
    }

    public double getMoney(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player1Money;
        if (player2Uuid.equals(uuid)) return player2Money;
        return 0;
    }

    public void setMoney(UUID uuid, double amount) {
        if (player1Uuid.equals(uuid)) player1Money = amount;
        else if (player2Uuid.equals(uuid)) player2Money = amount;
        updateActivity();
    }

    public boolean isConfirmed(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player1Confirmed;
        if (player2Uuid.equals(uuid)) return player2Confirmed;
        return false;
    }

    public void setConfirmed(UUID uuid, boolean confirmed) {
        if (player1Uuid.equals(uuid)) player1Confirmed = confirmed;
        else if (player2Uuid.equals(uuid)) player2Confirmed = confirmed;
        updateActivity();
    }

    public boolean bothConfirmed() {
        return player1Confirmed && player2Confirmed;
    }

    /** Toute modification d'offre annule les deux confirmations et le compte à rebours. */
    public void resetConfirmations() {
        player1Confirmed = false;
        player2Confirmed = false;
        countdownEndsAt = -1;
        updateActivity();
    }

    public void startCountdown(long durationMs) {
        this.countdownEndsAt = System.currentTimeMillis() + durationMs;
        updateActivity();
    }

    public void stopCountdown() {
        this.countdownEndsAt = -1;
    }

    public boolean isCountingDown() {
        return countdownEndsAt > 0;
    }

    public long getCountdownEndsAt() {
        return countdownEndsAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity > timeoutMs;
    }
}
