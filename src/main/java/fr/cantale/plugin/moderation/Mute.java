package fr.cantale.plugin.moderation;

import java.util.UUID;

public class Mute {
    private final UUID playerUuid;
    private final String playerName;
    private final long mutedBy;
    private final String reason;
    private final long duration;
    private final long mutedAt;

    public Mute(UUID playerUuid, String playerName, long mutedBy, String reason, long duration, long mutedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.mutedBy = mutedBy;
        this.reason = reason;
        this.duration = duration;
        this.mutedAt = mutedAt;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getMutedBy() {
        return mutedBy;
    }

    public String getReason() {
        return reason;
    }

    public long getDuration() {
        return duration;
    }

    public long getMutedAt() {
        return mutedAt;
    }

    public long getExpiry() {
        return mutedAt + duration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 > getExpiry();
    }

    public boolean isPermanent() {
        return duration == -1;
    }
}
