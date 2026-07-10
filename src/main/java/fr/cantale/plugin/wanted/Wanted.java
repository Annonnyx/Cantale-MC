package fr.cantale.plugin.wanted;

import java.util.UUID;

public class Wanted {
    private final int id;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID issuerUuid;
    private final String issuerName;
    private final double reward;
    private final long issuedAt;
    private final String reason;
    private boolean active;

    public Wanted(int id, UUID targetUuid, String targetName, UUID issuerUuid, String issuerName, 
                  double reward, long issuedAt, String reason, boolean active) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.issuerUuid = issuerUuid;
        this.issuerName = issuerName;
        this.reward = reward;
        this.issuedAt = issuedAt;
        this.reason = reason;
        this.active = active;
    }

    public int getId() { return id; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public UUID getIssuerUuid() { return issuerUuid; }
    public String getIssuerName() { return issuerName; }
    public double getReward() { return reward; }
    public long getIssuedAt() { return issuedAt; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
