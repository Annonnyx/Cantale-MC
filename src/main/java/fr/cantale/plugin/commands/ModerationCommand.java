package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.cooldown.CooldownManager;
import fr.cantale.plugin.discord.DiscordBot;
import fr.cantale.plugin.moderation.MuteManager;
import fr.cantale.plugin.moderation.VanishManager;
import fr.cantale.plugin.moderation.ModerationTraceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ModerationCommand implements CommandExecutor {

    private final CantalePlugin plugin;
    private final MuteManager muteManager;
    private final VanishManager vanishManager;
    private final ModerationTraceManager traceManager;
    private final CooldownManager cooldownManager;
    private static final long COOLDOWN_MS = 60000; // 1 minute

    public ModerationCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.muteManager = new MuteManager(plugin);
        this.vanishManager = new VanishManager(plugin);
        this.traceManager = new ModerationTraceManager(plugin);
        this.cooldownManager = plugin.getCooldownManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cantale.admin") && !sender.hasPermission("cantale.moderator")) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            // Activer/désactiver le mode trace
            if (traceManager.isInTrace(player.getUniqueId())) {
                // Désactivation - pas de cooldown
                java.io.File report = traceManager.deactivateTrace(player);
                if (report != null) {
                    DiscordBot bot = plugin.getDiscordBot();
                    if (bot != null && bot.isConnected()) {
                        bot.sendFileToChannel(1505562281470201916L, report,
                            "📋 Rapport de modération — " + player.getName());
                    }
                }
            } else {
                // Activation - vérifier le cooldown
                if (cooldownManager.hasModerationCooldown(player, COOLDOWN_MS)) {
                    long remaining = cooldownManager.getRemainingModerationCooldown(player, COOLDOWN_MS);
                    player.sendMessage("§cCooldown ! Attendez encore " + (remaining / 1000) + " secondes.");
                    return true;
                }
                traceManager.activateTrace(player);
                cooldownManager.setModerationCooldown(player);
            }
            return true;
        }

        // Vérifier si le mode trace est activé pour les commandes de modération
        if (!traceManager.isInTrace(player.getUniqueId())) {
            player.sendMessage("§cVous devez activer le mode trace avec /moderation avant d'utiliser les commandes de modération.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "kick":
                handleKick(player, args);
                break;
            case "mute":
                handleMute(player, args);
                break;
            case "unmute":
                handleUnmute(player, args);
                break;
            case "tp":
                handleTp(player, args);
                break;
            case "tphere":
                handleTpHere(player, args);
                break;
            case "vanish":
                handleVanish(player);
                break;
            case "lock":
            case "freeze":
                handleLock(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleLock(Player staff, String[] args) {
        if (args.length < 2) {
            staff.sendMessage("§8[§6Cantale§8] §cUsage : /moderation lock <joueur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            staff.sendMessage("§8[§6Cantale§8] §cJoueur introuvable ou hors ligne : §e" + args[1]);
            return;
        }
        var lockMgr = plugin.getPlayerLockManager();
        if (lockMgr != null) {
            lockMgr.toggleLock(staff, target);
        } else {
            staff.sendMessage("§8[§6Cantale§8] §cPlayerLockManager indisponible.");
        }
    }

    private void handleKick(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /moderation kick <joueur> [raison]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return;
        }

        String reason = args.length >= 3 ? String.join(" ", args).substring(args[0].length() + args[1].length() + 2) : "Expulsé par un modérateur";
        target.kickPlayer("§cVous avez été expulsé : §f" + reason);
        sender.sendMessage("§a" + target.getName() + " a été expulsé.");
    }

    private void handleMute(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /moderation mute <joueur> [durée] [raison]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return;
        }

        long duration = -1; // Permanent par défaut
        String reason = "Mute par un modérateur";

        if (args.length >= 3) {
            try {
                duration = parseDuration(args[2]);
                if (args.length >= 4) {
                    reason = String.join(" ", args).substring(args[0].length() + args[1].length() + args[2].length() + 3);
                }
            } catch (NumberFormatException e) {
                reason = String.join(" ", args).substring(args[0].length() + args[1].length() + 2);
            }
        }

        if (muteManager.mutePlayer(target.getUniqueId(), target.getName(), reason, duration)) {
            target.sendMessage("§cVous avez été mute : §f" + reason);
            sender.sendMessage("§a" + target.getName() + " a été mute.");
        } else {
            sender.sendMessage("§cErreur lors du mute.");
        }
    }

    private void handleUnmute(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /moderation unmute <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return;
        }

        if (muteManager.unmutePlayer(target.getUniqueId())) {
            target.sendMessage("§aVous avez été unmute.");
            sender.sendMessage("§a" + target.getName() + " a été unmute.");
        } else {
            sender.sendMessage("§cCe joueur n'est pas mute.");
        }
    }

    private void handleTp(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /moderation tp <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return;
        }

        sender.teleport(target.getLocation());
        sender.sendMessage("§aTéléporté à " + target.getName() + ".");
    }

    private void handleTpHere(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /moderation tphere <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return;
        }

        target.teleport(sender.getLocation());
        sender.sendMessage("§a" + target.getName() + " téléporté à vous.");
    }

    private void handleVanish(Player player) {
        vanishManager.toggleVanish(player);
        if (vanishManager.isVanished(player.getUniqueId())) {
            player.sendMessage("§aVanish activé.");
        } else {
            player.sendMessage("§cVanish désactivé.");
        }
    }

    private long parseDuration(String str) throws NumberFormatException {
        if (str.endsWith("s")) return Long.parseLong(str.substring(0, str.length() - 1));
        if (str.endsWith("m")) return Long.parseLong(str.substring(0, str.length() - 1)) * 60;
        if (str.endsWith("h")) return Long.parseLong(str.substring(0, str.length() - 1)) * 3600;
        if (str.endsWith("d")) return Long.parseLong(str.substring(0, str.length() - 1)) * 86400;
        return Long.parseLong(str);
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8[§6Cantale§8] §7=== Modération ===");
        player.sendMessage("§7/moderation kick <joueur> [raison]");
        player.sendMessage("§7/moderation mute <joueur> [durée] [raison]");
        player.sendMessage("§7/moderation unmute <joueur>");
        player.sendMessage("§7/moderation tp <joueur>");
        player.sendMessage("§7/moderation tphere <joueur>");
        player.sendMessage("§7/moderation vanish");
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public ModerationTraceManager getTraceManager() {
        return traceManager;
    }
}
