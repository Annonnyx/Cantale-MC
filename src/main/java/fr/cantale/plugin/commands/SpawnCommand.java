package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final CooldownManager cooldownManager;
    private static final long COOLDOWN_MS = 20000; // 20 secondes

    public SpawnCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        Player player = (Player) sender;

        // Vérifier le cooldown
        if (cooldownManager.hasCooldown(player, "spawn", COOLDOWN_MS)) {
            long remaining = cooldownManager.getRemainingCooldown(player, "spawn", COOLDOWN_MS);
            player.sendMessage("§cCooldown ! Attendez encore " + (remaining / 1000) + " secondes.");
            return true;
        }

        Location spawn = plugin.getServer().getWorld("world").getSpawnLocation();
        
        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(spawn);
            player.sendMessage("§8[§6Cantale§8] §aTéléportation au spawn !");
            cooldownManager.setCooldown(player, "spawn");
        });
        
        return true;
    }
}
