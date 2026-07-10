package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LifeCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final LifeManager lifeManager;
    private final PlayerDAO playerDAO;

    public LifeCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.lifeManager = plugin.getLifeManager();
        this.playerDAO = new PlayerDAO(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dropvie")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(formatMessage("&cCette commande est réservée aux joueurs."));
                return true;
            }

            if (lifeManager.getLives(player.getUniqueId()) <= 1) {
                player.sendMessage(formatMessage("&cVous n'avez plus assez de vies pour faire ça !"));
                return true;
            }

            lifeManager.removeLife(player.getUniqueId());

            // Create custom VIE item (not old NETHER_STAR)
            CustomItemManager itemManager = plugin.getCustomItemManager();
            ItemStack vieItem = itemManager.createCustomItem(CustomItemType.VIE);

            player.getInventory().addItem(vieItem);
            player.sendMessage(formatMessage("&aVous avez transformé une vie en item &eVie&a."));
            player.sendMessage(formatMessage("&7Vies restantes : &c" + lifeManager.getLives(player.getUniqueId())));
            return true;
        }

        if (command.getName().equalsIgnoreCase("givevie")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(formatMessage("&cCette commande est réservée aux joueurs."));
                return true;
            }

            if (args.length < 1) {
                player.sendMessage(formatMessage("&cUsage : /givevie <joueur>"));
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(formatMessage("&cJoueur introuvable."));
                return true;
            }

            if (player.getUniqueId().equals(target.getUniqueId())) {
                player.sendMessage(formatMessage("&cVous ne pouvez pas vous donner une vie à vous-même."));
                return true;
            }

            if (!lifeManager.canGiveLife(player.getUniqueId(), target.getUniqueId())) {
                player.sendMessage(formatMessage("&cVous n'avez pas assez de vies pour en donner une (minimum 2 requis)."));
                return true;
            }

            if (lifeManager.transferLife(player.getUniqueId(), target.getUniqueId())) {
                player.sendMessage(formatMessage("&aVous avez donné une vie à &e" + target.getName() + "&a."));
                target.sendMessage(formatMessage("&a" + player.getName() + " vous a donné une vie !"));
                player.sendMessage(formatMessage("&7Vies restantes : &c" + lifeManager.getLives(player.getUniqueId())));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("addlife")) {
            if (!sender.isOp()) {
                sender.sendMessage(formatMessage("&cCette commande est réservée aux administrateurs."));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(formatMessage("&cUsage : /addlife <joueur> <nombre>"));
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(formatMessage("&cJoueur introuvable."));
                return true;
            }

            try {
                int amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    sender.sendMessage(formatMessage("&cLe nombre doit être positif."));
                    return true;
                }

                lifeManager.addLife(target.getUniqueId(), amount);

                sender.sendMessage(formatMessage("&aAjouté &e" + amount + " &avies à &e" + target.getName() + "&a."));
                target.sendMessage(formatMessage("&aVous avez reçu &e" + amount + " &avies !"));
            } catch (NumberFormatException e) {
                sender.sendMessage(formatMessage("&cNombre invalide."));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("removelife")) {
            if (!sender.isOp()) {
                sender.sendMessage(formatMessage("&cCette commande est réservée aux administrateurs."));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(formatMessage("&cUsage : /removelife <joueur> <nombre>"));
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(formatMessage("&cJoueur introuvable."));
                return true;
            }

            try {
                int amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    sender.sendMessage(formatMessage("&cLe nombre doit être positif."));
                    return true;
                }

                int currentLives = lifeManager.getLives(target.getUniqueId());
                if (amount > currentLives) {
                    sender.sendMessage(formatMessage("&cLe joueur n'a que &e" + currentLives + " &cvies."));
                    return true;
                }

                for (int i = 0; i < amount; i++) {
                    lifeManager.removeLife(target.getUniqueId());
                }

                sender.sendMessage(formatMessage("&aRetiré &e" + amount + " &avies à &e" + target.getName() + "&a."));
                target.sendMessage(formatMessage("&cVous avez perdu &e" + amount + " &cvies."));
            } catch (NumberFormatException e) {
                sender.sendMessage(formatMessage("&cNombre invalide."));
            }
            return true;
        }

        return false;
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
