package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionPowerShop;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import fr.cantale.plugin.ui.inventory.FactionBankUI;
import fr.cantale.plugin.ui.inventory.FactionPowerShopUI;
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
            openBankUI(player, faction);
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
                    player.sendMessage("§8[§6Cantale§8] §aVous avez déposé §6"
                            + FactionPowerShop.formatMoney(amount) + " §aCantox dans la banque.");
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
                    player.sendMessage("§8[§6Cantale§8] §aVous avez retiré §6"
                            + FactionPowerShop.formatMoney(amount) + " §aCantox de la banque.");
                } else {
                    player.sendMessage("§8[§6Cantale§8] §cRetrait échoué. Banque insuffisante.");
                }
                break;

            default:
                player.sendMessage("§8[§6Cantale§8] §cUsage : /f bank add|take <montant> | /f bank power [nombre]");
        }

        return true;
    }

    private void openBankUI(Player player, Faction faction) {
        if (!factionManager.hasPermission(player.getUniqueId(), FactionPermission.INFO_BANK)
                && !faction.isLeader(player.getUniqueId())
                && !factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_ADD)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas accès à la banque de faction.");
            return;
        }
        plugin.getUIManager().getNavigationManager().openUI(player, new FactionBankUI(plugin, player, faction));
    }

    private void handlePowerBuy(Player player, Faction faction, String[] args) {
        // Sans nombre → GUI (tous les membres : info ; achat gated dans l'UI)
        if (args.length < 2) {
            plugin.getUIManager().getNavigationManager()
                    .openUI(player, new FactionPowerShopUI(plugin, player, faction));
            return;
        }

        boolean canBuy = faction.isLeader(player.getUniqueId())
                || factionManager.hasPermission(player.getUniqueId(), FactionPermission.BANK_TAKE)
                || faction.isOfficier(player.getUniqueId());
        if (!canBuy) {
            player.sendMessage("§8[§6Cantale§8] §cRéservé aux officiers et au chef.");
            return;
        }

        int points;
        try {
            points = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§8[§6Cantale§8] §cNombre invalide.");
            return;
        }

        int max = plugin.getConfigManager().getPowerBuyMaxPerPurchase();
        if (points < 1 || points > max) {
            player.sendMessage("§8[§6Cantale§8] §cTu peux acheter entre §e1 §cet §e" + max
                    + " §cunités à la fois (§e1 §c→ §b+" + powerShop.powerPerUnit() + " §cpouvoir).");
            return;
        }

        FactionPowerShop.PurchaseOutcome outcome = powerShop.buy(faction, player.getUniqueId(), points);
        FactionPowerShop.Quote q = outcome.quote();

        switch (outcome.result()) {
            case OK -> {
                player.sendMessage("§8[§6Cantale§8] §aAchat réussi : §b+" + q.powerGranted()
                        + " pouvoir §7(§e" + q.units() + " §7unité"
                        + (q.units() > 1 ? "s" : "") + "§7) §apour §6"
                        + FactionPowerShop.formatMoney(q.totalCost()) + " §aCantox.");
                player.sendMessage("§7Nouveau pouvoir : §b" + faction.getPower()
                        + " §7· claims max : §e" + faction.getMaxClaims()
                        + " §7· prochaine unité : §6"
                        + FactionPowerShop.formatMoney(powerShop.nextPointCost(faction)));
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage("§8[§6Cantale§8] §cBanque insuffisante. Coût : §6"
                    + FactionPowerShop.formatMoney(q.totalCost()) + " §cCantox (solde : §6"
                    + FactionPowerShop.formatMoney(faction.getBalance()) + "§c).");
            case INVALID_AMOUNT -> player.sendMessage("§8[§6Cantale§8] §cNombre invalide (1–" + max + ").");
            case NO_FACTION -> player.sendMessage("§8[§6Cantale§8] §cFaction introuvable.");
        }
    }
}
