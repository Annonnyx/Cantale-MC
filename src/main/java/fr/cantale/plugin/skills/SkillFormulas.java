package fr.cantale.plugin.skills;

/**
 * Buffs de faction. Chaque métier compte les membres qui l'ont choisi, jusqu'au plafond.
 * Aucune formule ne crée de monnaie : le négociant réduit un coût existant.
 * Le chevalier reste sous +15 % de dégâts et -10 % de dégâts reçus, en warzone seulement.
 */
public final class SkillFormulas {

    public static final int ARCHITECT_CAP = 5;
    public static final int ARCHITECT_EXTRA_BREAKS_PER_MEMBER = 1;
    /** Au-delà, le siège ne gagne plus de solidité. 3 extras → 4 tentatives pour casser. */
    public static final int ARCHITECT_MAX_EXTRA_BREAKS = 3;

    public static final int NEGOCIANT_CAP = 5;
    /** Part retirée à la taxe de territoire et au prix d'achat de pouvoir, par négociant. */
    public static final double NEGOCIANT_REDUCTION_PER_MEMBER = 0.04;
    public static final double NEGOCIANT_MAX_REDUCTION = 0.20;

    public static final int ARTISAN_CAP = 5;
    public static final int ARTISAN_LEVELS_PER_MEMBER = 1;
    public static final int ARTISAN_MAX_LEVEL_REDUCTION = 4;
    /** L'enclume ne devient jamais gratuite. */
    public static final int ARTISAN_MIN_REPAIR_COST = 1;
    public static final int ARTISAN_BONUS_TICKS_PER_MEMBER = 15 * 20;
    public static final int ARTISAN_MAX_BONUS_TICKS = 60 * 20;

    public static final int KNIGHT_CAP = 5;
    public static final double KNIGHT_DAMAGE_PER_MEMBER = 0.03;
    public static final double KNIGHT_MAX_DAMAGE_BONUS = 0.15;
    public static final double KNIGHT_RESIST_PER_MEMBER = 0.02;
    public static final double KNIGHT_MAX_RESIST = 0.10;

    /** Premier choix gratuit. Changement : coût + délai, pour éviter le swap en combat. */
    public static final double CHANGE_COST = 2500.0;
    public static final long CHANGE_COOLDOWN_SECONDS = 6L * 3600L;

    private SkillFormulas() {
    }

    public static int architectBreaksRequired(int members) {
        int extra = stacked(members, ARCHITECT_CAP) * ARCHITECT_EXTRA_BREAKS_PER_MEMBER;
        extra = Math.min(extra, ARCHITECT_MAX_EXTRA_BREAKS);
        return 1 + extra;
    }

    public static double negociantReduction(int members) {
        double reduction = stacked(members, NEGOCIANT_CAP) * NEGOCIANT_REDUCTION_PER_MEMBER;
        return Math.min(NEGOCIANT_MAX_REDUCTION, reduction);
    }

    public static double negociantCostMultiplier(int members) {
        return 1.0 - negociantReduction(members);
    }

    /** Montant après réduction. Jamais supérieur à {@code amount}, jamais négatif. */
    public static double discountedAmount(double amount, int members) {
        if (amount <= 0) {
            return 0;
        }
        return amount * negociantCostMultiplier(members);
    }

    public static int artisanRepairCost(int baseCost, int members) {
        if (baseCost <= 0) {
            return baseCost;
        }
        int reduction = Math.min(ARTISAN_MAX_LEVEL_REDUCTION,
                stacked(members, ARTISAN_CAP) * ARTISAN_LEVELS_PER_MEMBER);
        return Math.max(ARTISAN_MIN_REPAIR_COST, baseCost - reduction);
    }

    public static int artisanDurationBonusTicks(int members) {
        int bonus = stacked(members, ARTISAN_CAP) * ARTISAN_BONUS_TICKS_PER_MEMBER;
        return Math.min(ARTISAN_MAX_BONUS_TICKS, bonus);
    }

    public static double knightOutgoingMultiplier(int members) {
        double bonus = Math.min(KNIGHT_MAX_DAMAGE_BONUS,
                stacked(members, KNIGHT_CAP) * KNIGHT_DAMAGE_PER_MEMBER);
        return 1.0 + bonus;
    }

    public static double knightIncomingMultiplier(int members) {
        double resist = Math.min(KNIGHT_MAX_RESIST,
                stacked(members, KNIGHT_CAP) * KNIGHT_RESIST_PER_MEMBER);
        return 1.0 - resist;
    }

    public static boolean changeCooldownElapsed(long nowSeconds, long changedAtSeconds) {
        if (changedAtSeconds <= 0) {
            return true;
        }
        return nowSeconds >= changedAtSeconds + CHANGE_COOLDOWN_SECONDS;
    }

    private static int stacked(int members, int cap) {
        return Math.min(cap, Math.max(0, members));
    }
}
