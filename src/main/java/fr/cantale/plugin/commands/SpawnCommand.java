package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.world.WorldTopology;
import fr.cantale.plugin.world.WorldTransit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final CantalePlugin plugin;

    public SpawnCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        String spawnName = WorldTransit.spawnWorld(
                plugin.getServer().getWorld(WorldTopology.HUB) != null,
                plugin.getServer().getWorld(WorldTopology.LEGACY) != null);
        World world = plugin.getServer().getWorld(spawnName);
        if (world == null || WorldTopology.WARZONE.equals(world.getName())) {
            player.sendMessage("§8[§6Cantale§8] §cLe monde du spawn est introuvable.");
            return true;
        }

        Location spawn = world.getSpawnLocation();
        plugin.getTeleportDelayManager().requestTeleport(player, spawn, false, () ->
                player.sendMessage("§8[§6Cantale§8] §aTéléportation au spawn !"));
        return true;
    }
}
