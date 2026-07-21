package fr.cantale.plugin.anticheat.rotation;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Détection de rotations anormales.
 *
 * Vérifie si les mouvements de caméra sont trop parfaits ou trop réguliers,
 * ce qui indiquerait un cheat de rotation (snap, head rotation bots).
 * Analyse la distribution des deltas yaw/pitch sur les derniers ticks.
 */
public class RotationCheck extends Check {

    public RotationCheck(AntiCheatConfig config) {
        super(CheckType.ROTATION, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        Deque<Float> yawHistory = data.getYawHistory();
        Deque<Float> pitchHistory = data.getPitchHistory();

        if (yawHistory.size() < 10 || pitchHistory.size() < 10) {
            return CheckResult.pass(type);
        }

        Float[] yaws = yawHistory.toArray(new Float[0]);
        Float[] pitches = pitchHistory.toArray(new Float[0]);

        // Compter les rotations identiques consécutives (pitch identiques = suspect)
        int identicalPitchCount = 0;
        for (int i = 1; i < pitches.length; i++) {
            if (Math.abs(pitches[i] - pitches[i - 1]) < 0.001f && Math.abs(pitches[i]) > 0.1f) {
                identicalPitchCount++;
            }
        }

        // Si plus de 80% des pitch sont identiques et non nuls
        double identicalRatio = (double) identicalPitchCount / (pitches.length - 1);
        if (identicalRatio > 0.8 && pitches.length >= 8) {
            // Vérifier que le yaw change (le joueur bouge la souris en X mais pas en Y = snap)
            int yawChanges = 0;
            for (int i = 1; i < yaws.length; i++) {
                if (Math.abs(normalizeAngle(yaws[i] - yaws[i - 1])) > 1.0f) {
                    yawChanges++;
                }
            }

            if (yawChanges > 3) {
                String details = String.format("identicalPitch=%.0f%% yawChanges=%d",
                        identicalRatio * 100, yawChanges);
                return CheckResult.violation(type, identicalRatio, details);
            }
        }

        // Vérifier les changements de yaw trop rapides (180° en 1 tick = snap rotation)
        for (int i = 1; i < yaws.length; i++) {
            float deltaYaw = Math.abs(normalizeAngle(yaws[i] - yaws[i - 1]));
            if (deltaYaw > 170) {
                // Un 180° en 1 tick est très suspect sauf si le joueur ne bouge pas
                float deltaPitch = Math.abs(pitches[i] - pitches[i - 1]);
                if (deltaPitch < 1.0f) {
                    String details = String.format("instantYaw=%.1f° deltaPitch=%.1f°", deltaYaw, deltaPitch);
                    return CheckResult.violation(type, deltaYaw / 180.0, details);
                }
            }
        }

        return CheckResult.pass(type);
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
