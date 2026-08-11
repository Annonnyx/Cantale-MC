package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionPowerShop;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FactionBankCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final BankManager bankManager;
    private final FactionPowerShop powerShop;

    public FactionBankCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.bankManager = new BankManager(plugin);
        this.powerShop = new FactionPowerShop(plugin, bankManager);
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

        if (args.length == 0) {
            showBankInfo(player, faction);
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("power") || action.equals("pouvoir")) {
            handlePowerBuy(player, faction, args);
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /f bank add|take <montant> | /f bank power [nombre]");
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
                    player.sendMessage("§8[§6Cantale§8] §aVous avez déposé §6" + formatMoney(amount) + " §aCantox dans la banque.");
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
                    player.sendMessage("§8[§6Cantale§8] §aVous avez retiré §6" + formatMoney(amount) + " §aCantox de la banque.");
                } else {
                    player.sendMessage("§8[§6Cantale§8] §cRetrait échoué. Banque insuffisante.");
                }
                break;

            default:
                player.sendMessage("§8[§6Cantale§8] §cUsage : /f bank add|take <montant> | /f bank power [nombre]");
        }

        return true;
    }

    private void showBankInfo(Player player, Faction faction) {
        if (!factionManager.hasPermission(player.getUniqueId(), FactionPermission.INFO_BANK)
                && !faction.isLeader(player.getUniqueId())
                && !factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_ADD)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas accès à la banque de faction.");
            return;
        }
        double next = powerShop.nextPointCost(faction);
        player.sendMessage("§8[§6Cantale§8] §7=== Banque de §e" + faction.getName() + " §7===");
        player.sendMessage("§7Solde : §6" + formatMoney(bankManager.getBalance(faction.getId())) + " §7Cantox");
        player.sendMessage("§7Pouvoir : §b" + faction.getPower()
                + " §7(claims max §e" + faction.getMaxClaims() + "§7)");
        player.sendMessage("§7Prochain point de pouvoir : §6" + formatMoney(next) + " §7Cantox");
        player.sendMessage("§7Usage : §e/f bank add|take <montant> §7| §e/f bank power [nombre]");
    }

    private void handlePowerBuy(Player player, Faction faction, String[] args) {
        boolean canBuy = faction.isLeader(player.getUniqueId())
                || factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_TAKE)
                || faction.isOfficier(player.getUniqueId());
        if (!canBuy) {
            player.sendMessage("§8[§6Cantale§8] §cRéservé aux officiers et au chef.");
            return;
        }

        int points = 1;
        if (args.length >= 2) {
            try {
                points = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§8[§6Cantale§8] §cNombre invalide.");
                return;
            }
        }

        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        if (points < 1 || points > max) {
            player.sendMessage("§8[§6Cantale§8] §cTu peux acheter entre §e1 §cet §e" + max + " §cpoints à la fois.");
            return;
        }

        FactionPowerShop.PurchaseOutcome outcome = powerShop.buy(faction, player.getUniqueId(), points);
        FactionPowerShop.Quote q = outcome.quote();

        switch (outcome.result()) {
            case OK -> {
                player.sendMessage("§8[§6Cantale§8] §aAchat réussi : §b+" + q.points() + " pouvoir §apour §6"
                        + formatMoney(q.totalCost()) + " §aCantox.");
                player.sendMessage("§7Nouveau pouvoir : §b" + faction.getPower()
                        + " §7· claims max : §e" + faction.getMaxClaims()
                        + " §7· prochain point : §6" + formatMoney(powerShop.nextPointCost(faction)));
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage("§8[§6Cantale§8] §cBanque insuffisante. Coût : §6"
                    + formatMoney(q.totalCost()) + " §cCantox (solde : §6"
                    + formatMoney(faction.getBalance()) + "§c).");
            case INVALID_AMOUNT -> player.sendMessage("§8[§6Cantale§8] §cNombre invalide (1–" + max + ").");
            case NO_FACTION -> player.sendMessage("§8[§6Cantale§8] §cFaction introuvable.");
        }
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 10_000) {
            return String.format("%.0fk", amount / 1000.0);
        }
        if (Math.floor(amount) == amount) {
            return String.format("%.0f", amount);
        }
        return String.format("%.2f", amount);
    }
}
