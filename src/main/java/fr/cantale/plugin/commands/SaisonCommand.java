package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.quests.SeasonManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SaisonCommand implements CommandExecutor {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CantalePlugin plugin;

    public SaisonCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SeasonManager seasons = plugin.getQuestEngine().seasons();
        if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("cantale.admin")) {
                sender.sendMessage("§cPermission refusée.");
                return true;
            }
            if (seasons.startNew("admin:" + sender.getName())) {
                plugin.getQuestEngine().refreshPools();
                Bukkit.broadcastMessage("§8[§6Cantale§8] §dUne nouvelle saison commence !");
            }
            return true;
        }
        ZonedDateTime end = ZonedDateTime.ofInstant(Instant.ofEpochSecond(seasons.endsAt()), SeasonManager.PARIS);
        sender.sendMessage("§8[§6Cantale§8] §7Saison §e#" + seasons.currentId()
                + " §7jusqu'au §e" + FMT.format(end) + " §7(Paris).");
        return true;
    }
}
