package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

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
        String name = command.getName().toLowerCase();

        return switch (name) {
            case "dropvie" -> handleDropVie(sender);
            case "givevie" -> handleGiveVie(sender, args);
            case "addlife" -> handleAddLife(sender, args);
            case "removelife" -> handleRemoveLife(sender, args);
            default -> false;
        };
    }

    private boolean handleDropVie(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(format("&c/dropvie est réservé aux joueurs (transforme 1 vie en item)."));
            sender.sendMessage(format("&7Depuis la console : &eaddlife <joueur> <nombre> &7ou &egivevie <joueur>"));
            return true;
        }

        if (lifeManager.getLives(player.getUniqueId()) <= 1) {
            player.sendMessage(format("&cVous n'avez plus assez de vies pour faire ça !"));
            return true;
        }

        lifeManager.removeLife(player.getUniqueId());
        CustomItemManager itemManager = plugin.getCustomItemManager();
        ItemStack vieItem = itemManager.createCustomItem(CustomItemType.VIE);
        player.getInventory().addItem(vieItem);
        player.sendMessage(format("&aVous avez transformé une vie en item &eVie&a."));
        player.sendMessage(format("&7Vies restantes : &c" + lifeManager.getLives(player.getUniqueId())));
        return true;
    }

    /**
     * Joueur → joueur : transfert (coûte 1 vie).
     * Console / staff admin → donne 1 vie sans coût (équivalent addlife 1).
     */
    private boolean handleGiveVie(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(format("&cUsage : /givevie <joueur>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(format("&cJoueur introuvable (doit être en ligne)."));
            return true;
        }

        // Console / RCON : grant admin
        if (isConsole(sender)) {
            ensurePlayerRow(target);
            lifeManager.addLife(target.getUniqueId(), 1);
            // Si le joueur était à 0, le pardonner du ban vies
            pardonLifeBan(target);
            sender.sendMessage(format("&a[Console] +1 vie pour &e" + target.getName()
                + " &7(total : &e" + lifeManager.getLives(target.getUniqueId()) + "&7)."));
            target.sendMessage(format("&aTu as reçu &e1 &avie (console) !"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(format("&cUsage console : givevie <joueur>"));
            return true;
        }

        // Staff avec permission admin : grant sans coût
        if (player.hasPermission("cantale.admin") && args.length >= 1
            && !player.getUniqueId().equals(target.getUniqueId())
            && !lifeManager.canGiveLife(player.getUniqueId(), target.getUniqueId())) {
            // Si le staff n'a pas assez de vies perso, permettre le grant admin
            ensurePlayerRow(target);
            lifeManager.addLife(target.getUniqueId(), 1);
            pardonLifeBan(target);
            player.sendMessage(format("&a[Admin] +1 vie pour &e" + target.getName() + "&a."));
            target.sendMessage(format("&aTu as reçu &e1 &avie (staff) !"));
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(format("&cVous ne pouvez pas vous donner une vie à vous-même."));
            return true;
        }

        if (!lifeManager.canGiveLife(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(format("&cVous n'avez pas assez de vies pour en donner une (minimum 2 requis)."));
            player.sendMessage(format("&7Astuce staff : utilise &e/addlife <joueur> <nombre>&7."));
            return true;
        }

        if (lifeManager.transferLife(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(format("&aVous avez donné une vie à &e" + target.getName() + "&a."));
            target.sendMessage(format("&a" + player.getName() + " vous a donné une vie !"));
            player.sendMessage(format("&7Vies restantes : &c" + lifeManager.getLives(player.getUniqueId())));
        }
        return true;
    }

    private boolean handleAddLife(CommandSender sender, String[] args) {
        if (!canAdmin(sender)) {
            sender.sendMessage(format("&cCette commande est réservée aux administrateurs / console."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(format("&cUsage : /addlife <joueur> <nombre>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            // Offline : créer/charger via UUID si possible
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (offline.getUniqueId() == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                sender.sendMessage(format("&cJoueur introuvable."));
                return true;
            }
            try {
                int amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    sender.sendMessage(format("&cLe nombre doit être positif."));
                    return true;
                }
                UUID uuid = offline.getUniqueId();
                playerDAO.createOrUpdatePlayer(uuid, offline.getName() != null ? offline.getName() : args[0]);
                lifeManager.addLife(uuid, amount);
                sender.sendMessage(format("&aAjouté &e" + amount + " &avies à &e"
                    + (offline.getName() != null ? offline.getName() : args[0])
                    + " &7(hors-ligne, total : &e" + lifeManager.getLives(uuid) + "&7)."));
            } catch (NumberFormatException e) {
                sender.sendMessage(format("&cNombre invalide."));
            }
            return true;
        }

        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                sender.sendMessage(format("&cLe nombre doit être positif."));
                return true;
            }

            ensurePlayerRow(target);
            int before = lifeManager.getLives(target.getUniqueId());
            lifeManager.addLife(target.getUniqueId(), amount);
            if (before <= 0) {
                pardonLifeBan(target);
            }

            sender.sendMessage(format("&aAjouté &e" + amount + " &avies à &e" + target.getName()
                + " &7(total : &e" + lifeManager.getLives(target.getUniqueId()) + "&7)."));
            target.sendMessage(format("&aVous avez reçu &e" + amount + " &avies !"));
        } catch (NumberFormatException e) {
            sender.sendMessage(format("&cNombre invalide."));
        }
        return true;
    }

    private boolean handleRemoveLife(CommandSender sender, String[] args) {
        if (!canAdmin(sender)) {
            sender.sendMessage(format("&cCette commande est réservée aux administrateurs / console."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(format("&cUsage : /removelife <joueur> <nombre>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(format("&cJoueur introuvable (doit être en ligne)."));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                sender.sendMessage(format("&cLe nombre doit être positif."));
                return true;
            }

            int currentLives = lifeManager.getLives(target.getUniqueId());
            if (amount > currentLives) {
                sender.sendMessage(format("&cLe joueur n'a que &e" + currentLives + " &cvies."));
                return true;
            }

            for (int i = 0; i < amount; i++) {
                lifeManager.removeLife(target.getUniqueId());
            }

            sender.sendMessage(format("&aRetiré &e" + amount + " &avies à &e" + target.getName()
                + " &7(total : &e" + lifeManager.getLives(target.getUniqueId()) + "&7)."));
            target.sendMessage(format("&cVous avez perdu &e" + amount + " &cvies."));
        } catch (NumberFormatException e) {
            sender.sendMessage(format("&cNombre invalide."));
        }
        return true;
    }

    private void ensurePlayerRow(Player target) {
        playerDAO.createOrUpdatePlayer(target.getUniqueId(), target.getName());
    }

    private void pardonLifeBan(Player target) {
        try {
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(target.getName());
        } catch (Exception ignored) {
            // best effort
        }
        try {
            // Paper : lever le ban profil UUID
            target.getPlayerProfile(); // ensure loaded
            Bukkit.getServer().getBanList(org.bukkit.BanList.Type.PROFILE)
                .pardon(target.getName());
        } catch (Exception ignored) {
            // best effort — certaines versions n'acceptent que le profil typé
        }
    }

    private static boolean isConsole(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }

    private static boolean canAdmin(CommandSender sender) {
        return isConsole(sender) || sender.isOp() || sender.hasPermission("cantale.admin");
    }

    private String format(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
