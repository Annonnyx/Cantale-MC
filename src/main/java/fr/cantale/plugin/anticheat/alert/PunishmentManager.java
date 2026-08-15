package fr.cantale.plugin.anticheat.alert;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.anticheat.AntiCheatConfig;
import fr.cantale.plugin.anticheat.AntiCheatManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sanctions automatiques progressives selon le trust.
 *
 * Principes (rodage anti faux-bans) :
 * - JAMAIS de ban automatique à trust 0 : trust 0 = alerte staff prioritaire + kick
 *   (si auto-punish). Le ban reste une décision staff.
 * - Kick limité par un cooldown (1 kick / {@code kick-cooldown-minutes}) — entre deux,
 *   le staff est alerté à la place.
 * - Tempban automatique uniquement si {@code punishments.ban-auto: true} ET récidive
 *   ({@code kicks-for-tempban} kicks dans les dernières 24 h).
 */
public class PunishmentManager {

    private static final long RECIDIVE_WINDOW_MS = 24L * 3600L * 1000L;

    private final CantalePlugin plugin;
    private final AntiCheatManager manager;
    private final Map<UUID, Deque<Long>> kickHistory = new ConcurrentHashMap<>();

    public PunishmentManager(CantalePlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Évalue la sanction suite à une violation.
     *
     * @param player   le joueur
     * @param trust    son trust après pénalité
     * @param label    le libellé de la détection (natif ou Grim)
     */
    public void evaluate(Player player, int trust, String label) {
        AntiCheatConfig config = manager.getConfig();

        if (trust <= 0) {
            // Trust épuisé : alerte staff prioritaire, toujours (même sans auto-punish)
            manager.getAlertManager().sendPriorityAlert(player,
                    "Trust épuisé (0) — dernière détection : " + label);

            if (config.isBanAuto() && countRecentKicks(player.getUniqueId()) >= config.getKicksForTempban()) {
                executeBan(player, label);
                return;
            }

            if (config.isAutoPunish()) {
                executeKickWithCooldown(player, label);
            }
            return;
        }

        if (!config.isAutoPunish()) {
            return;
        }

        if (trust <= config.getKickThreshold()) {
            executeKickWithCooldown(player, label);
            return;
        }

        if (trust <= config.getWarnThreshold()) {
            executeWarn(player, label);
        }
    }

    private void executeWarn(Player player, String label) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6Cantale&8] &cComportement de jeu anormal détecté (" + label + "). " +
                "Veuillez stabiliser votre connexion ou désactiver tout logiciel tiers."));
    }

    /**
     * Kick avec cooldown : 1 kick max par joueur par {@code kick-cooldown-minutes}.
     * Pendant le cooldown, le staff est alerté à la place.
     */
    private void executeKickWithCooldown(Player player, String label) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, manager.getConfig().getKickCooldownMinutes()) * 60_000L;

        Deque<Long> kicks = kickHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (kicks) {
            Long lastKick = kicks.peekLast();
            if (lastKick != null && now - lastKick < cooldownMs) {
                // Cooldown actif : alerter le staff au lieu de re-kicker
                long remainingMin = (cooldownMs - (now - lastKick)) / 60_000L + 1;
                manager.getAlertManager().sendPriorityAlert(player,
                        "Kick anti-cheat requis (" + label + ") mais cooldown actif (~" + remainingMin + " min restantes)");
                return;
            }
            kicks.addLast(now);
            while (!kicks.isEmpty() && now - kicks.peekFirst() > RECIDIVE_WINDOW_MS) {
                kicks.pollFirst();
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            String message = ChatColor.translateAlternateColorCodes('&',
                    "&cVous avez été éjecté de Cantale.\n&7Raison: Comportement de déplacement/combat suspect.\n&7Detection: "
                    + label);
            player.kickPlayer(message);
            Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cAC&8] &e" + player.getName() + " &7a été expulsé automatiquement pour violations répétées."),
                    manager.getConfig().getAlertsPermission());
        });
    }

    /**
     * Nombre de kicks dans les dernières 24 h (fenêtre de récidive).
     */
    public int countRecentKicks(UUID uuid) {
        Deque<Long> kicks = kickHistory.get(uuid);
        if (kicks == null) return 0;
        long now = System.currentTimeMillis();
        synchronized (kicks) {
            while (!kicks.isEmpty() && now - kicks.peekFirst() > RECIDIVE_WINDOW_MS) {
                kicks.pollFirst();
            }
            return kicks.size();
        }
    }

    public void removePlayer(UUID uuid) {
        // L'historique de kicks est conservé en mémoire (fenêtre 24 h, anti relog).
    }

    /**
     * Tempban — utilisé uniquement quand {@code ban-auto: true} avec récidive avérée.
     */
    private void executeBan(Player player, String label) {
        int duration = Math.max(1, manager.getConfig().getBanDurationMinutes());
        String playerName = player.getName();
        String reason = "AntiCheat Cantale — récidive après kicks (" + label + ")";

        Bukkit.getScheduler().runTask(plugin, () -> {
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
                    "&8[&cAC&8] &e" + playerName + " &7a été tempban &c" + duration
                            + " &7min (récidive : " + countRecentKicks(player.getUniqueId()) + " kicks/24h)."),
                    manager.getConfig().getAlertsPermission());
        });
    }
}
