package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RaidCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public RaidCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        org.bukkit.Location loc = player.getLocation();
        Claim claim = plugin.getFactionManager().getClaimAt(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
        if (claim == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getMessagePrefix() + "&cTu dois être dans un claim ennemi pour déclarer un raid."));
            return true;
        }

        plugin.getRaidManager().startRaid(player, claim);
        return true;
    }
}
