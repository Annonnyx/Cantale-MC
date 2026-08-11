package fr.cantale.plugin.anticheat.rotation;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Détection de snaps de caméra extrêmes (quasi 180° en un tick sans pitch).
 * Les heuristiques « pitch constant » ont été retirées (trop de faux positifs).
 */
public class RotationCheck extends Check {

    public RotationCheck(AntiCheatConfig config) {
        super(CheckType.ROTATION, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        if (data.isMovementExempt()) {
            return CheckResult.pass(type);
        }

        Deque<Float> yawHistory = data.getYawHistory();
        Deque<Float> pitchHistory = data.getPitchHistory();

        if (yawHistory.size() < 8 || pitchHistory.size() < 8) {
            return CheckResult.pass(type);
        }

        Float[] yaws = yawHistory.toArray(new Float[0]);
        Float[] pitches = pitchHistory.toArray(new Float[0]);

        // Snap quasi-instantané (ex. head rotation cheat) — seuil très haut pour éviter les FP DPI
        int snapHits = 0;
        for (int i = 1; i < yaws.length; i++) {
            float deltaYaw = Math.abs(normalizeAngle(yaws[i] - yaws[i - 1]));
            float deltaPitch = Math.abs(pitches[i] - pitches[i - 1]);
            if (deltaYaw > 175 && deltaPitch < 0.5f) {
                snapHits++;
            }
        }

        if (snapHits >= 2) {
            String details = String.format("instantYawSnaps=%d", snapHits);
            return CheckResult.violation(type, snapHits, details);
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
