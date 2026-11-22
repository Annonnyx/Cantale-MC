package fr.cantale.plugin.skills;

/**
 * Un métier par joueur. Le premier choix est libre.
 * Un changement exige le délai et le solde, et échoue en combat.
 */
public final class SkillChangeRules {

    public enum Deny {
        NONE,
        COMBAT,
        SAME,
        COOLDOWN,
        FUNDS
    }

    private SkillChangeRules() {
    }

    public static Deny evaluate(boolean inCombat, boolean hasSkill, boolean sameSkill,
                                 long nowSeconds, long changedAtSeconds, double balance) {
        if (inCombat) {
            return Deny.COMBAT;
        }
        if (sameSkill) {
            return Deny.SAME;
        }
        if (!hasSkill) {
            return Deny.NONE;
        }
        if (!SkillFormulas.changeCooldownElapsed(nowSeconds, changedAtSeconds)) {
            return Deny.COOLDOWN;
        }
        if (balance < SkillFormulas.CHANGE_COST) {
            return Deny.FUNDS;
        }
        return Deny.NONE;
    }
}
