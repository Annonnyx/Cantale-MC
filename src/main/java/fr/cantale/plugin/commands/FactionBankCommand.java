package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FactionBankCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final BankManager bankManager;

    public FactionBankCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.bankManager = new BankManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'êtes pas dans une faction.");
            return true;
        }

        if (args.length < 2) {
            if (!factionManager.hasPermission(player.getUniqueId(), FactionPermission.INFO_BANK)
                    && !faction.isLeader(player.getUniqueId())) {
                player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas accès à la banque de faction.");
                return true;
            }
            player.sendMessage("§8[§6Cantale§8] §7=== Banque de §e" + faction.getName() + " §7===");
            player.sendMessage("§7Solde : §6" + bankManager.getBalance(faction.getId()) + " §7Cantox");
            player.sendMessage("§7Usage : /f bank add <montant> | /f bank take <montant>");
            return true;
        }

        String action = args[0].toLowerCase();
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

        boolean hasBankAddPerm = factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_ADD);
        boolean hasBankTakePerm = factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_TAKE);
        boolean isLeader = faction.isLeader(player.getUniqueId());

        switch (action) {
            case "add":
            case "deposit":
                if (!hasBankAddPerm && !isLeader) {
                    player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas la permission de déposer.");
                    return true;
                }
                if (bankManager.deposit(faction.getId(), player.getUniqueId(), amount)) {
                    player.sendMessage("§8[§6Cantale§8] §aVous avez déposé §6" + amount + " §aCantox dans la banque.");
                } else {
                    player.sendMessage("§8[§6Cantale§8] §cDépôt échoué. Vérifiez votre solde.");
                }
                break;

            case "take":
            case "withdraw":
                if (!hasBankTakePerm && !isLeader) {
                    player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas la permission de retirer.");
                    return true;
                }
                if (bankManager.withdraw(faction.getId(), player.getUniqueId(), amount)) {
                    player.sendMessage("§8[§6Cantale§8] §aVous avez retiré §6" + amount + " §aCantox de la banque.");
                } else {
                    player.sendMessage("§8[§6Cantale§8] §cRetrait échoué. Banque insuffisante.");
                }
                break;

            default:
                player.sendMessage("§8[§6Cantale§8] §cUsage : /f bank add/take <montant>");
        }

        return true;
    }
}
