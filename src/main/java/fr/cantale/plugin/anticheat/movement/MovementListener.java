package fr.cantale.plugin.anticheat.movement;

import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import fr.cantale.plugin.anticheat.check.CheckType;
import fr.cantale.plugin.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener principal pour les checks de mouvement.
 *
 * Intercepte chaque PlayerMoveEvent, met à jour les données du joueur,
 * puis exécute tous les checks de mouvement activés.
 * Les résultats sont transmis à l'AntiCheatManager pour traitement.
 */
public class MovementListener implements Listener {

    private final AntiCheatManager manager;

    public MovementListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignorer les modes créatif et spectateur
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifier le bypass
        if (manager.shouldBypass(player)) {
            return;
        }

        // Vérifier si l'anti-cheat est activé
        if (!manager.getConfig().isEnabled()) {
            return;
        }

        // Récupérer ou créer les données du joueur
        PlayerData data = manager.getPlayerData(player.getUniqueId());

        // Grâce post-TP / reset mouvement : ne pas checker, juste resync position
        if (data.isMovementExempt()) {
            data.addPosition(player.getLocation());
            data.addRotation(player.getLocation().getYaw(), player.getLocation().getPitch());
            data.setWasOnGround(player.isOnGround());
            if (player.isOnGround()) {
                data.setLastGroundY(player.getLocation().getY());
            }
            return;
        }

        // Exécuter tous les checks de mouvement activés
        for (Check check : manager.getCheckRegistry().getEnabledByCategory(CheckType.Category.MOVEMENT)) {
            CheckResult result = check.check(player, data);
            if (result.isViolated()) {
                manager.handleViolation(player, result, check);
            }
        }

        // Mettre à jour les données du joueur après les checks
        data.addPosition(player.getLocation());
        data.addRotation(player.getLocation().getYaw(), player.getLocation().getPitch());

        // Mettre à jour le statut au sol
        data.setWasOnGround(player.isOnGround());
        if (player.isOnGround()) {
            data.setLastGroundY(player.getLocation().getY());
        }
    }
}
