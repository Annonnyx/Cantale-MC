package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.optimization.PerformanceGovernor;
import fr.cantale.plugin.optimization.ServerOptimizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
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
                sender.sendMessage(formatMessage("&aSauvegarde des données joueurs effectuée !"));
            }
            case "on" -> {
                optimizer.setEnabled(true);
                sender.sendMessage(formatMessage("&aOptimisation serveur activée (persisté)."));
            }
            case "off" -> {
                optimizer.setEnabled(false);
                sender.sendMessage(formatMessage("&cOptimisation serveur désactivée (persisté)."));
            }
            case "reload" -> {
                optimizer.loadConfig();
                if (plugin.getMobStackManager() != null) {
                    plugin.getMobStackManager().loadConfig();
                    plugin.getMobStackManager().start();
                }
                if (plugin.getItemMergeManager() != null) {
                    plugin.getItemMergeManager().loadConfig();
                    plugin.getItemMergeManager().start();
                }
                if (plugin.getPerformanceGovernor() != null) {
                    plugin.getPerformanceGovernor().loadConfig();
                    plugin.getPerformanceGovernor().start();
                }
                if (plugin.getChunkLoader() != null) {
                    plugin.getChunkLoader().loadConfig();
                }
                sender.sendMessage(formatMessage("&aConfiguration rechargée (optimizer + mobstack + itemmerge + governor + chunks)."));
            }
            case "villagers", "villager" -> handleVillagers(sender, args);
            case "clearall" -> handleClearAll(sender, args);
            case "clearlag" -> handleClearLag(sender, args);
            case "governor" -> showGovernor(sender);
            case "mode" -> handleMode(sender, args);
            case "hotspots" -> handleHotspots(sender, args);
            default -> sender.sendMessage(formatMessage("&cSous-commande inconnue. " +
                    "Utilisez /optimize stats|hotspots|governor|mode|cleanup|clearall|save|villagers|clearlag|on|off|reload"));
        }

        return true;
    }

    private void handleClearLag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(formatMessage("&eIntervalle ClearLag actuel : &6" + plugin.getClearLagTask().getInterval() + " &esecondes."));
            sender.sendMessage(formatMessage("&7Prochain clear dans : &e" + plugin.getClearLagTask().getCountdown() + " &esecondes."));
            sender.sendMessage(formatMessage("&7Items récents protégés : &e" + plugin.getClearLagTask().getProtectRecentSeconds() + " &esecondes."));
            sender.sendMessage(formatMessage("&7Usage : /optimize clearlag <secondes>"));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            if (seconds < 60) {
                sender.sendMessage(formatMessage("&cIntervalle trop court. Minimum 60 secondes."));
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

    /** /optimize governor — état du gouverneur de performance. */
    private void showGovernor(CommandSender sender) {
        PerformanceGovernor governor = plugin.getPerformanceGovernor();
        if (governor == null) {
            sender.sendMessage(formatMessage("&cGouverneur non initialisé."));
            return;
        }
        sender.sendMessage(formatMessage("&6=== Gouverneur de performance ==="));
        for (String line : governor.getStatusLines()) {
            sender.sendMessage(formatMessage(line));
        }
        sender.sendMessage(formatMessage("&7Forcer un mode : &e/optimize mode <auto|normal|degraded|critical>"));
    }

    /** /optimize mode <auto|normal|degraded|critical> — override manuel du governor. */
    private void handleMode(CommandSender sender, String[] args) {
        PerformanceGovernor governor = plugin.getPerformanceGovernor();
        if (governor == null) {
            sender.sendMessage(formatMessage("&cGouverneur non initialisé."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(formatMessage("&eMode courant : &6" + governor.getMode()
                    + (governor.getForcedMode() != null ? " &7(forcé)" : " &7(auto)")));
            sender.sendMessage(formatMessage("&7Usage : /optimize mode <auto|normal|degraded|critical>"));
            return;
        }
        String target = args[1].toLowerCase();
        if (target.equals("auto")) {
            governor.setForcedMode(null);
            sender.sendMessage(formatMessage("&aMode automatique rétabli (mode courant : &e" + governor.getMode() + "&a)."));
            return;
        }
        try {
            PerformanceGovernor.Mode forced = PerformanceGovernor.Mode.valueOf(target.toUpperCase());
            governor.setForcedMode(forced);
            sender.sendMessage(formatMessage("&eMode forcé : &6" + forced
                    + " &7(distances/cadences ajustées — &f/optimize mode auto&7 pour revenir en auto)"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(formatMessage("&cMode inconnu. Utilisez auto|normal|degraded|critical"));
        }
    }

    /** Snapshot d'un chunk pour l'analyse de hotspots. */
    private record ChunkStat(String world, int x, int z, int entities, int tileEntities) {
        int score() {
            return entities + tileEntities;
        }
    }

    /**
     * /optimize hotspots [n] — top N des chunks les plus chargés (entités + tile entities).
     * Snapshot sync (lecture mémoire des chunks déjà chargés), tri hors thread principal.
     */
    private void handleHotspots(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Math.max(1, Math.min(25, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                sender.sendMessage(formatMessage("&cNombre invalide. Usage : /optimize hotspots [1-25]"));
                return;
            }
        }

        List<ChunkStat> snapshot = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int entities = chunk.getEntities().length;
                int tiles = chunk.getTileEntities().length;
                if (entities + tiles == 0) continue;
                snapshot.add(new ChunkStat(world.getName(), chunk.getX(), chunk.getZ(), entities, tiles));
            }
        }

        if (snapshot.isEmpty()) {
            sender.sendMessage(formatMessage("&aAucun chunk chargé avec des entités."));
            return;
        }

        sender.sendMessage(formatMessage("&7Analyse de &e" + snapshot.size() + " &7chunks…"));
        final int top = limit;
        plugin.getAsyncTaskManager().runAsyncThenSync(
                () -> snapshot.sort((a, b) -> Integer.compare(b.score(), a.score())),
                () -> {
                    sender.sendMessage(formatMessage("&6=== Hotspots (top " + Math.min(top, snapshot.size()) + ") ==="));
                    sender.sendMessage(formatMessage("&8monde · x,z (blocs) · entités · tile entities"));
                    for (int i = 0; i < Math.min(top, snapshot.size()); i++) {
                        ChunkStat stat = snapshot.get(i);
                        sender.sendMessage(formatMessage("&e#" + (i + 1) + " &7" + stat.world()
                                + " &f" + (stat.x() * 16 + 8) + "," + (stat.z() * 16 + 8)
                                + " &7— &c" + stat.entities() + " ent. &8/ &6" + stat.tileEntities() + " tiles"));
                    }
                });
    }

    private void showStats(CommandSender sender) {
        Map<String, Object> stats = optimizer.getStats();

        sender.sendMessage(formatMessage("&6=== Optimisation Serveur ==="));
        sender.sendMessage(formatMessage("&7TPS (1m/5m/15m) : &e" + fmt1(stats.get("tps"))
                + " &7/ &e" + fmt1(stats.get("tps_5m")) + " &7/ &e" + fmt1(stats.get("tps_15m"))));
        double mspt = stats.get("mspt") instanceof Number n ? n.doubleValue() : -1;
        if (mspt >= 0) {
            sender.sendMessage(formatMessage("&7MSPT moyen : &e" + String.format("%.1f", mspt) + " &8(50 = limite 20 TPS)"));
        }
        PerformanceGovernor governor = plugin.getPerformanceGovernor();
        if (governor != null) {
            sender.sendMessage(formatMessage("&7Gouverneur : &e" + governor.getMode()
                    + (governor.getForcedMode() != null ? " &7(forcé)" : " &7(auto)")
                    + (governor.isRunning() ? "" : " &c· inactif")));
        }
        sender.sendMessage(formatMessage("&7Joueurs : &e" + stats.getOrDefault("online_players", 0)
                + "/" + stats.getOrDefault("max_players", 0)));
        sender.sendMessage(formatMessage("&7Mémoire : &e" + stats.getOrDefault("used_memory_mb", 0)
                + "MB / " + stats.getOrDefault("max_memory_mb", 0) + "MB"
                + " &8(GC : " + stats.getOrDefault("gc_count", 0L) + " collections, "
                + stats.getOrDefault("gc_time_ms", 0L) + " ms cumulés)"));
        sender.sendMessage(formatMessage("&7Chunks chargés : &e" + stats.getOrDefault("loaded_chunks", 0)
                + " &8· &7Entités : &e" + stats.getOrDefault("total_entities", 0)));

        Object worldsObj = stats.get("worlds");
        if (worldsObj instanceof Map<?, ?> worlds) {
            for (Map.Entry<?, ?> entry : worlds.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> w)) continue;
                sender.sendMessage(formatMessage("&8- &f" + entry.getKey()
                        + " &7chunks &e" + w.get("chunks")
                        + " &7ent. &e" + w.get("entities")
                        + " &7tiles &e" + w.get("tile_entities")
                        + " &8sim " + w.get("sim_distance") + " / view " + w.get("view_distance")));
            }
        }

        sender.sendMessage(formatMessage("&7Optimisation : " + (optimizer.isEnabled() ? "&aActivée" : "&cDésactivée")
                + " &8· &7Dernier cleanup : &e" + stats.getOrDefault("last_cleanup_items", 0) + " items, "
                + stats.getOrDefault("last_cleanup_arrows", 0) + " flèches, "
                + stats.getOrDefault("last_cleanup_excess", 0) + " surplus"));

        double tps = stats.get("tps") instanceof Number n ? n.doubleValue() : 20.0;
        if (tps < 15) {
            sender.sendMessage(formatMessage("&cTPS bas — regardez /optimize hotspots pour localiser la source."));
        }
    }

    private String fmt1(Object value) {
        if (value instanceof Number n) {
            return String.format("%.1f", n.doubleValue());
        }
        return "20.0";
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Arrays.asList("stats", "hotspots", "governor", "mode", "cleanup", "clearall", "save",
                            "villagers", "clearlag", "on", "off", "reload")
                    .stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("villagers") || sub.equals("villager")) return Arrays.asList("on", "off", "remove");
            if (sub.equals("clearall")) return List.of("named");
            if (sub.equals("mode")) return Arrays.asList("auto", "normal", "degraded", "critical");
        }
        return Collections.emptyList();
    }
}
