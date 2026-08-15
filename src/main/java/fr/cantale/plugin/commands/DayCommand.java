package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.OverworldDay;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DayCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public DayCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        World world = OverworldDay.resolveOverworld(plugin.getServer());
        if (world == null) {
            sender.sendMessage(formatMessage("&cAucun monde overworld n'est chargé."));
            return true;
        }

        long day = OverworldDay.currentDay(world);
        long timeOfDay = OverworldDay.timeOfDay(world);

        int hours = (int) ((timeOfDay / 1000 + 6) % 24);
        int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);

        String timeString = String.format("%02d:%02d", hours, minutes);
        String period;

        if (hours >= 6 && hours < 12) {
            period = "Matin";
        } else if (hours >= 12 && hours < 18) {
            period = "Après-midi";
        } else if (hours >= 18 && hours < 22) {
            period = "Soir";
        } else {
            period = "Nuit";
        }

        sender.sendMessage(formatMessage("&6=== Temps Minecraft ==="));
        sender.sendMessage(formatMessage("&7Monde: &e" + world.getName()));
        sender.sendMessage(formatMessage("&7Jour: &e" + day));
        sender.sendMessage(formatMessage("&7Heure: &e" + timeString + " &7(" + period + ")"));

        return true;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
