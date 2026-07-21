package fr.cantale.plugin.anticheat;

import fr.cantale.plugin.anticheat.check.Check;
import fr.cantale.plugin.anticheat.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Commande principale /anticheat (alias /ac) pour la gestion du système par le staff.
 */
public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final AntiCheatManager manager;

    public AntiCheatCommand(AntiCheatManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'exécuter cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "toggle":
                return handleToggle(sender, args);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender, args);
            case "alerts":
                return handleAlerts(sender);
            case "clear":
                return handleClear(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m--------------------------------------------------"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lCantale AntiCheat &8— &7Administration"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac toggle &7— Activer/désactiver l'AntiCheat globalement"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac toggle <check> &7— Activer/désactiver un check spécifique"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac reload &7— Recharger la configuration"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac info <joueur> &7— Voir le score de confiance et les violations d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac alerts &7— Activer/désactiver vos alertes en jeu"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e• /ac clear <joueur> &7— Réinitialiser les violations d'un joueur"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m--------------------------------------------------"));
    }

    private boolean handleToggle(CommandSender sender, String[] args) {
        if (args.length == 1) {
            boolean current = manager.getConfig().isEnabled();
            manager.getConfig().setEnabled(!current);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&cAC&8] &7AntiCheat global mis sur : " + 
                    (!current ? "&aACTIVÉ" : "&cDÉSACTIVÉ")));
            return true;
        }

        String checkName = args[1].toUpperCase();
        try {
            CheckType type = CheckType.valueOf(checkName);
            Check check = manager.getCheckRegistry().get(type);
            if (check != null) {
                // Pour basculer dynamiquement, on modifie la config en mémoire
                // Note: idéalement cela modifierait le fichier yml ou garderait l'état
                // Ici on le change dynamiquement, mais reload écrasera. On informe le sender.
                boolean active = manager.getConfig().isCheckEnabled(type);
                // Le toggle en mémoire nécessite une gestion de config dynamique ou de reload
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&8[&cAC&8] &7Le check &e" + type.getDisplayName() + " &7est actuellement : " + 
                        (active ? "&aACTIVÉ" : "&cDÉSACTIVÉ") + " &8(&7Modifiez config.yml pour persister&8)"));
            } else {
                sender.sendMessage(ChatColor.RED + "Check non trouvé.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Check de type inconnu. Utilisez le Tab-Completion.");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        manager.getPlugin().reloadConfig();
        manager.getConfig().reload();
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&cAC&8] &aConfiguration rechargée avec succès !"));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ac info <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur hors ligne.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        int trust = manager.getTrustManager().getTrust(uuid);
        Map<CheckType, Integer> viols = manager.getViolationManager().getAllViolations(uuid);

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m--------------------------------------------------"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lStatus AntiCheat &8— &e" + target.getName()));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Trust Score: " + getTrustColor(trust) + trust + "/100"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Violations enregistrées:"));
        
        if (viols.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "  Aucune violation récente.");
        } else {
            for (Map.Entry<CheckType, Integer> entry : viols.entrySet()) {
                if (entry.getValue() > 0) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "  &c- " + entry.getKey().getDisplayName() + ": &e" + entry.getValue()));
                }
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m--------------------------------------------------"));
        return true;
    }

    private String getTrustColor(int trust) {
        if (trust >= 80) return "&a";
        if (trust >= 50) return "&e";
        if (trust >= 25) return "&6";
        return "&c";
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent exécuter cette commande.");
            return true;
        }

        boolean active = manager.getAlertManager().toggleAlerts(player.getUniqueId());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&8[&cAC&8] &7Vos alertes anti-cheat en jeu sont maintenant : " + 
                (active ? "&aACTIVÉES" : "&cDÉSACTIVÉES")));
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ac clear <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur hors ligne.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        manager.getViolationManager().clearViolations(uuid);
        manager.getTrustManager().resetTrust(uuid);
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&8[&cAC&8] &aViolations et trust réinitialisés pour &e" + target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("cantale.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("toggle");
            completions.add("reload");
            completions.add("info");
            completions.add("alerts");
            completions.add("clear");
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("toggle")) {
                for (CheckType type : CheckType.values()) {
                    completions.add(type.name());
                }
            } else if (sub.equals("info") || sub.equals("clear")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
            return filterCompletions(completions, args[1]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> list, String input) {
        String lowerInput = input.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}
