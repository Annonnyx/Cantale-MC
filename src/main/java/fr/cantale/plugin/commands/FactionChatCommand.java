package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FactionChatCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final FactionCommand factionCommand;

    public FactionChatCommand(CantalePlugin plugin, FactionCommand factionCommand) {
        this.plugin = plugin;
        this.factionCommand = factionCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "c";
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        factionCommand.handleChat(player, fullArgs);
        return true;
    }
}
