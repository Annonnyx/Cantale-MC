package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PasdicCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public PasdicCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cCette commande est réservée aux administrateurs."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cUsage: /pasdic set <true|false>"));
            return true;
        }

        if (!args[0].equalsIgnoreCase("set")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cUsage: /pasdic set <true|false>"));
            return true;
        }

        boolean pasdic;
        if (args[1].equalsIgnoreCase("true")) {
            pasdic = true;
        } else if (args[1].equalsIgnoreCase("false")) {
            pasdic = false;
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cValeur invalide. Utilisez true ou false."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cCette commande doit être exécutée par un joueur."));
            return true;
        }

        Player player = (Player) sender;
        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        String world = player.getWorld().getName();

        FactionManager factionManager = plugin.getFactionManager();
        Claim claim = factionManager.getClaimAt(world, chunkX, chunkZ);

        if (claim == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cCe chunk n'est pas claim par une faction."));
            return true;
        }

        Faction faction = factionManager.getFaction(claim.getFactionId());
        if (faction == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cErreur: faction introuvable."));
            return true;
        }

        Chunk loaded = player.getChunk();
        String error = plugin.getPasdicService().setPasdic(claim, pasdic, loaded);
        if (error != null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c" + error));
            return true;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&aChunk défini en PASDIC: " + pasdic + " pour la faction " + faction.getName()));

        return true;
    }
}
