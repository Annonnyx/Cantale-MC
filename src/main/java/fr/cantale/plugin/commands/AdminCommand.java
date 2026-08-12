package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.economy.BankManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public AdminCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier si le sender est OP
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cCette commande est réservée aux administrateurs."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "addmoney":
                handleAddMoney(sender, args);
                break;
            case "setmoney":
                handleSetMoney(sender, args);
                break;
            case "deletefaction":
                handleDeleteFaction(sender, args);
                break;
            case "addpower":
                handleAddPower(sender, args);
                break;
            case "addbank":
                handleAddBank(sender, args);
                break;
            case "removebank":
                handleRemoveBank(sender, args);
                break;
            case "deletehome":
                handleDeleteHome(sender, args);
                break;
            case "kickmember":
                handleKickMember(sender, args);
                break;
            case "f":
                handleFactionAdmin(sender, args);
                break;
            default:
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&cSous-commande inconnue. Tape /admin pour voir la liste."));
                break;
        }

        return true;
    }

    private void handleAddMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin addmoney <joueur> <montant>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            plugin.getEconomyManager().deposit(target.getPlayer(), amount);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a" + amount + " Cantox ajoutés à " + target.getName()));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cMontant invalide."));
        }
    }

    private void handleSetMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin setmoney <joueur> <montant>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            double currentBalance = plugin.getEconomyManager().getBalance(target.getUniqueId());
            double difference = amount - currentBalance;
            if (difference > 0) {
                plugin.getEconomyManager().deposit(target.getPlayer(), difference);
            } else if (difference < 0) {
                plugin.getEconomyManager().withdraw(target.getPlayer(), -difference);
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&aSolde de " + target.getName() + " fixé à " + amount + " Cantox"));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cMontant invalide."));
        }
    }

    private void handleDeleteFaction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin deletefaction <faction>"));
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(args[1]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        plugin.getFactionManager().deleteFaction(faction.getId());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&aFaction " + args[1] + " détruite."));
    }

    private void handleAddPower(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin addpower <faction> <montant>"));
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(args[1]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        try {
            int power = Integer.parseInt(args[2]);
            faction.setPower(faction.getPower() + power);
            plugin.getFactionManager().saveFaction(faction);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a" + power + " points de pouvoir ajoutés à " + args[1]));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cMontant invalide."));
        }
    }

    private void handleAddBank(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin addbank <faction> <montant>"));
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(args[1]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            faction.setBalance(faction.getBalance() + amount);
            plugin.getFactionManager().saveFaction(faction);
            UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
            new BankManager(plugin).logTransaction(
                    faction.getId(), actor, BankManager.ACTION_ADMIN_ADD, amount,
                    faction.getBalance(), "Admin addbank par " + sender.getName());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a" + amount + " Cantox ajoutés à la banque de " + args[1]));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cMontant invalide."));
        }
    }

    private void handleRemoveBank(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin removebank <faction> <montant>"));
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(args[1]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            faction.setBalance(faction.getBalance() - amount);
            plugin.getFactionManager().saveFaction(faction);
            UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
            new BankManager(plugin).logTransaction(
                    faction.getId(), actor, BankManager.ACTION_ADMIN_REMOVE, amount,
                    faction.getBalance(), "Admin removebank par " + sender.getName());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a" + amount + " Cantox retirés de la banque de " + args[1]));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cMontant invalide."));
        }
    }

    private void handleDeleteHome(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin deletehome <joueur> <nom>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cLe joueur doit être en ligne pour supprimer son home."));
            return;
        }

        boolean deleted = plugin.getHomeManager().deleteHome(target, args[2]);
        if (deleted) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&aHome " + args[2] + " de " + target.getName() + " supprimé."));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cHome introuvable."));
        }
    }

    private void handleKickMember(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cUsage: /admin kickmember <faction> <joueur>"));
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(args[1]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable."));
            return;
        }

        boolean kicked = plugin.getFactionManager().removeMember(faction.getId(), target.getUniqueId());
        if (kicked) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&a" + target.getName() + " viré de " + args[1]));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&cCe joueur n'est pas dans cette faction."));
        }
    }

    private void handleFactionAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cUsage: /admin f rename <faction> <nouveau_nom>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "rename", "renommer" -> handleRenameFaction(sender, args);
            default -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cSous-commande inconnue. Usage: /admin f rename <faction> <nouveau_nom>"));
        }
    }

    private void handleRenameFaction(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cUsage: /admin f rename <faction> <nouveau_nom>"));
            return;
        }

        FactionManager factionManager = plugin.getFactionManager();
        Faction faction = factionManager.getFaction(args[2]);
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFaction introuvable."));
            return;
        }

        String newName = args[3];
        String oldName = faction.getName();
        String error = factionManager.renameFaction(faction, newName);
        if (error != null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c" + error));
            return;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&aFaction renommée : &e" + oldName + " &a→ &e" + faction.getName()
                + " &7(tag &e[" + faction.getTag() + "]&7)"));
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6[Faction] &eUn admin a renommé la faction en &e"
                        + faction.getName() + " &7[" + faction.getTag() + "]"));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&6=== &lCOMMANDES ADMIN &r&6 ==="));
        sender.sendMessage("");
        
        // Commandes Plugin
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e&lCommandes Plugin :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/cantale reload &7- Recharger la configuration"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/cantale save &7- Sauvegarder les données"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/cantale version &7- Afficher la version du plugin"));
        sender.sendMessage("");
        
        // Commandes Vies
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e&lCommandes Vies :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/addlife <joueur> <nombre> &7- Ajouter des vies à un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/removelife <joueur> <nombre> &7- Retirer des vies à un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/givevie <joueur> &7- Donner une vie à un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/dropvie &7- Transformer une vie en item"));
        sender.sendMessage("");
        
        // Commandes Modération
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e&lCommandes Modération :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/mute <joueur> <durée> <raison> &7- Mute un joueur (ex: 1h, 30m, 1d)"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/unmute <joueur> &7- Unmute un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/kick <joueur> <raison> &7- Kick un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/ban <joueur> <durée> <raison> &7- Bannir un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/unban <joueur> &7- Débannir un joueur"));
        sender.sendMessage("");
        
        // Commandes Wanted
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e&lCommandes Wanted :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/wanted <joueur> <raison> &7- Mettre un joueur en recherche"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/unwanted <joueur> &7- Retirer un joueur de la recherche"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/wantedlist &7- Liste des joueurs recherchés"));
        sender.sendMessage("");
        
        // Commandes Admin Avancées
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e&lCommandes Admin Avancées :"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin addmoney <joueur> <montant> &7- Ajouter de l'argent à un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin setmoney <joueur> <montant> &7- Modifier le solde d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin deletefaction <faction> &7- Détruire une faction"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin addpower <faction> <montant> &7- Ajouter des points de pouvoir"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin addbank <faction> <montant> &7- Ajouter de l'argent à une banque"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin removebank <faction> <montant> &7- Retirer de l'argent d'une banque"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin deletehome <joueur> <nom> &7- Détruire un home privé"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "  &a/admin kickmember <faction> <joueur> &7- Virer un membre d'une faction"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "  &a/admin f rename <faction> <nouveau_nom> &7- Renommer une faction (+ tag)"));
        sender.sendMessage("");
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&6=== &lFIN &r&6 ==="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("addmoney");
            subcommands.add("setmoney");
            subcommands.add("deletefaction");
            subcommands.add("addpower");
            subcommands.add("addbank");
            subcommands.add("removebank");
            subcommands.add("deletehome");
            subcommands.add("kickmember");
            subcommands.add("f");
            return subcommands.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("f")) {
            return List.of("rename", "renommer").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("f")
                && (args[1].equalsIgnoreCase("rename") || args[1].equalsIgnoreCase("renommer"))) {
            String prefix = args[2].toLowerCase();
            return plugin.getFactionManager().getAllFactions().stream()
                .map(Faction::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("deletefaction")
                || args[0].equalsIgnoreCase("addpower")
                || args[0].equalsIgnoreCase("addbank")
                || args[0].equalsIgnoreCase("removebank")
                || args[0].equalsIgnoreCase("kickmember"))) {
            String prefix = args[1].toLowerCase();
            return plugin.getFactionManager().getAllFactions().stream()
                .map(Faction::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
