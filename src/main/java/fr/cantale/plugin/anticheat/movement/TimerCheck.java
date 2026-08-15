package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Détection de Timer Hack.
 *
 * Un joueur normal envoie ~20 packets de mouvement avec changement de position
 * par seconde. Un timer hack accélère les ticks client → plus de position packets.
 *
 * Fiabilisation :
 * - Seuls les moves avec changement de position comptent (les look packets gonflent
 *   artificiellement le compteur — source historique de faux positifs).
 * - Compensation TPS : quand le serveur lag, les packets arrivent en rafales ;
 *   le seuil est mis à l'échelle (20/TPS) et le check est suspendu sous min-tps.
 * - Les joueurs avec un ping > lag.max-ping-ms sont ignorés (jitter réseau).
 *
 * Note : Grim couvre Timer de façon bien plus fiable (packet-based). Ce check natif
 * est désactivé par défaut et ne sert qu'en fallback quand Grim est absent.
 */
public class TimerCheck extends Check {

    private static final double MAX_TPS_COMPENSATION = 2.0;

    public TimerCheck(AntiCheatConfig config) {
        super(CheckType.TIMER, config);
    }

    @Override
    public CheckResult check(Player player, PlayerData data) {
        // Ne compter que les packets avec déplacement réel (pas les rotations seules)
        if (!data.isLastMoveChangedPosition()) {
            return CheckResult.pass(type);
        }

        // Joueurs à très haute latence : mesure non fiable
        if (player.getPing() > config.getLagMaxPingMs()) {
            data.resetMovePacketCount();
            data.resetTimerBuffer();
            return CheckResult.pass(type);
        }

        // Serveur en surcharge : suspendre le check
        double tps = getCurrentTps();
        if (tps < config.getLagMinTps()) {
            data.resetMovePacketCount();
            data.resetTimerBuffer();
            return CheckResult.pass(type);
        }

        long now = System.currentTimeMillis();
        long elapsed = now - data.getMovePacketWindowStart();

        data.incrementMovePacketCount();

        if (elapsed >= 1000) {
            int packetCount = data.getMovePacketCount();
            // Compensation TPS : en dessous de 20 TPS, les packets arrivent en rafales
            double compensation = Math.min(MAX_TPS_COMPENSATION, 20.0 / Math.max(tps, 1.0));
            int effectiveMax = (int) Math.ceil(config.getMaxPacketsPerSecond() * compensation);

            data.resetMovePacketCount();

            if (packetCount > effectiveMax) {
                data.incrementTimerBuffer();
                if (data.getTimerBuffer() >= 3) {
                    String details = String.format("packets=%d max=%d (tps=%.1f ping=%d) window=%dms",
                            packetCount, effectiveMax, tps, player.getPing(), elapsed);
                    return CheckResult.violation(type, packetCount - effectiveMax, details);
                }
            } else {
                data.resetTimerBuffer();
            }
        }

        return CheckResult.pass(type);
    }

    private double getCurrentTps() {
        try {
            return Bukkit.getTPS()[0];
        } catch (Throwable t) {
            return 20.0;
        }
    }
}
