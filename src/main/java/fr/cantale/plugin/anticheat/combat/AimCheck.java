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
 * Analyse les variations de yaw/pitch du joueur pour détecter des rotations
 * trop parfaites ou trop régulières. Les aimbots produisent des rotations
 * avec un écart-type très faible (snap précis) ou des angles récurrents
 * (0°, 45°, 90°, 180°).
 */
public class AimCheck extends Check {

    public AimCheck(AntiCheatConfig config) {
        super(CheckType.AIM, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        Deque<Float> yawHistory = data.getYawHistory();
        Deque<Float> pitchHistory = data.getPitchHistory();

        // Besoin d'un minimum d'historique
        if (yawHistory.size() < 10) {
            return CheckResult.pass(type);
        }

        // Calculer les deltas de rotation
        Float[] yaws = yawHistory.toArray(new Float[0]);
        Float[] pitches = pitchHistory.toArray(new Float[0]);

        double[] yawDeltas = new double[yaws.length - 1];
        double[] pitchDeltas = new double[pitches.length - 1];

        for (int i = 0; i < yawDeltas.length; i++) {
            yawDeltas[i] = Math.abs(normalizeAngle(yaws[i + 1] - yaws[i]));
        }
        for (int i = 0; i < pitchDeltas.length; i++) {
            pitchDeltas[i] = Math.abs(pitches[i + 1] - pitches[i]);
        }

        // Calculer l'écart-type des deltas yaw
        double yawDeviation = calculateStdDev(yawDeltas);
        double minDeviation = config.getMinRotationDeviation();

        // Écart-type trop faible = rotations trop régulières = aimbot
        if (yawDeviation < minDeviation && yawDeviation > 0) {
            // Vérifier que le joueur bouge réellement la souris (pas immobile)
            double avgYawDelta = average(yawDeltas);
            if (avgYawDelta > 0.5) {
                String details = String.format("yawDev=%.2f minDev=%.2f avgDelta=%.2f",
                        yawDeviation, minDeviation, avgYawDelta);
                return CheckResult.violation(type, minDeviation - yawDeviation, details);
            }
        }

        // Vérifier les snap angles (multiples de 45° récurrents)
        int snapCount = 0;
        for (double delta : yawDeltas) {
            if (isSnapAngle(delta)) {
                snapCount++;
            }
        }

        // Si plus de 60% des rotations sont des snap angles
        double snapRatio = (double) snapCount / yawDeltas.length;
        if (snapRatio > 0.6 && yawDeltas.length >= 5) {
            String details = String.format("snapRatio=%.0f%% snapCount=%d/%d",
                    snapRatio * 100, snapCount, yawDeltas.length);
            return CheckResult.violation(type, snapRatio, details);
        }

        return CheckResult.pass(type);
    }

    /**
     * Normalise un angle entre -180 et 180.
     */
    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    /**
     * Vérifie si un delta de rotation est un snap angle (multiple de 45° ±5°).
     */
    private boolean isSnapAngle(double delta) {
        double threshold = config.getSnapAngleThreshold();
        for (int angle = 45; angle <= 180; angle += 45) {
            if (Math.abs(delta - angle) < threshold) {
                return true;
            }
        }
        return false;
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
