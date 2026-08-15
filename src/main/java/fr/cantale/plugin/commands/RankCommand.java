package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import fr.cantale.plugin.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;

    public RankCommand(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin")) {
            sender.sendMessage("§8[§6Cantale§8] §cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage("§8[§6Cantale§8] §cJoueur introuvable : §e" + targetName);
            return true;
        }

        RankManager rankManager = plugin.getRankManager();

        switch (subCommand) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("§8[§6Cantale§8] §cUsage : /rank set <joueur> <OWNER|ADMIN|MODERATOR|AVENTURIER|VIP|CHEVRE|NONE>");
                    return true;
                }
                PlayerRank rank = PlayerRank.fromString(args[2]);
                // Rétro-compatibilité : si le joueur utilise /rank, on synchronise aussi le rôle PermissionManager
                plugin.getPermissionManager().setPlayerRole(target.getUniqueId(), rank);
                if (rankManager.setRank(target.getUniqueId(), rank)) {
                    sender.sendMessage("§8[§6Cantale§8] §aGrade de §e" + target.getName() + " §adéfini sur §e" + rank.getDisplayName());
                    target.sendMessage("§8[§6Cantale§8] §aVotre grade est maintenant : §e" + rank.getDisplayName());
                    plugin.getTagManager().updatePlayerNametag(target);
                    // Ré-applique immédiatement les perms Bukkit du nouveau grade (staff non-op).
                    plugin.getRankPermissionBridge().refresh(target);
                    if (rank == PlayerRank.CHEVRE) {
                        target.sendMessage("§8[§6Cantale§8] §6Avantages Chèvre : Vol illimité, 100k Cantox/jour, /pc+/pc2, 3 Vies/mois, pas de cooldown TP");
                    } else if (rank == PlayerRank.VIP) {
                        target.sendMessage("§8[§6Cantale§8] §cAvantages VIP : 1 Statio-Lytra/mois, 10k Cantox/jour, /pc 54 slots, 2 Vies/mois");
                    } else if (rank == PlayerRank.AVENTURIER) {
                        target.sendMessage("§8[§6Cantale§8] §bAvantages Aventurier : 2.5k Cantox/jour, 1 Vie/mois, 5 homes, 5 ventes /ah");
                    } else if (rank == PlayerRank.MODERATOR) {
                        target.sendMessage("§8[§6Cantale§8] §9Grade Modo actif.");
                    } else if (rank == PlayerRank.ADMIN) {
                        target.sendMessage("§8[§6Cantale§8] §cGrade Admin actif.");
                    } else if (rank == PlayerRank.OWNER) {
                        target.sendMessage("§8[§6Cantale§8] §4Grade Owner actif.");
                    }
                } else {
                    sender.sendMessage("§8[§6Cantale§8] §cErreur lors de la définition du grade.");
                }
            }
            case "remove" -> {
                if (rankManager.removeRank(target.getUniqueId())) {
                    sender.sendMessage("§8[§6Cantale§8] §aGrade de §e" + target.getName() + " §aretiré.");
                    target.sendMessage("§8[§6Cantale§8] §cVotre grade a été retiré.");
                    // Retire immédiatement les perms Bukkit du grade (sinon actives jusqu'à la déco).
                    plugin.getRankPermissionBridge().refresh(target);
                } else {
                    sender.sendMessage("§8[§6Cantale§8] §cErreur lors du retrait du grade.");
                }
            }
            case "info" -> {
                PlayerRank rank = rankManager.getRank(target.getUniqueId());
                sender.sendMessage("§8[§6Cantale§8] §7Grade de §e" + target.getName() + "§7 : §e" + rank.getDisplayName());
                if (rank != PlayerRank.NONE) {
                    sender.sendMessage("§8[§6Cantale§8] §7  Cantox/jour : §e" + rank.getDailyCantox());
                    sender.sendMessage("§8[§6Cantale§8] §7  /pc slots : §e" + rank.getPcSlots());
                    sender.sendMessage("§8[§6Cantale§8] §7  /pc2 slots : §e" + (rank.getPc2Slots() > 0 ? rank.getPc2Slots() : "—"));
                    sender.sendMessage("§8[§6Cantale§8] §7  Vies/mois : §e" + rank.getMonthlyLives());
                    sender.sendMessage("§8[§6Cantale§8] §7  Homes : §e" + rank.getMaxHomes());
                    sender.sendMessage("§8[§6Cantale§8] §7  Ventes /ah : §e" + rank.getMaxAhListings());
                    sender.sendMessage("§8[§6Cantale§8] §7  Pas de cooldown TP : §e" + (rank.hasNoTpCooldown() ? "Oui" : "Non"));
                    sender.sendMessage("§8[§6Cantale§8] §7  Vol créatif (claims) : §e" + (rank.hasUnlimitedFlight() ? "Oui" : "Non"));
                    sender.sendMessage("§8[§6Cantale§8] §7  Vol coffres (claims étrangers) : §e" + (rank.canStealInClaims() ? "Oui" : "Non"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8[§6Cantale§8] §7Commandes de grade :");
        sender.sendMessage("§8[§6Cantale§8] §e/rank set <joueur> <OWNER|ADMIN|MODERATOR|AVENTURIER|VIP|CHEVRE|NONE> §7— Définir le grade");
        sender.sendMessage("§8[§6Cantale§8] §e/rank remove <joueur> §7— Retirer le grade");
        sender.sendMessage("§8[§6Cantale§8] §e/rank info <joueur> §7— Voir le grade et les avantages");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("cantale.admin")) return completions;

        if (args.length == 1) {
            completions.add("set");
            completions.add("remove");
            completions.add("info");
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            completions.add("OWNER");
            completions.add("ADMIN");
            completions.add("MODERATOR");
            completions.add("AVENTURIER");
            completions.add("VIP");
            completions.add("CHEVRE");
            completions.add("NONE");
        }

        return completions;
    }
}
