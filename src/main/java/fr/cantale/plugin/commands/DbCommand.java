package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DbCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public DbCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cPermission refusée.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reset")) {
            sender.sendMessage("§cUsage: /db reset §7— vide toutes les tables du plugin");
            return true;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§c⚠ Cette action est IRRÉVERSIBLE. Tape §f/db reset confirm §cpour confirmer.");
            return true;
        }

        sender.sendMessage("§8[§6Cantale§8] §7Réinitialisation de la base de données en cours...");

        boolean ok = plugin.getDatabaseManager().resetAllTables();
        if (ok) {
            sender.sendMessage("§8[§6Cantale§8] §aBase de données réinitialisée. §7Redémarre le serveur pour recharger les caches.");
            plugin.getLogger().warning("DB reset effectué par " + sender.getName());
        } else {
            sender.sendMessage("§8[§6Cantale§8] §cErreur lors du reset. Voir console.");
        }
        return true;
    }
}
