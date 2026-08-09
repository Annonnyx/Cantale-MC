package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.wanted.Wanted;
import fr.cantale.plugin.wanted.WantedManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WantedCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final WantedManager wantedManager;

    public WantedCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.wantedManager = new WantedManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            showWantedList(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /wanted add <joueur> <prix> <raison>");
                    return true;
                }
                handleAdd(player, args);
                break;
            case "remove":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /wanted remove <joueur> [raison]");
                    return true;
                }
                handleRemove(player, args);
                break;
            case "list":
                showWantedList(player);
                break;
            default:
                player.sendMessage("§7/wanted - Voir les primes");
                player.sendMessage("§7/wanted add <joueur> <prix> <raison> - Mettre une prime");
                player.sendMessage("§7/wanted remove <joueur> <raison> - Retirer une prime");
                break;
        }
        return true;
    }

    private void handleAdd(Player issuer, String[] args) {
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            issuer.sendMessage("§cJoueur introuvable.");
            return;
        }

        double reward;
        try {
            reward = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            issuer.sendMessage("§cPrix invalide.");
            return;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        
        if (wantedManager.addWanted(issuer, target, reward, reason)) {
            issuer.sendMessage("§aPrime de §6" + reward + " Cantox §amise sur §e" + target.getName());
        }
    }

    private void handleRemove(Player issuer, String[] args) {
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            issuer.sendMessage("§cJoueur introuvable.");
            return;
        }

        String reason = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "";

        if (wantedManager.removeWanted(target, reason)) {
            issuer.sendMessage("§aPrime(s) retirée(s) pour §e" + target.getName() + (reason.isBlank() ? "" : " §7(" + reason + ")"));
        } else {
            issuer.sendMessage("§cAucune prime active pour ce joueur.");
        }
    }

    private void showWantedList(Player player) {
        new fr.cantale.plugin.ui.inventory.WantedUI(plugin, player).open();
    }

    public WantedManager getWantedManager() {
        return wantedManager;
    }
}
