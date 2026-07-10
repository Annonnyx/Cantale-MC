package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.optimization.ServerOptimizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class OptimizeCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final ServerOptimizer optimizer;

    public OptimizeCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.optimizer = plugin.getServerOptimizer();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage(formatMessage("&cVous n'avez pas la permission."));
            return true;
        }

        if (args.length == 0) {
            showStats(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats" -> showStats(sender);
            case "cleanup" -> {
                optimizer.cleanupEntities();
                sender.sendMessage(formatMessage("&aNettoyage des entités effectué !"));
            }
            case "save" -> {
                optimizer.autoSave();
                sender.sendMessage(formatMessage("&aSauvegarde effectuée !"));
            }
            case "chunks" -> {
                optimizer.optimizeChunks();
                sender.sendMessage(formatMessage("&aOptimisation des chunks effectuée !"));
            }
            case "gc" -> {
                System.gc();
                sender.sendMessage(formatMessage("&aGarbage Collector appelé."));
            }
            case "on" -> {
                optimizer.setEnabled(true);
                sender.sendMessage(formatMessage("&aOptimisation serveur activée."));
            }
            case "off" -> {
                optimizer.setEnabled(false);
                sender.sendMessage(formatMessage("&cOptimisation serveur désactivée."));
            }
            case "reload" -> {
                optimizer.loadConfig();
                sender.sendMessage(formatMessage("&aConfiguration rechargée."));
            }
            case "villagers", "villager" -> handleVillagers(sender, args);
            case "clearall" -> handleClearAll(sender, args);
            case "clearlag" -> handleClearLag(sender, args);
            default -> sender.sendMessage(formatMessage("&cSous-commande inconnue. " +
                    "Utilisez /optimize stats|cleanup|clearall|save|chunks|villagers|clearlag|gc|on|off|reload"));
        }

        return true;
    }

    private void handleClearLag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(formatMessage("&eIntervalle ClearLag actuel : &6" + plugin.getClearLagTask().getInterval() + " &esecondes."));
            sender.sendMessage(formatMessage("&7Prochain clear dans : &e" + plugin.getClearLagTask().getCountdown() + " &esecondes."));
            sender.sendMessage(formatMessage("&7Usage : /optimize clearlag <secondes>"));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            if (seconds < 10) {
                sender.sendMessage(formatMessage("&cIntervalle trop court. Minimum 10 secondes."));
                return;
            }
            plugin.getClearLagTask().setInterval(seconds);
            sender.sendMessage(formatMessage("&aIntervalle ClearLag défini à &e" + seconds + " &asecondes (sauvegardé et appliqué)."));
        } catch (NumberFormatException e) {
            sender.sendMessage(formatMessage("&cNombre invalide. Usage : /optimize clearlag <secondes>"));
        }
    }

    /**
     * /optimize villagers           → status + nb actuel
     * /optimize villagers off       → désactive le spawn de villageois + supprime existants (persisté)
     * /optimize villagers on        → ré-autorise le spawn (persisté)
     * /optimize villagers remove    → supprime tous les villageois actuels sans toucher à la flag
     */
    private void handleVillagers(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(formatMessage("&6=== Villageois ==="));
            sender.sendMessage(formatMessage("&7Spawn désactivé : " + (optimizer.isVillagersDisabled() ? "&aOUI" : "&cNON")));
            sender.sendMessage(formatMessage("&7Usage : /optimize villagers <on|off|remove>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "off", "disable" -> {
                int removed = optimizer.setVillagersDisabled(true);
                sender.sendMessage(formatMessage("&aSpawn de villageois désactivé (persisté). &7" + removed + " supprimés."));
            }
            case "on", "enable" -> {
                optimizer.setVillagersDisabled(false);
                sender.sendMessage(formatMessage("&aSpawn de villageois ré-autorisé."));
            }
            case "remove", "clear" -> {
                int removed = optimizer.removeAllVillagers();
                sender.sendMessage(formatMessage("&a" + removed + " villageois supprimés."));
            }
            default -> sender.sendMessage(formatMessage("&cUsage : /optimize villagers <on|off|remove>"));
        }
    }

    /**
     * /optimize clearall            → clear tout sauf joueurs, tames et nommées
     * /optimize clearall named      → clear tout sauf joueurs et tames (même les nommées)
     */
    private void handleClearAll(CommandSender sender, String[] args) {
        boolean includeNamed = args.length >= 2 && (args[1].equalsIgnoreCase("named") || args[1].equalsIgnoreCase("all"));
        int removed = optimizer.forceClearEntities(includeNamed);
        sender.sendMessage(formatMessage("&a" + removed + " entités supprimées — joueurs et bêtes apprivoisées préservés"
                + (includeNamed ? " &7(entités nommées incluses)" : " &7(entités nommées préservées)")));
    }

    private void showStats(CommandSender sender) {
        Map<String, Object> stats = optimizer.getStats();

        sender.sendMessage(formatMessage("&6=== Optimisation Serveur ==="));
        sender.sendMessage(formatMessage("&7TPS: &e" + String.format("%.1f", (double) stats.getOrDefault("tps", 20.0))));
        sender.sendMessage(formatMessage("&7Joueurs: &e" + stats.getOrDefault("online_players", 0) + "/" + stats.getOrDefault("max_players", 0)));
        sender.sendMessage(formatMessage("&7Mémoire: &e" + stats.getOrDefault("used_memory_mb", 0) + "MB / " + stats.getOrDefault("max_memory_mb", 0) + "MB"));
        sender.sendMessage(formatMessage("&7Chunks chargés: &e" + stats.getOrDefault("loaded_chunks", 0)));
        sender.sendMessage(formatMessage("&7Entités totales: &e" + stats.getOrDefault("total_entities", 0)));
        sender.sendMessage(formatMessage("&7Optimisation: " + (optimizer.isEnabled() ? "&aActivée" : "&cDésactivée")));

        double tps = (double) stats.getOrDefault("tps", 20.0);
        if (tps < 15) {
            sender.sendMessage(formatMessage("&c⚠ TPS bas détecté ! Utilisez /optimize cleanup"));
        }
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Arrays.asList("stats", "cleanup", "clearall", "save", "chunks", "villagers", "clearlag", "gc", "on", "off", "reload")
                    .stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("villagers") || sub.equals("villager")) return Arrays.asList("on", "off", "remove");
            if (sub.equals("clearall")) return Arrays.asList("named");
        }
        return Collections.emptyList();
    }
}
