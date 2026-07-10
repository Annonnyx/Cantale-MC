package fr.cantale.plugin.tag;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TagCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final TagManager tagManager;

    public TagCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.tagManager = plugin.getTagManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.moderator")) {
            sender.sendMessage(formatMessage("&cVous n'avez pas la permission d'utiliser cette commande."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "add", "give" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(formatMessage("&cUsage: /tag create <id> <nom_affichage>"));
            return;
        }

        String tagId = args[1].toLowerCase();
        // Joindre le reste des arguments pour le display name afin de supporter les espaces
        String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (tagManager.createCustomTag(tagId, displayName)) {
            sender.sendMessage(formatMessage("&aLe tag customisé &e" + tagId + " &a(" + ChatColor.translateAlternateColorCodes('&', displayName) + "&a) a été créé avec succès."));
        } else {
            sender.sendMessage(formatMessage("&cErreur lors de la création du tag."));
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(formatMessage("&cUsage: /tag delete <id>"));
            return;
        }

        String tagId = args[1].toLowerCase();
        if (tagManager.deleteCustomTag(tagId)) {
            sender.sendMessage(formatMessage("&aLe tag &e" + tagId + " &aa été supprimé."));
        } else {
            sender.sendMessage(formatMessage("&cCe tag n'existe pas."));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(formatMessage("&cUsage: /tag add <joueur> <id>"));
            return;
        }

        String targetName = args[1];
        String tagId = args[2].toLowerCase();

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(formatMessage("&cJoueur introuvable."));
            return;
        }

        String displayName = tagManager.getCustomTagDisplayName(tagId);
        if (displayName == null) {
            sender.sendMessage(formatMessage("&cLe tag &e" + tagId + " &cn'existe pas."));
            return;
        }

        if (tagManager.unlockTag(target.getUniqueId(), tagId)) {
            sender.sendMessage(formatMessage("&aLe tag &e" + tagId + " &aa été attribué à &e" + target.getName() + "&a."));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(formatMessage("&aVous avez débloqué le tag : " + ChatColor.translateAlternateColorCodes('&', displayName)));
            }
        } else {
            sender.sendMessage(formatMessage("&cErreur lors de l'attribution du tag."));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(formatMessage("&cUsage: /tag remove <joueur> <id>"));
            return;
        }

        String targetName = args[1];
        String tagId = args[2].toLowerCase();

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(formatMessage("&cJoueur introuvable."));
            return;
        }

        if (tagManager.lockTag(target.getUniqueId(), tagId)) {
            sender.sendMessage(formatMessage("&aLe tag &e" + tagId + " &aa été retiré à &e" + target.getName() + "&a."));
            // Si le joueur avait ce tag actif, on l'enlève
            String active = tagManager.getActiveCustomTag(target.getUniqueId());
            if (tagId.equalsIgnoreCase(active)) {
                tagManager.setActiveCustomTag(target.getUniqueId(), null);
                if (target.isOnline() && target.getPlayer() != null) {
                    tagManager.updatePlayerNametag(target.getPlayer());
                }
            }
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(formatMessage("&cLe tag &e" + tagId + " &cvous a été retiré."));
            }
        } else {
            sender.sendMessage(formatMessage("&cCe joueur ne possède pas ce tag."));
        }
    }

    private void handleList(CommandSender sender) {
        Map<String, String> tags = tagManager.getAllCustomTags();
        if (tags.isEmpty()) {
            sender.sendMessage(formatMessage("&cAucun tag customisé disponible."));
            return;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6=== Tags Customisés disponibles ==="));
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            sender.sendMessage(formatMessage("&e• &b" + entry.getKey() + " &7- " + entry.getValue()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[§6Cantale§8] &7=== Modération des Tags ==="));
        sender.sendMessage(formatMessage("&7/tag create <id> <nom_affichage> &e- Créer un tag"));
        sender.sendMessage(formatMessage("&7/tag delete <id> &e- Supprimer un tag"));
        sender.sendMessage(formatMessage("&7/tag add <joueur> <id> &e- Donner un tag"));
        sender.sendMessage(formatMessage("&7/tag remove <joueur> <id> &e- Retirer un tag"));
        sender.sendMessage(formatMessage("&7/tag list &e- Voir la liste des tags"));
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cantale.moderator")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("create", "delete", "add", "give", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("remove") || sub.equals("add") || sub.equals("give")) {
                if (sub.equals("delete")) {
                    return new ArrayList<>(tagManager.getAllCustomTags().keySet());
                }
                // Pour add, give, remove on propose les joueurs en ligne
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("give")) {
                // Proposer tous les tags existants
                return new ArrayList<>(tagManager.getAllCustomTags().keySet());
            }
            if (sub.equals("remove")) {
                // Proposer les tags du joueur cible
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target != null) {
                    return tagManager.getUnlockedTags(target.getUniqueId());
                }
            }
        }

        return Collections.emptyList();
    }
}
