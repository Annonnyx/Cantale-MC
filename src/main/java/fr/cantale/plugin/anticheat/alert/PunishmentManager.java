package fr.cantale.plugin.anticheat.alert;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Gère l'application de sanctions automatiques graduelles
 * en fonction du score de confiance (trust) du joueur.
 */
public class PunishmentManager {

    private final CantalePlugin plugin;
    private final AntiCheatManager manager;

    public PunishmentManager(CantalePlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Evalue si le score de confiance actuel d'un joueur nécessite une punition.
     */
    public void evaluate(Player player, int trust, Check check) {
        if (!manager.getConfig().isAutoPunish()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // 1. Seuil de tempban
        if (trust <= manager.getConfig().getBanThreshold()) {
            executeBan(player, check);
            return;
        }

        // 2. Seuil de kick
        if (trust <= manager.getConfig().getKickThreshold()) {
            executeKick(player, check);
            return;
        }

        // 3. Seuil de warn (avertissement textuel)
        if (trust <= manager.getConfig().getWarnThreshold()) {
            executeWarn(player, check);
        }
    }

    private void executeWarn(Player player, Check check) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Cantale&8] &cComportement de jeu anormal détecté (" + check.getType().getDisplayName() + "). " +
                "Veuillez stabiliser votre connexion ou désactiver tout logiciel tiers."));
    }

    private void executeKick(Player player, Check check) {
        // Kick synchrone sur le thread principal
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                String message = ChatColor.translateAlternateColorCodes('&',
                        "&cVous avez été éjecté de Cantale.\n&7Raison: Comportement de déplacement/combat suspect.\n&7Detection: "
                        + check.getType().getDisplayName());
                
                player.kickPlayer(message);
                
                // Annoncer au staff
                Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
                        "&8[&cAC&8] &e" + player.getName() + " &7a été expulsé automatiquement pour violations répétées."),
                        manager.getConfig().getAlertsPermission());
            }
        });
    }

    private void executeBan(Player player, Check check) {
        int duration = manager.getConfig().getBanDurationMinutes();
        String playerName = player.getName();

        // Ban synchrone sur le thread principal
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                String command = String.format("tempban %s %dm Auto-Kick/Cheat détecté par l'AntiCheat (%s)",
                        playerName, duration, check.getType().getDisplayName());
                
                // Exécuter via la console
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                // Annoncer publiquement ou au staff
                Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
                        "&8[&cAC&8] &e" + playerName + " &7a été banni automatiquement pour triche pendant &c" + duration + " &7minutes."),
                        manager.getConfig().getAlertsPermission());
            }
        });
    }
}
