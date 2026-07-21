package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateManager;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CrateCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CrateManager crateManager;

    public CrateCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§cTu n'as pas la permission.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§8[§6Cantale§8] §cUsage: /crate give <joueur> <vote|rare|epic|mythic|legendary> [quantité]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
            return true;
        }

        CrateType type;
        try {
            type = CrateType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§8[§6Cantale§8] §cType invalide. Utilise: vote, rare, epic, mythic, legendary.");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§8[§6Cantale§8] §cQuantité invalide.");
                return true;
            }
        }

        var crateItem = crateManager.createCrateItem(type);
        crateItem.setAmount(amount);
        var leftover = target.getInventory().addItem(crateItem);
        if (!leftover.isEmpty()) {
            for (var drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
        }

        sender.sendMessage("§8[§6Cantale§8] §a" + amount + "x " + type.getDisplayName() + " donné(s) à §e" + target.getName() + "§a.");
        target.sendMessage("§8[§6Cantale§8] §aTu as reçu §e" + amount + "x " + type.getDisplayName() + "§a.");
        return true;
    }
}
