package fr.cantale.plugin.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeSession {

    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private final Map<Integer, ItemStack> player1Items;
    private final Map<Integer, ItemStack> player2Items;
    private double player1Money;
    private double player2Money;
    private boolean player1Accepted;
    private boolean player2Accepted;
    private final long createdAt;
    private long lastActivity;

    public TradeSession(UUID player1Uuid, UUID player2Uuid) {
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
        this.player1Items = new HashMap<>();
        this.player2Items = new HashMap<>();
        this.player1Money = 0;
        this.player2Money = 0;
        this.player1Accepted = false;
        this.player2Accepted = false;
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

    public Map<Integer, ItemStack> getPlayerItems(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player1Items;
        if (player2Uuid.equals(uuid)) return player2Items;
        return null;
    }

    public void addItem(UUID uuid, int slot, ItemStack item) {
        if (player1Uuid.equals(uuid)) {
            player1Items.put(slot, item.clone());
        } else if (player2Uuid.equals(uuid)) {
            player2Items.put(slot, item.clone());
        }
        updateActivity();
    }

    public void removeItem(UUID uuid, int slot) {
        if (player1Uuid.equals(uuid)) {
            player1Items.remove(slot);
        } else if (player2Uuid.equals(uuid)) {
            player2Items.remove(slot);
        }
        updateActivity();
    }

    public void clearItems(UUID uuid) {
        if (player1Uuid.equals(uuid)) {
            player1Items.clear();
        } else if (player2Uuid.equals(uuid)) {
            player2Items.clear();
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

    public boolean isAccepted(UUID uuid) {
        if (player1Uuid.equals(uuid)) return player1Accepted;
        if (player2Uuid.equals(uuid)) return player2Accepted;
        return false;
    }

    public void setAccepted(UUID uuid, boolean accepted) {
        if (player1Uuid.equals(uuid)) player1Accepted = accepted;
        else if (player2Uuid.equals(uuid)) player2Accepted = accepted;
        updateActivity();
    }

    public void resetAcceptance() {
        setAccepted(player1Uuid, false);
        setAccepted(player2Uuid, false);
        updateActivity();
    }

    public boolean bothAccepted() {
        return player1Accepted && player2Accepted;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    private void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity > timeoutMs;
    }
}
