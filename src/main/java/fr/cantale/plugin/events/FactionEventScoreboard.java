package fr.cantale.plugin.events;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidebar scoreboard pour les événements faction.
 * Utilise un scoreboard par joueur pendant l'événement, puis restaure le main scoreboard.
 */
public class FactionEventScoreboard {

    private static final String OBJECTIVE_NAME = "cantale_fevt";

    private final CantalePlugin plugin;
    private final Map<UUID, Scoreboard> previous = new ConcurrentHashMap<>();

    public FactionEventScoreboard(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void showForAll(FactionEventManager manager) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player, manager);
        }
    }

    public void updateAll(FactionEventManager manager) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!previous.containsKey(player.getUniqueId())) {
                show(player, manager);
            } else {
                render(player.getScoreboard(), manager);
            }
        }
    }

    public void show(Player player, FactionEventManager manager) {
        previous.putIfAbsent(player.getUniqueId(), player.getScoreboard());
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                ChatColor.translateAlternateColorCodes('&', "&6&lEvent Faction"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        render(board, manager);
    }

    private void render(Scoreboard board, FactionEventManager manager) {
        if (board == null) return;
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        // Clear previous scores
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        String typeLine = manager.getEventTitle();
        if (typeLine == null || typeLine.isBlank()) typeLine = "?";
        // Sidebar entries are capped (~40 chars); keep title short
        if (typeLine.length() > 22) {
            typeLine = typeLine.substring(0, 22);
        }
        long remainingSec = Math.max(0, (manager.getEndsAtMs() - System.currentTimeMillis()) / 1000L);
        long mins = remainingSec / 60;
        long secs = remainingSec % 60;

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.YELLOW + typeLine);
        lines.add(ChatColor.YELLOW + "Temps: " + ChatColor.WHITE + String.format("%d:%02d", mins, secs));
        lines.add(ChatColor.DARK_GRAY + "--------------");
        lines.add(ChatColor.GOLD + "Top factions:");

        List<Map.Entry<Integer, Integer>> ranking = new ArrayList<>(manager.getFactionPointsSnapshot().entrySet());
        ranking.sort(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed());

        int shown = 0;
        for (Map.Entry<Integer, Integer> entry : ranking) {
            if (shown >= 5) break;
            String name = manager.getFactionName(entry.getKey());
            if (name.length() > 12) name = name.substring(0, 12);
            lines.add(ChatColor.GRAY + "#" + (shown + 1) + " " + ChatColor.AQUA + name
                    + ChatColor.WHITE + " " + entry.getValue());
            shown++;
        }
        if (shown == 0) {
            lines.add(ChatColor.DARK_GRAY + "Aucun point…");
        }

        // Scores must be unique descending for display order
        int score = lines.size();
        for (String line : lines) {
            // Ensure unique entry keys
            String entry = line;
            int suffix = 0;
            while (board.getEntries().contains(entry)) {
                entry = line + ChatColor.values()[suffix++ % ChatColor.values().length];
            }
            objective.getScore(entry).setScore(score--);
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restore(player);
        }
        previous.clear();
    }

    private void restore(Player player) {
        Scoreboard prev = previous.remove(player.getUniqueId());
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        player.setScoreboard(prev != null ? prev : main);
        if (plugin.getTagManager() != null) {
            plugin.getTagManager().updatePlayerNametag(player);
        }
    }
}
