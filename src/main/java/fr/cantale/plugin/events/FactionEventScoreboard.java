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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sidebar événement faction sur le <strong>scoreboard principal</strong>.
 * Un scoreboard par joueur masquait les préfixes TAB / nametag (équipes du main).
 */
public class FactionEventScoreboard {

    private static final String OBJECTIVE_NAME = "cantale_fevt";

    private final CantalePlugin plugin;
    private final Set<String> sidebarEntries = new HashSet<>();

    public FactionEventScoreboard(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public void showForAll(FactionEventManager manager) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective existing = main.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            existing.unregister();
        }
        sidebarEntries.clear();
        Objective objective = main.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                ChatColor.translateAlternateColorCodes('&', "&6&lEvent Faction"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        ensurePlayersOnMain(main);
        render(main, manager);
    }

    public void updateAll(FactionEventManager manager) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (main.getObjective(OBJECTIVE_NAME) == null) {
            showForAll(manager);
            return;
        }
        ensurePlayersOnMain(main);
        render(main, manager);
    }

    public void show(Player player, FactionEventManager manager) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (main.getObjective(OBJECTIVE_NAME) == null) {
            showForAll(manager);
            return;
        }
        if (player.getScoreboard() != main) {
            player.setScoreboard(main);
        }
        if (plugin.getTagManager() != null) {
            plugin.getTagManager().updatePlayerNametag(player);
        }
    }

    private void ensurePlayersOnMain(Scoreboard main) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getScoreboard() != main) {
                player.setScoreboard(main);
            }
        }
    }

    private void render(Scoreboard board, FactionEventManager manager) {
        if (board == null) return;
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        for (String entry : sidebarEntries) {
            board.resetScores(entry);
        }
        sidebarEntries.clear();

        String typeLine = manager.getEventTitle();
        if (typeLine == null || typeLine.isBlank()) typeLine = "?";
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

        int score = lines.size();
        for (String line : lines) {
            String entry = line;
            int suffix = 0;
            while (board.getEntries().contains(entry) && !sidebarEntries.contains(entry)) {
                entry = line + ChatColor.values()[suffix++ % ChatColor.values().length];
            }
            objective.getScore(entry).setScore(score--);
            sidebarEntries.add(entry);
        }
    }

    public void clearAll() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = main.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        for (String entry : sidebarEntries) {
            main.resetScores(entry);
        }
        sidebarEntries.clear();

        ensurePlayersOnMain(main);
        if (plugin.getTagManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getTagManager().updatePlayerNametag(player);
            }
        }
    }
}
