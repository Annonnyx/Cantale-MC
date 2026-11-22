package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.lang.LanguageCodes;
import fr.cantale.plugin.lang.PlayerLanguageRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class LangCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public LangCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (args.length != 1) {
            plugin.messages().send(player, "lang.usage");
            return true;
        }
        String lang = LanguageCodes.canonical(args[0]);
        if (lang == null) {
            plugin.messages().send(player, "lang.rejected");
            return true;
        }
        plugin.messages().remember(player.getUniqueId(), lang);
        plugin.messages().send(player, "lang.set", lang);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PlayerLanguageRepository.write(conn, player.getUniqueId(), lang);
            } catch (SQLException e) {
                plugin.getLogger().warning("[i18n] Langue non écrite pour " + player.getName() + " : " + e.getMessage());
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of(LanguageCodes.FR, LanguageCodes.EN).stream()
                .filter(code -> code.startsWith(prefix))
                .toList();
    }
}
