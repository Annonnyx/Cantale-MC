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
 * Les alertes prioritaires (trust 0, kick bloqué par cooldown) bypassent le cooldown.
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
     * Envoie une alerte de violation (check natif) aux membres du staff en jeu et à Discord.
     */
    public void sendAlert(Player player, CheckResult result, Check check, int violations, int trust) {
        sendAlert(player, check.getType().getDisplayName(), result.getDetails(), violations, trust);
    }

    /**
     * Envoie une alerte de violation par libellé (utilisé aussi par le bridge Grim).
     */
    public void sendAlert(Player player, String checkDisplay, String details, int violations, int trust) {
        UUID uuid = player.getUniqueId();

        // Vérifier le cooldown de l'alerte
        if (hasCooldown(uuid, checkDisplay)) {
            return;
        }
        setCooldown(uuid, checkDisplay);

        String alertMessage = ChatColor.translateAlternateColorCodes('&',
                "&8[&cAC&8] &e" + player.getName() + " &7a échoué &c" + checkDisplay +
                " &7(x" + violations + ") &8— &7" + details + " &8(&cTrust: " + trust + "&8)");

        broadcastToStaff(alertMessage);

        plugin.getLogger().info("[AntiCheat] " + player.getName() + " failed " + checkDisplay +
                " (x" + violations + "): " + details + " [Trust: " + trust + "]");

        sendDiscordAlert("🛡️ ANTICHEAT — ALERTE", Color.RED,
                "**Joueur:** " + player.getName() + "\n" +
                "**Détection:** " + checkDisplay + "\n" +
                "**Violations cumulées:** " + violations + "\n" +
                "**Score de confiance:** " + trust + "/100\n" +
                "**Détails:** " + details);
    }

    /**
     * Alerte prioritaire staff (trust 0, kick bloqué par cooldown…).
     * Ignore le cooldown d'alerte et est toujours envoyée sur Discord.
     */
    public void sendPriorityAlert(Player player, String reason) {
        String alertMessage = ChatColor.translateAlternateColorCodes('&',
                "&8[&4AC&8] &c&lPRIORITÉ &e" + player.getName() + " &7— &c" + reason);

        broadcastToStaff(alertMessage);
        plugin.getLogger().warning("[AntiCheat] PRIORITY " + player.getName() + " — " + reason);

        sendDiscordAlert("🚨 ANTICHEAT — PRIORITÉ", Color.ORANGE,
                "**Joueur:** " + player.getName() + "\n" +
                "**Raison:** " + reason + "\n" +
                "**Action requise:** vérification staff (pas de ban automatique)");
    }

    private void broadcastToStaff(String message) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(manager.getConfig().getAlertsPermission())
                    && !alertsDisabledPlayers.contains(onlinePlayer.getUniqueId())) {
                onlinePlayer.sendMessage(message);
            }
        }
    }

    private void sendDiscordAlert(String title, Color color, String description) {
        if (!manager.getConfig().isDiscordEnabled()
                || plugin.getDiscordBot() == null
                || !plugin.getDiscordBot().isConnected()) {
            return;
        }

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
            plugin.getDiscordBot().sendEmbedToChannel(channelId, color, title, description);
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
     * Active/désactive les alertes pour un administrateur spécifique (par session).
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

    /**
     * Purge complète des données éphémères d'un joueur (déconnexion).
     * La préférence d'alertes est volontairement par session.
     */
    public void clearCooldowns(UUID uuid) {
        alertCooldowns.remove(uuid);
        alertsDisabledPlayers.remove(uuid);
    }
}
