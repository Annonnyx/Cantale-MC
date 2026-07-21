package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Affiche périodiquement un message incitant les joueurs à voter.
 */
public class VoteReminder extends BukkitRunnable {

    private final CantalePlugin plugin;

    public VoteReminder(CantalePlugin plugin) {
        this.plugin = plugin;
        if (plugin.getConfig().getBoolean("vote.reminders.enabled", true)) {
            long minutes = plugin.getConfig().getInt("vote.reminders.interval-minutes", 30);
            long ticks = Math.max(1, minutes) * 60 * 20L;
            runTaskTimer(plugin, ticks, ticks);
        }
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("vote.reminders.enabled", true)) return;

        String message = plugin.getConfig().getString("vote.reminders.message",
                "&8[&6Vote&8] &7N'oublie pas de voter pour Cantale et gagner des récompenses ! &e/vote");

        String colored = ChatColor.translateAlternateColorCodes('&', message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(colored);
        }
    }
}
