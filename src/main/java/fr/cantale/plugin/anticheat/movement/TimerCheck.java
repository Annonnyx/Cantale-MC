package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Détection de Timer Hack.
 *
 * Un joueur normal envoie environ 20 packets de mouvement par seconde (1 par tick).
 * Un timer hack augmente la fréquence des ticks client, ce qui résulte en
 * plus de packets de mouvement que la normale (22+ par seconde).
 *
 * Fonctionne avec une fenêtre glissante de 1 seconde.
 */
public class TimerCheck extends Check {

    public TimerCheck(AntiCheatConfig config) {
        super(CheckType.TIMER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        long windowStart = data.getMovePacketWindowStart();
        long elapsed = now - windowStart;

        data.incrementMovePacketCount();

        // Si la fenêtre de 1 seconde est écoulée, vérifier
        if (elapsed >= 1000) {
            int packetCount = data.getMovePacketCount();
            int maxPackets = config.getMaxPacketsPerSecond();

            data.resetMovePacketCount();

            if (packetCount > maxPackets) {
                String details = String.format("packets=%d max=%d window=%dms",
                        packetCount, maxPackets, elapsed);
                return CheckResult.violation(type, packetCount - maxPackets, details);
            }
        }

        return CheckResult.pass(type);
    }
}
