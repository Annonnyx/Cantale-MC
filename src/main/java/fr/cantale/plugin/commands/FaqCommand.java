package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.chatbot.faq.FaqCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Commande admin in-game pour gérer la FAQ.
 * /faq reload
 * /faq add <id> <triggers> <réponse>
 * /faq remove <id>
 */
public class FaqCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final FaqCore faqCore;

    public FaqCommand(CantalePlugin plugin, FaqCore faqCore) {
        this.plugin = plugin;
        this.faqCore = faqCore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage(format("&cVous n'avez pas la permission."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        faqCore.reload();
        refreshDiscordChannels();
        sender.sendMessage(format("&aFAQ rechargée (&e" + faqCore.getEntries().size() + " &aentrées)."));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(format("&cUsage : /faq add <id> <trigger1,trigger2,...> <réponse>"));
            return;
        }

        String id = args[1].toLowerCase();
        String triggersRaw = args[2];
        String response = Arrays.stream(args, 3, args.length).collect(Collectors.joining(" "));

        List<String> triggers = Arrays.stream(triggersRaw.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        if (triggers.isEmpty()) {
            sender.sendMessage(format("&cAu moins un trigger est requis."));
            return;
        }

        File file = new File(plugin.getDataFolder(), "faq.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        List<Map<String, Object>> entries = loadEntriesMap(yaml);
        entries.removeIf(e -> id.equalsIgnoreCase(String.valueOf(e.get("id"))));

        Map<String, Object> newEntry = new LinkedHashMap<>();
        newEntry.put("id", id);
        newEntry.put("triggers", new ArrayList<>(triggers));
        newEntry.put("response", response);
        newEntry.put("enabled", true);
        entries.add(newEntry);

        yaml.set("entries", entries);
        try {
            yaml.save(file);
        } catch (IOException e) {
            sender.sendMessage(format("&cErreur lors de la sauvegarde de faq.yml : " + e.getMessage()));
            return;
        }

        faqCore.reload();
        refreshDiscordChannels();
        sender.sendMessage(format("&aEntrée &e" + id + " &aajoutée/rechargée."));
    }

    private void refreshDiscordChannels() {
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().getFaqDiscordListener() != null) {
            plugin.getDiscordBot().getFaqDiscordListener().refreshAllowedChannels();
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(format("&cUsage : /faq remove <id>"));
            return;
        }
        String id = args[1].toLowerCase();

        File file = new File(plugin.getDataFolder(), "faq.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        List<Map<String, Object>> entries = loadEntriesMap(yaml);
        boolean removed = entries.removeIf(e -> id.equalsIgnoreCase(String.valueOf(e.get("id"))));

        if (!removed) {
            sender.sendMessage(format("&cAucune entrée trouvée avec l'id &e" + id + "&c."));
            return;
        }

        yaml.set("entries", entries);
        try {
            yaml.save(file);
        } catch (IOException e) {
            sender.sendMessage(format("&cErreur lors de la sauvegarde de faq.yml : " + e.getMessage()));
            return;
        }

        faqCore.reload();
        refreshDiscordChannels();
        sender.sendMessage(format("&aEntrée &e" + id + " &asupprimée."));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntriesMap(YamlConfiguration yaml) {
        Object raw = yaml.get("entries");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> map) {
                    Map<String, Object> casted = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        casted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(casted);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(format("&6=== Commandes FAQ ==="));
        sender.sendMessage(format("&e/faq reload &7— Recharger faq.yml"));
        sender.sendMessage(format("&e/faq add <id> <trigger1,trigger2,...> <réponse> &7— Ajouter une entrée"));
        sender.sendMessage(format("&e/faq remove <id> &7— Supprimer une entrée"));
    }

    private String format(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
