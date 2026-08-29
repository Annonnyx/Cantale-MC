package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.teleport.FWarp;
import fr.cantale.plugin.teleport.FWarpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conservé du commit {@code check} (table {@code fwarps}).
 * La commande live {@code /fw} est {@link FwCommand} (table historique {@code faction_warps} :
 * quota, claim, UI faction). Cette classe n'est plus enregistrée dans {@code plugin.yml}.
 */
public class FWarpCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_NAME_LENGTH = 32;
    /** Noms réservés aux sous-commandes, interdits comme nom de fw. */
    private static final List<String> RESERVED_NAMES = Arrays.asList("create", "delete", "del", "remove", "list", "help");

    private final CantalePlugin plugin;
    private final FWarpManager fwarpManager;

    public FWarpCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.fwarpManager = plugin.getFWarpManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            fwarpManager.listFWarps(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            handleCreate(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("remove")) {
            handleDelete(player, args);
            return true;
        }

        fwarpManager.teleportFWarp(player, args[0]);
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /fw create <nom>");
            return;
        }

        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Cantale§8] §cVous devez être dans une faction pour créer un warp de faction.");
            return;
        }
        if (!faction.isOfficier(player.getUniqueId())) {
            player.sendMessage("§8[§6Cantale§8] §cSeuls les officiers et le chef peuvent créer un warp de faction.");
            return;
        }

        String name = args[1];
        if (name.length() > MAX_NAME_LENGTH) {
            player.sendMessage("§8[§6Cantale§8] §cNom trop long (max " + MAX_NAME_LENGTH + " caractères).");
            return;
        }
        if (RESERVED_NAMES.contains(name.toLowerCase())) {
            player.sendMessage("§8[§6Cantale§8] §cCe nom est réservé.");
            return;
        }
        if (fwarpManager.findFWarp(name) != null) {
            player.sendMessage("§8[§6Cantale§8] §cUn warp de faction porte déjà ce nom.");
            return;
        }

        if (fwarpManager.createFWarp(name, faction, player)) {
            player.sendMessage("§8[§6Cantale§8] §aWarp de faction §e" + name + " §acréé ! Tous les joueurs peuvent s'y téléporter avec §e/fw " + name + "§a.");
        } else {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de créer ce warp (nom déjà pris ?).");
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8[§6Cantale§8] §cUsage : /fw delete <nom>");
            return;
        }

        FWarp fwarp = fwarpManager.findFWarp(args[1]);
        if (fwarp == null) {
            player.sendMessage("§8[§6Cantale§8] §cWarp de faction inexistant.");
            return;
        }
        if (!fwarpManager.canManage(player, fwarp)) {
            player.sendMessage("§8[§6Cantale§8] §cSeuls les officiers+ de la faction propriétaire peuvent supprimer ce warp.");
            return;
        }

        if (fwarpManager.deleteFWarp(fwarp.getName())) {
            player.sendMessage("§8[§6Cantale§8] §aWarp de faction §e" + fwarp.getName() + " §asupprimé !");
        } else {
            player.sendMessage("§8[§6Cantale§8] §cErreur lors de la suppression.");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8[§6Cantale§8] §7=== Warps de faction (/fw) ===");
        player.sendMessage("§e/fw §7- Liste les warps de faction");
        player.sendMessage("§e/fw <nom> §7- Se téléporter (ouvert à tous)");
        player.sendMessage("§e/fw create <nom> §7- Créer un warp à ta position §8(officier+)");
        player.sendMessage("§e/fw delete <nom> §7- Supprimer un warp de ta faction §8(officier+)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> options = new ArrayList<>(Arrays.asList("create", "delete", "list", "help"));
            options.addAll(fwarpManager.getFWarps().stream().map(FWarp::getName).toList());
            return options.stream()
                    .filter(o -> o.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("remove"))) {
            String prefix = args[1].toLowerCase();
            if (sender instanceof Player player) {
                return fwarpManager.getFWarps().stream()
                        .filter(w -> fwarpManager.canManage(player, w))
                        .map(FWarp::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
