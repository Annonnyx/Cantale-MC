package fr.cantale.plugin.anticheat.alert;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.AntiCheatManager;
import fr.cantale.plugin.anticheat.check.Check;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sanctions automatiques selon le trust.
 * Trust à 0 → ban (toujours). Warn/kick si {@code auto-punish}.
 */
public class PunishmentManager {

    private final CantalePlugin plugin;
    private final AntiCheatManager manager;
    private final Set<UUID> banQueued = ConcurrentHashMap.newKeySet();

    public PunishmentManager(CantalePlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void evaluate(Player player, int trust, Check check) {
        // Ban dès que le trust est épuisé (0), même si auto-punish est off
        if (trust <= 0) {
            executeBan(player, check);
            return;
        }

        if (!manager.getConfig().isAutoPunish()) {
            return;
        }

        if (trust <= manager.getConfig().getBanThreshold()) {
            executeBan(player, check);
            return;
        }

        if (trust <= manager.getConfig().getKickThreshold()) {
            executeKick(player, check);
            return;
        }

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
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            String message = ChatColor.translateAlternateColorCodes('&',
                    "&cVous avez été éjecté de Cantale.\n&7Raison: Comportement de déplacement/combat suspect.\n&7Detection: "
                    + check.getType().getDisplayName());
            player.kickPlayer(message);
            Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cAC&8] &e" + player.getName() + " &7a été expulsé automatiquement pour violations répétées."),
                    manager.getConfig().getAlertsPermission());
        });
    }

    private void executeBan(Player player, Check check) {
        UUID uuid = player.getUniqueId();
        if (!banQueued.add(uuid)) return;

        int duration = Math.max(1, manager.getConfig().getBanDurationMinutes());
        String playerName = player.getName();
        String reason = "AntiCheat Cantale — trust épuisé (" + check.getType().getDisplayName() + ")";

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Instant until = Instant.now().plus(Duration.ofMinutes(duration));
                boolean banned = false;
                if (player.isOnline()) {
                    try {
                        player.ban(reason, until, "Cantale-AC", true);
                        banned = true;
                    } catch (Exception ignored) {
                        // fallback ci-dessous
                    }
                }
                if (!banned) {
                    Date expiry = Date.from(until);
                    try {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expiry, "Cantale-AC");
                    } catch (Exception ignored) {}
                    try {
                        Bukkit.getBanList(BanList.Type.PROFILE).addBan(playerName, reason, expiry, "Cantale-AC");
                    } catch (Exception ignored) {}
                    if (player.isOnline()) {
                        player.kickPlayer(ChatColor.RED + reason);
                    }
                }

                Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
                        "&8[&cAC&8] &e" + playerName + " &7a été banni automatiquement (trust 0) pour &c"
                                + duration + " &7minutes."),
                        manager.getConfig().getAlertsPermission());
            } finally {
                // Laisser re-ban possible après reconnexion / nouveau cycle de trust
                Bukkit.getScheduler().runTaskLater(plugin, () -> banQueued.remove(uuid), 20L * 60);
            }
        });
    }
}
