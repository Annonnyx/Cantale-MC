package fr.cantale.plugin.discord.tasks;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Annonce toutes les 5 minutes la localisation des joueurs WANTED de palier 4 (5M+).
 */
public class WantedTrackerTask extends BukkitRunnable {

    private final CantalePlugin plugin;

    public WantedTrackerTask(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void schedule() {
        runTaskTimerAsynchronously(plugin, 20L * 60 * 5, 20L * 60 * 5); // toutes les 5 min
    }

    @Override
    public void run() {
        var wantedMgr = plugin.getWantedManager();
        if (wantedMgr == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!wantedMgr.isWanted(player.getUniqueId())) continue;
            int tier = wantedMgr.getWantedTier(player.getUniqueId());
            if (tier < 4) continue;

            double total = wantedMgr.getTotalBounty(player.getUniqueId());
            String tierLabel = wantedMgr.getTierLabel(tier);

            var facMgr = plugin.getFactionManager();
            var fac = facMgr != null ? facMgr.getFactionAtClaim(player.getWorld().getName(), player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ()) : null;
            boolean inClaim = fac != null;
            String loc;
            if (!inClaim) {
                loc = String.format("%.0f", player.getLocation().getX()) + " " + String.format("%.0f", player.getLocation().getY()) + " " + String.format("%.0f", player.getLocation().getZ()) + " (" + player.getWorld().getName() + ")";
            } else {
                loc = "Dans un claim (caché)";
            }

            String mcMsg = "§8[§cWANTED§8] §e" + player.getName() + " §7est localisé: §f" + loc + " §7| Prime: §6" + String.format("%.0f", total) + " §7Cantox";
            Bukkit.broadcastMessage(mcMsg);

            if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
                long ch = plugin.getDiscordBot().getConfig().getMcDeathsChannelId();
                if (ch != 0L) {
                    String desc = "**" + player.getName() + "** traqué en direct !\n**Localisation:** " + loc + "\n**Prime totale:** " + String.format("%.0f", total) + " Cantox\n**Palier:** " + tierLabel;
                    long pingRoleId = plugin.getDiscordBot().getConfig().getWantedPingRoleId();
                    String content = pingRoleId > 0 ? "<@&" + pingRoleId + "> **Un joueur Wanted est traqué en direct !**" : null;
                    plugin.getDiscordBot().sendEmbedToChannel(ch, java.awt.Color.RED, "🚨 WANTED — Tracker", desc, content);
                }
            }
        }
    }
}
