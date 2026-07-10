package fr.cantale.plugin.ui.chat;

import fr.cantale.plugin.CantalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public class ChatMenu {

    private final CantalePlugin plugin;

    public ChatMenu(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    public static Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text(" [" + text + "] ")
                .color(color)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover).color(NamedTextColor.GRAY)));
    }

    public static Component separator() {
        return Component.text("═══════════════════════════\n").color(NamedTextColor.DARK_GRAY);
    }

    public static Component header(String title) {
        return Component.text("═══ ").color(NamedTextColor.GOLD)
                .append(Component.text(title).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" ═══\n").color(NamedTextColor.GOLD));
    }

    public static void sendMenu(Player player, Component... components) {
        Component message = Component.empty();
        for (Component c : components) {
            message = message.append(c);
        }
        player.sendMessage(message);
    }

    public static Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(value).color(valueColor))
                .append(Component.text("\n"));
    }

    public static Component clickableLine(String label, String command, String hover) {
        return Component.text("  > ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(label).color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(hover).color(NamedTextColor.GRAY))))
                .append(Component.text("\n"));
    }
}
