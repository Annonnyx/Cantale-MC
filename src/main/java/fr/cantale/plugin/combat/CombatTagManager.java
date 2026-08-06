package fr.cantale.plugin.combat;

import fr.cantale.plugin.CantalePlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager {

    private static final int COMBAT_DURATION_SECONDS = 15;

    private final CantalePlugin plugin;
    private final Map<UUID, Long> combatExpirations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> opponents = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> actionbarTasks = new ConcurrentHashMap<>();

    public CombatTagManager(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Marque ou rafraîchit le statut de combat pour deux joueurs (attaquant et victime).
     */
    public void tagCombat(Player player1, Player player2) {
        if (player1 == null || player2 == null) return;
        if (player1.getUniqueId().equals(player2.getUniqueId())) return;

        long expireAt = System.currentTimeMillis() + (COMBAT_DURATION_SECONDS * 1000L);

        tagPlayer(player1, player2, expireAt);
        tagPlayer(player2, player1, expireAt);
    }

    private void tagPlayer(Player player, Player opponent, long expireAt) {
        UUID uuid = player.getUniqueId();
        boolean wasInCombat = isInCombat(player);

        combatExpirations.put(uuid, expireAt);
        opponents.put(uuid, opponent.getUniqueId());

        if (!wasInCombat) {
            player.sendMessage(formatMessage("&c⚔ Vous êtes maintenant en combat avec &e" + opponent.getName() + " &c! Ne vous déconnectez pas."));
        }

        startActionbarTask(player, opponent.getName());
    }

    /**
     * Vérifie si un joueur est actuellement en combat.
     */
    public boolean isInCombat(Player player) {
        return player != null && isInCombat(player.getUniqueId());
    }

    public boolean isInCombat(UUID uuid) {
        return getRemainingSeconds(uuid) > 0;
    }

    /**
     * Retourne le nombre de secondes restantes en combat pour un joueur.
     */
    public int getRemainingSeconds(UUID uuid) {
        Long exp = combatExpirations.get(uuid);
        if (exp == null) return 0;
        long diff = exp - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    /**
     * Met fin au combat d'un joueur et nettoie ses tâches.
     */
    public void endCombat(UUID uuid) {
        combatExpirations.remove(uuid);
        opponents.remove(uuid);

        BukkitTask task = actionbarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy("§a✔ Vous n'êtes plus en combat."));
            player.sendMessage(formatMessage("&a✔ Vous n'êtes plus en combat."));
        }
    }

    /**
     * Gère la mort d'un joueur : si la victime avait un adversaire, le timer de cet adversaire est réinitialisé à 0.
     */
    public void handleOpponentDeath(Player victim) {
        if (victim == null) return;
        UUID victimUuid = victim.getUniqueId();
        UUID opponentUuid = opponents.get(victimUuid);

        endCombat(victimUuid);

        if (opponentUuid != null) {
            endCombat(opponentUuid);
        }
    }

    /**
     * Gère la déconnexion en combat d'un joueur (déco combat) : retrait d'une vie et annonce.
     */
    public void handleCombatLog(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        if (!isInCombat(uuid)) return;

        endCombat(uuid);

        String broadcastMsg = formatMessage("&c&lDéco Combat ! &e" + player.getName() + " &cs'est déconnecté en combat et perd 1 vie.");
        Bukkit.broadcastMessage(broadcastMsg);

        // Appliquer la perte d'une vie
        plugin.getLifeManager().handleDeath(player);
    }

    private void startActionbarTask(Player player, String opponentName) {
        UUID uuid = player.getUniqueId();

        BukkitTask existing = actionbarTasks.get(uuid);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    actionbarTasks.remove(uuid);
                    return;
                }

                int remaining = getRemainingSeconds(uuid);
                if (remaining <= 0) {
                    endCombat(uuid);
                    cancel();
                    return;
                }

                String actionBarText = "§c⚔ En combat avec §e" + opponentName + " §7(§e" + remaining + "s§7)";
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(actionBarText));
            }
        }.runTaskTimer(plugin, 0L, 10L); // Met à jour toutes les 0.5 sec

        actionbarTasks.put(uuid, task);
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
