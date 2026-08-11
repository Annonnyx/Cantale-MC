package fr.cantale.plugin.anticheat.combat;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Détection de Reach Hack.
 *
 * Mesure la distance œil → hitbox (AABB), avec compensation ping,
 * et un petit buffer pour éviter les faux positifs lag.
 */
public class ReachCheck extends Check {

    private static final int REACH_BUFFER_NEEDED = 2;

    public ReachCheck(AntiCheatConfig config) {
        super(CheckType.REACH, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        return CheckResult.pass(type);
    }

    public CheckResult checkReach(Player attacker, Entity target, PlayerData data) {
        Location eye = attacker.getEyeLocation();
        if (eye.getWorld() == null || target.getWorld() == null
                || !eye.getWorld().equals(target.getWorld())) {
            return CheckResult.pass(type);
        }

        BoundingBox box = target.getBoundingBox().clone().expand(0.05);
        Vector eyeVec = eye.toVector();
        double closest = distanceToAabb(eyeVec, box);

        double maxReach = Math.min(3.35, Math.max(3.0, config.getMaxReach()));
        // Compensation lag : ~0.002 bloc / ms de ping, plafonnée
        double pingBonus = Math.min(0.45, Math.max(0, attacker.getPing()) * 0.002);
        double allowed = maxReach + pingBonus;

        if (closest > allowed) {
            data.incrementReachBuffer();
            if (data.getReachBuffer() >= REACH_BUFFER_NEEDED) {
                data.resetReachBuffer();
                String details = String.format("eyeDist=%.2f max=%.2f ping=%dms",
                        closest, allowed, attacker.getPing());
                return CheckResult.violation(type, closest - allowed, details);
            }
            return CheckResult.pass(type);
        }

        data.resetReachBuffer();
        return CheckResult.pass(type);
    }

    /** Distance minimale du point à la boîte (0 si à l'intérieur). */
    private static double distanceToAabb(Vector point, BoundingBox box) {
        double dx = Math.max(box.getMinX() - point.getX(), Math.max(0, point.getX() - box.getMaxX()));
        double dy = Math.max(box.getMinY() - point.getY(), Math.max(0, point.getY() - box.getMaxY()));
        double dz = Math.max(box.getMinZ() - point.getZ(), Math.max(0, point.getZ() - box.getMaxZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
