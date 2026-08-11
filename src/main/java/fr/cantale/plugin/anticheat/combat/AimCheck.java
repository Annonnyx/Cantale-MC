package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Détection d'Aim Assist / Aimbot.
 *
 * Ne flag que des rotations anormalement robotiques (écart-type très bas
 * sur une fenêtre large), avec buffer de confirmations pour limiter les FP.
 */
public class AimCheck extends Check {

    private static final int AIM_BUFFER_NEEDED = 4;
    private static final int MIN_SAMPLES = 14;

    public AimCheck(AntiCheatConfig config) {
        super(CheckType.AIM, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        Deque<Float> yawHistory = data.getYawHistory();

        if (yawHistory.size() < MIN_SAMPLES) {
            return CheckResult.pass(type);
        }

        Float[] yaws = yawHistory.toArray(new Float[0]);
        double[] yawDeltas = new double[yaws.length - 1];
        for (int i = 0; i < yawDeltas.length; i++) {
            yawDeltas[i] = Math.abs(normalizeAngle(yaws[i + 1] - yaws[i]));
        }

        double avgYawDelta = average(yawDeltas);
        // Pas de mouvement souris significatif → pas d'analyse aim
        if (avgYawDelta < 1.5) {
            data.resetAimBuffer();
            return CheckResult.pass(type);
        }

        double yawDeviation = calculateStdDev(yawDeltas);
        // Anciennes configs avaient 1.5 (beaucoup trop permissif côté FP) — on plafonne.
        double minDeviation = Math.min(0.40, Math.max(0.08, config.getMinRotationDeviation()));

        if (yawDeviation > 0 && yawDeviation < minDeviation && avgYawDelta > 2.0) {
            data.incrementAimBuffer();
            if (data.getAimBuffer() >= AIM_BUFFER_NEEDED) {
                data.resetAimBuffer();
                String details = String.format("yawDev=%.3f minDev=%.3f avgDelta=%.2f",
                        yawDeviation, minDeviation, avgYawDelta);
                return CheckResult.violation(type, minDeviation - yawDeviation, details);
            }
            return CheckResult.pass(type);
        }

        data.resetAimBuffer();
        return CheckResult.pass(type);
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0;
        double mean = average(values);
        double variance = 0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        return Math.sqrt(variance / values.length);
    }

    private double average(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}
