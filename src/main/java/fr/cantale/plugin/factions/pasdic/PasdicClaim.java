package fr.cantale.plugin.factions.pasdic;

import fr.cantale.plugin.factions.Claim;
import java.util.UUID;

public class PasdicClaim {
    private final int claimId;
    private final UUID approvedBy;
    private final long approvedAt;
    private boolean active;

    public PasdicClaim(int claimId, UUID approvedBy, long approvedAt, boolean active) {
        this.claimId = claimId;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.active = active;
    }

    public int getClaimId() { return claimId; }
    public UUID getApprovedBy() { return approvedBy; }
    public long getApprovedAt() { return approvedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
