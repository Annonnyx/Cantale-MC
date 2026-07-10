package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final EconomyManager economyManager;

    public PayCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.economyManager = new EconomyManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /pay <joueur> <montant>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cVous ne pouvez pas vous payer vous-même.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cMontant invalide.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("§8[§6Cantale§8] §cLe montant doit être positif.");
            return true;
        }

        if (!economyManager.hasEnough(player, amount)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas assez de Cantox. (Solde : " + economyManager.getBalance(player) + ")");
            return true;
        }

        if (economyManager.transfer(player, target, amount)) {
            player.sendMessage("§8[§6Cantale§8] §aVous avez payé §e" + target.getName() + " §6" + amount + " §aCantox.");
            target.sendMessage("§8[§6Cantale§8] §e" + player.getName() + " §avous a payé §6" + amount + " §aCantox.");
        } else {
            player.sendMessage("§8[§6Cantale§8] §cTransaction échouée.");
        }

        return true;
    }
}
