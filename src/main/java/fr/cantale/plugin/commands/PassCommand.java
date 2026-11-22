package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cosmetics.CosmeticDefinition;
import fr.cantale.plugin.cosmetics.CosmeticKind;
import fr.cantale.plugin.cosmetics.SeasonPassRules;
import fr.cantale.plugin.cosmetics.SeasonPassService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PassCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public PassCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        SeasonPassService pass = plugin.getSeasonPass();
        if (pass == null) {
            plugin.messages().send(player, "pass.unavailable");
            return true;
        }
        if (args.length == 0) {
            int xp = pass.xpOf(player.getUniqueId());
            int tier = SeasonPassRules.tierReached(xp);
            int next = tier >= SeasonPassRules.MAX_TIER ? xp : SeasonPassRules.xpRequired(tier + 1);
            SeasonPassService.Look look = pass.look(player.getUniqueId());
            plugin.messages().send(player, "pass.status", tier, xp, next);
            plugin.messages().send(player, "pass.equipped-line",
                    blank(look.titleId()), blank(look.aura()), blank(look.cape()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("equip".equals(sub) || "equiper".equals(sub)) {
            if (args.length < 2) {
                plugin.messages().send(player, "pass.usage");
                return true;
            }
            SeasonPassRules.Equip result = pass.equip(player, args[1]);
            switch (result) {
                case OK -> plugin.messages().send(player, "pass.equipped", args[1]);
                case LOCKED -> plugin.messages().send(player, "pass.locked", args[1]);
                case UNKNOWN -> plugin.messages().send(player, "pass.unknown", args[1]);
            }
            return true;
        }
        if ("unequip".equals(sub) || "retirer".equals(sub)) {
            if (args.length < 2) {
                plugin.messages().send(player, "pass.usage");
                return true;
            }
            CosmeticKind kind = CosmeticKind.fromArgument(args[1]);
            if (kind == null) {
                plugin.messages().send(player, "pass.usage");
                return true;
            }
            if (pass.unequip(player, kind)) {
                plugin.messages().send(player, "pass.unequipped", kind.name().toLowerCase(Locale.ROOT));
            } else {
                plugin.messages().send(player, "pass.none");
            }
            return true;
        }
        plugin.messages().send(player, "pass.usage");
        return true;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("equip", "unequip"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            List<String> ids = new ArrayList<>();
            for (CosmeticDefinition definition : CosmeticDefinition.SEASON_ONE) {
                ids.add(definition.id());
            }
            return filter(ids, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("unequip") || args[0].equalsIgnoreCase("retirer"))) {
            return filter(List.of("title", "aura", "cape"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(needle)).toList();
    }
}
