package fr.cantale.plugin.ui.inventory;

import org.bukkit.entity.Player;

import java.util.*;

public class NavigationManager {

    private final Map<UUID, Deque<BaseUI>> history = new HashMap<>();

    public void openUI(Player player, BaseUI ui) {
        history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(ui);
        ui.open();
    }

    public void goBack(Player player) {
        Deque<BaseUI> stack = history.get(player.getUniqueId());
        if (stack != null && stack.size() > 1) {
            stack.pop();
            BaseUI previous = stack.peek();
            if (previous != null) {
                previous.open();
            }
        }
    }

    public void clearHistory(Player player) {
        history.remove(player.getUniqueId());
    }

    public boolean hasHistory(Player player) {
        Deque<BaseUI> stack = history.get(player.getUniqueId());
        return stack != null && stack.size() > 1;
    }
}
