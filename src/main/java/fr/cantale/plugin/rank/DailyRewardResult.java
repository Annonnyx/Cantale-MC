package fr.cantale.plugin.rank;

public class DailyRewardResult {
    private final boolean alreadyClaimed;
    private final int amount;
    private final int streak;
    private final boolean customItemRewarded;

    public DailyRewardResult(boolean alreadyClaimed, int amount, int streak, boolean customItemRewarded) {
        this.alreadyClaimed = alreadyClaimed;
        this.amount = amount;
        this.streak = streak;
        this.customItemRewarded = customItemRewarded;
    }

    public boolean isAlreadyClaimed() {
        return alreadyClaimed;
    }

    public int getAmount() {
        return amount;
    }

    public int getStreak() {
        return streak;
    }

    public boolean isCustomItemRewarded() {
        return customItemRewarded;
    }
}
