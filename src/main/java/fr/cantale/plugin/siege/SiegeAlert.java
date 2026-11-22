package fr.cantale.plugin.siege;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.discord.DiscordBot;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.awt.Color;

/**
 * Alerte sonore pour tout le serveur, et Discord si le salon factions est configuré.
 * Sans salon ou sans bot : un log explicite, pas d'exception.
 */
public final class SiegeAlert {

    private SiegeAlert() {
    }

    public static void announce(CantalePlugin plugin, Faction attacker, Faction defender, int durationMinutes) {
        String attackerName = attacker == null ? null : attacker.getName();
        String defenderName = defender == null ? null : defender.getName();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(player, "siege.banner",
                    attackerName == null ? plugin.messages().raw(player, "siege.someone") : attackerName,
                    defenderName == null ? plugin.messages().raw(player, "siege.someone") : defenderName,
                    durationMinutes);
        }
        Sound horn = horn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), horn, 1.0f, 1.0f);
        }
        notifyDiscord(plugin, attackerName, defenderName, durationMinutes);
    }

    private static void notifyDiscord(CantalePlugin plugin, String attackerName, String defenderName, int minutes) {
        DiscordBot bot = plugin.getDiscordBot();
        if (bot == null || !bot.isConnected() || bot.getConfig() == null) {
            plugin.getLogger().warning("[Siège] Notification Discord non envoyée : bot absent ou déconnecté.");
            return;
        }
        long channel = bot.getConfig().getMcFactionsChannelId();
        if (channel == 0L) {
            plugin.getLogger().warning("[Siège] Notification Discord non envoyée : salon factions non configuré.");
            return;
        }
        bot.sendEmbedToChannel(
                channel,
                Color.RED,
                "Bannière de siège",
                "La faction **" + attackerName + "** a planté une bannière de siège contre **"
                        + defenderName + "** (" + minutes + " min)."
        );
    }

    private static Sound horn() {
        try {
            return Sound.valueOf("EVENT_RAID_HORN");
        } catch (IllegalArgumentException ex) {
            return Sound.ENTITY_ENDER_DRAGON_GROWL;
        }
    }
}
