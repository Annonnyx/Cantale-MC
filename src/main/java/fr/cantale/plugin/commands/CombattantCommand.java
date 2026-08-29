package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.combattant.CombattantManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CombattantCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public CombattantCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§8[§6Cantale§8] §cPermission refusée (cantale.admin).");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage : /" + label + " <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou hors ligne : §e" + args[0]);
            return true;
        }

        Player staff = sender instanceof Player p ? p : target;
        CombattantManager manager = plugin.getCombattantManager();
        if (manager != null) {
            manager.toggleCombattant(staff, target);
        } else {
            sender.sendMessage("§8[§6Cantale§8] §cCombattantManager indisponible.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.admin")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
