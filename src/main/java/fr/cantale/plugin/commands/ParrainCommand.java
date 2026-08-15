package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.lives.LifeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /parrain &lt;joueur&gt; — l'invité remercie son parrain une seule fois ;
 * le parrain gagne +1 vie (persisté en BDD).
 */
public class ParrainCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final PlayerDAO playerDAO;
    private final LifeManager lifeManager;

    public ParrainCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        this.playerDAO = new PlayerDAO(plugin);
        this.lifeManager = plugin.getLifeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player invitee)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length < 1) {
            invitee.sendMessage(format("&cUsage : /parrain <joueur>"));
            return true;
        }

        playerDAO.createOrUpdatePlayer(invitee.getUniqueId(), invitee.getName());

        if (playerDAO.hasUsedReferral(invitee.getUniqueId())) {
            invitee.sendMessage(format("&cTu as déjà remercié ton parrain. Cette commande ne s'utilise qu'une seule fois."));
            return true;
        }

        UUID sponsorUuid = resolveTargetUuid(args[0]);
        if (sponsorUuid == null || !playerDAO.playerExists(sponsorUuid)) {
            invitee.sendMessage(format("&cJoueur introuvable : &e" + args[0]));
            return true;
        }

        if (sponsorUuid.equals(invitee.getUniqueId())) {
            invitee.sendMessage(format("&cTu ne peux pas te parrainer toi-même !"));
            return true;
        }

        String sponsorName = resolveDisplayName(sponsorUuid, args[0]);

        // Marquage atomique avant le gain de vie (évite double-récompense en course)
        if (!playerDAO.tryMarkReferralUsed(invitee.getUniqueId(), sponsorUuid)) {
            invitee.sendMessage(format("&cTu as déjà remercié ton parrain. Cette commande ne s'utilise qu'une seule fois."));
            return true;
        }

        int livesBefore = lifeManager.getLives(sponsorUuid);
        lifeManager.addLife(sponsorUuid, 1);
        if (livesBefore <= 0) {
            lifeManager.pardonLifeBan(sponsorUuid, sponsorName);
        }

        invitee.sendMessage(format("&aTu as remercié &e" + sponsorName + " &apour t'avoir invité !"));
        invitee.sendMessage(format("&7Il/elle reçoit &e+1 vie&7. Tu ne pourras plus utiliser /parrain."));

        Player sponsorOnline = Bukkit.getPlayer(sponsorUuid);
        if (sponsorOnline != null && sponsorOnline.isOnline()) {
            sponsorOnline.sendMessage(format("&e" + invitee.getName()
                    + " &at'a remercié avec &e/parrain&a ! Tu gagnes &e+1 vie&a."));
            sponsorOnline.sendMessage(format("&7Vies actuelles : &c"
                    + lifeManager.getLives(sponsorUuid)));
        }

        plugin.getLogger().info("[Parrain] " + invitee.getName() + " (" + invitee.getUniqueId()
                + ") a parrainé " + sponsorName + " (" + sponsorUuid + ")");
        return true;
    }

    private UUID resolveTargetUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }

        UUID fromDb = playerDAO.getUuidByUsername(name);
        if (fromDb != null) {
            return fromDb;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()
                || playerDAO.playerExists(offline.getUniqueId())) {
            return offline.getUniqueId();
        }
        return null;
    }

    private String resolveDisplayName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return fallback;
    }

    private String format(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        boolean canSeeVanish = sender.hasPermission("cantale.moderator");
        var vanish = plugin.getModerationCommand() != null
                ? plugin.getModerationCommand().getVanishManager() : null;
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!canSeeVanish && vanish != null && vanish.isVanished(p.getUniqueId())) {
                continue;
            }
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(p.getName());
            }
        }
        return names.stream().sorted().collect(Collectors.toList());
    }
}
