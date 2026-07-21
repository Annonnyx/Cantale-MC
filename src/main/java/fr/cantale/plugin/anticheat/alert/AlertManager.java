package fr.cantale.plugin.anticheat.alert;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère l'envoi d'alertes aux membres du staff et sur Discord.
 * Utilise un système de cooldown pour éviter de spammer les alertes identiques.
 */
public class AlertManager {

    private final CantalePlugin plugin;
    private final AntiCheatManager manager;
    private final Map<UUID, Set<String>> alertCooldowns = new HashMap<>();
    private final Set<UUID> alertsDisabledPlayers = new HashSet<>();

    public AlertManager(CantalePlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Envoie une alerte de violation aux membres du staff en jeu et à Discord.
     */
    public void sendAlert(Player player, CheckResult result, Check check, int violations, int trust) {
        UUID uuid = player.getUniqueId();
        String cooldownKey = check.getType().name();

        // Vérifier le cooldown de l'alerte
        if (hasCooldown(uuid, cooldownKey)) {
            return;
        }

        // Définir le cooldown
        setCooldown(uuid, cooldownKey);

        String alertMessage = ChatColor.translateAlternateColorCodes('&',
                "&8[&cAC&8] &e" + player.getName() + " &7a échoué &c" + check.getType().getDisplayName() +
                " &7(x" + violations + ") &8— &7" + result.getDetails() + " &8(&cTrust: " + trust + "&8)");

        // Envoyer aux membres du staff en ligne
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(manager.getConfig().getAlertsPermission())) {
                if (!alertsDisabledPlayers.contains(onlinePlayer.getUniqueId())) {
                    onlinePlayer.sendMessage(alertMessage);
                }
            }
        }

        // Logguer dans la console
        plugin.getLogger().info("[AntiCheat] " + player.getName() + " failed " + check.getType().getDisplayName() + 
                " (x" + violations + "): " + result.getDetails() + " [Trust: " + trust + "]");

        // Envoyer sur Discord si configuré
        if (manager.getConfig().isDiscordEnabled() && plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            sendDiscordAlert(player, check, violations, trust, result.getDetails());
        }
    }

    /**
     * Envoie l'alerte sur le bot Discord.
     */
    private void sendDiscordAlert(Player player, Check check, int violations, int trust, String details) {
        long channelId = 0L;
        String channelConfig = manager.getConfig().getDiscordChannelId();

        if (channelConfig != null && !channelConfig.isEmpty()) {
            try {
                channelId = Long.parseLong(channelConfig);
            } catch (NumberFormatException ignored) {}
        }

        // Si aucun canal spécifique n'est configuré, on utilise le canal de mod-logs par défaut
        if (channelId == 0L) {
            channelId = plugin.getDiscordBot().getConfig().getModLogsChannelId();
        }

        if (channelId != 0L) {
            String desc = "**Joueur:** " + player.getName() + "\n" +
                          "**Détection:** " + check.getType().getDisplayName() + "\n" +
                          "**Violations cumulées:** " + violations + "\n" +
                          "**Score de confiance:** " + trust + "/100\n" +
                          "**Détails:** " + details;

            plugin.getDiscordBot().sendEmbedToChannel(
                channelId,
                Color.RED,
                "🛡️ ANTICHEAT — ALERTE",
                desc
            );
        }
    }

    private boolean hasCooldown(UUID uuid, String key) {
        return alertCooldowns.containsKey(uuid) && alertCooldowns.get(uuid).contains(key);
    }

    private void setCooldown(UUID uuid, String key) {
        alertCooldowns.computeIfAbsent(uuid, k -> new HashSet<>()).add(key);
        
        // Retirer le cooldown après X secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Set<String> cooldowns = alertCooldowns.get(uuid);
            if (cooldowns != null) {
                cooldowns.remove(key);
                if (cooldowns.isEmpty()) {
                    alertCooldowns.remove(uuid);
                }
            }
        }, manager.getConfig().getAlertCooldownSeconds() * 20L);
    }

    /**
     * Active/désactive les alertes pour un administrateur spécifique.
     */
    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabledPlayers.contains(uuid)) {
            alertsDisabledPlayers.remove(uuid);
            return true; // Alertes activées
        } else {
            alertsDisabledPlayers.add(uuid);
            return false; // Alertes désactivées
        }
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return !alertsDisabledPlayers.contains(uuid);
    }

    public void clearCooldowns(UUID uuid) {
        alertCooldowns.remove(uuid);
    }
}
