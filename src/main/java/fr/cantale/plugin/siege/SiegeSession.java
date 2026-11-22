package fr.cantale.plugin.siege;

/**
 * Siège chargé en mémoire. Les blocs cassés vivent dans {@code siege_block_changes}.
 */
public final class SiegeSession {

    private final int id;
    private final int attackerFactionId;
    private final int defenderFactionId;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final long startedAt;
    private final long endsAt;
    private long regenAt;
    private String status;

    public SiegeSession(int id, int attackerFactionId, int defenderFactionId, String world,
                        int x, int y, int z, long startedAt, long endsAt, long regenAt, String status) {
        this.id = id;
        this.attackerFactionId = attackerFactionId;
        this.defenderFactionId = defenderFactionId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.regenAt = regenAt;
        this.status = status;
    }

    public int id() {
        return id;
    }

    public int attackerFactionId() {
        return attackerFactionId;
    }

    public int defenderFactionId() {
        return defenderFactionId;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public long startedAt() {
        return startedAt;
    }

    public long endsAt() {
        return endsAt;
    }

    public long regenAt() {
        return regenAt;
    }

    public String status() {
        return status;
    }

    public boolean active() {
        return SiegeRules.ACTIVE.equals(status);
    }

    public boolean busy() {
        return !SiegeRules.DONE.equals(status);
    }

    public void markEnded(long regenAt) {
        this.status = SiegeRules.ENDED;
        this.regenAt = regenAt;
    }

    public void markRegenerating() {
        this.status = SiegeRules.REGENERATING;
    }

    public void markDone() {
        this.status = SiegeRules.DONE;
    }
}
