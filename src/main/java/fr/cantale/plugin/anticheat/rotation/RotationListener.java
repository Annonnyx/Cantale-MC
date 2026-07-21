package fr.cantale.plugin.anticheat.rotation;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener pour les checks de rotation.
 *
 * Intercepte les PlayerMoveEvent où seule la rotation change (pas de déplacement)
 * pour alimenter l'historique et exécuter les vérifications.
 */
public class RotationListener implements Listener {

    private final AntiCheatManager manager;

    public RotationListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (manager.shouldBypass(player)) {
            return;
        }

        if (!manager.getConfig().isEnabled()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Vérifier si seule la rotation a changé (pas de déplacement significatif)
        boolean positionChanged = from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();

        boolean rotationChanged = from.getYaw() != to.getYaw()
                || from.getPitch() != to.getPitch();

        // On vérifie les rotations quand la rotation change sans déplacement
        // (les rotations pendant le déplacement sont déjà traitées par MovementListener)
        if (rotationChanged && !positionChanged) {
            PlayerData data = manager.getPlayerData(player.getUniqueId());
            data.addRotation(to.getYaw(), to.getPitch());

            Check rotationCheck = manager.getCheckRegistry().get(CheckType.ROTATION);
            if (rotationCheck != null && rotationCheck.isEnabled()) {
                CheckResult result = rotationCheck.check(player, data);
                if (result.isViolated()) {
                    manager.handleViolation(player, result, rotationCheck);
                }
            }
        }
    }
}
