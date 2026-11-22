package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.AdminBypassManager;
import fr.cantale.plugin.database.PlayerDAO;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.FactionReviveRules;
import fr.cantale.plugin.lives.LifeRules;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import fr.cantale.plugin.factions.permissions.FactionPermissionManager;
import fr.cantale.plugin.factions.permissions.FactionPermissions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class FactionCommand implements CommandExecutor, TabCompleter {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final FactionPermissionManager permissionManager;
    private final Map<UUID, Long> mapCooldowns;
    private static final long MAP_COOLDOWN_MS = 30000; // 30 secondes

    // Invitations en mémoire : invité → {factionId, expireAt}
    private final Map<UUID, int[]> pendingInvites = new HashMap<>();
    private static final long INVITE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    // Confirmations PASDIC : joueur -> {claim visé, activer?, timestamp}
    private final Map<UUID, PasdicConfirmation> pendingPasdic = new HashMap<>();
    private static final long PASDIC_CONFIRM_TTL_MS = 60 * 1000L; // 1 minute

    private record PasdicConfirmation(Claim claim, boolean enable, long timestamp) {}

    public FactionCommand(CantalePlugin plugin) {
        this.plugin = plugin;
        // Toujours utiliser le FactionManager du plugin (cache partagé)
        this.factionManager = plugin.getFactionManager();
        this.permissionManager = new FactionPermissionManager();
        this.mapCooldowns = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(formatMessage("&cCette commande est réservée aux joueurs."));
            return true;
        }

        if (args.length == 0) {
            plugin.getUIManager().openFactionMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny", "refuse" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "disband", "delete", "supprimer" -> handleDisband(player);
            case "rename", "name", "renommer" -> handleRename(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "join" -> handleJoin(player, args);
            case "kick" -> handleKick(player, args);
            case "ban" -> handleBan(player, args);
            case "claim" -> handleClaim(player, args);
            case "map" -> handleMap(player, args);
            case "autoclaim" -> handleAutoclaim(player, args);
            case "unclaim" -> handleUnclaim(player, args);
            case "spawn" -> handleSpawn(player);
            case "go" -> handleGo(player);
            case "setspawn" -> handleSetSpawn(player);
            case "bank" -> handleBank(player, args);
            case "revive", "reanimer" -> handleRevive(player, args);
            case "metier", "skill" -> handleSkill(player, args);
            case "vuln", "creneau" -> handleVulnerability(player, args);
            case "banniere", "siege" -> handleSiegeBanner(player);
            case "power", "pouvoir" -> handlePower(player);
            case "secret" -> handleSecret(player);
            case "pasdic", "protect" -> handlePasdic(player, args);
            case "raid" -> handleRaid(player);
            case "war", "enemy" -> handleWar(player, args);
            case "peace" -> handlePeace(player, args);
            case "surrender" -> handleSurrender(player, args);
            case "warend" -> handleWarEnd(player, args);
            case "ally" -> handleAlly(player, args);
            case "perms", "permissions" -> handlePerms(player, args);
            case "members", "membres" -> handleMembers(player);
            case "warp" -> handleFactionWarp(player, args);
            case "setwarp" -> handleSetWarp(player, args);
            case "delwarp" -> handleDelWarp(player, args);
            case "home", "sethome", "homes" -> {
                player.sendMessage(formatMessage("&cLes homes de faction n'existent plus. Utilise &e/home &c(perso), &e/f spawn &cou &e/fw&c."));
            }
            case "promote", "promo" -> handlePromote(player, args);
            case "demote", "demo" -> handleDemote(player, args);
            case "c", "chat" -> handleChat(player, args);
            case "bypass" -> handleBypass(player);
            case "help" -> sendHelp(player);
            default -> player.sendMessage(formatMessage("&cSous-commande inconnue. Utilisez /f help"));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "faction.create.usage");
            return;
        }

        String name = args[1];
        String nameError = factionManager.validateFactionName(name);
        if (nameError != null) {
            plugin.messages().sendEncoded(player, nameError);
            return;
        }
        if (factionManager.getPlayerFaction(player.getUniqueId()) != null) {
            plugin.messages().send(player, "faction.create.already");
            return;
        }

        if (factionManager.getFaction(name) != null) {
            plugin.messages().send(player, "faction.name.taken");
            return;
        }

        // Vérifications de zone AVANT création : impossible dans une zone protégée ou déjà claim
        org.bukkit.Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (!plugin.getSpawnProtection().canClaim(loc)) {
            plugin.messages().send(player, "faction.create.protected");
            return;
        }

        if (factionManager.getClaimAt(world, chunkX, chunkZ) != null) {
            plugin.messages().send(player, "faction.create.claimed");
            return;
        }

        int minMembers = plugin.getConfigManager().getMinMembersCreate();
        int founders = countEligibleFounders(player);
        if (!fr.cantale.plugin.factions.FactionCreateRules.canFound(minMembers, founders)) {
            int required = Math.max(1, minMembers);
            plugin.messages().send(player, "faction.create.need", required,
                    (int) fr.cantale.plugin.factions.FactionCreateRules.FOUNDING_RADIUS, founders);
            return;
        }

        // Générer un tag unique (4 caractères max)
        String tag = factionManager.generateUniqueTag(name, -1);

        Faction faction = factionManager.createFaction(name, tag, "", player.getUniqueId());
        if (faction == null) {
            plugin.messages().send(player, "faction.create.failed");
            return;
        }

        // Claim automatique du chunk courant
        boolean claimed = factionManager.addClaim(faction.getId(), world, chunkX, chunkZ);

        // Spawn de faction à la position actuelle (dans le claim fraîchement créé)
        faction.setSpawnX(loc.getX());
        faction.setSpawnY(loc.getY());
        faction.setSpawnZ(loc.getZ());
        faction.setSpawnWorld(world);
        factionManager.saveFaction(faction);
        // Marqueur /f spawn initial sur la carte mod client des membres
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().refreshMarkersForFaction(faction);
        }

        plugin.messages().send(player, "faction.create.ok", name);
        if (claimed) {
            plugin.messages().send(player, "faction.create.chunk");
        }
        plugin.messages().send(player, "faction.create.spawn");

        // Bridge Discord
        if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
            long ch = plugin.getDiscordBot().getConfig().getMcFactionsChannelId();
            if (ch != 0L) {
                plugin.getDiscordBot().sendEmbedToChannel(
                    ch,
                    java.awt.Color.CYAN,
                    "🏰 Nouvelle Faction",
                    "**" + player.getName() + "** a créé la faction **" + name + "** [" + tag + "]"
                );
            }
        }
    }

    /** Créateur inclus. Les joueurs déjà dans une faction ne comptent pas. */
    private int countEligibleFounders(Player creator) {
        org.bukkit.Location origin = creator.getLocation();
        if (origin.getWorld() == null) {
            return 1;
        }
        int count = 0;
        for (Player other : origin.getWorld().getPlayers()) {
            if (other == null || other.getWorld() == null) {
                continue;
            }
            if (factionManager.getPlayerFaction(other.getUniqueId()) != null) {
                continue;
            }
            org.bukkit.Location loc = other.getLocation();
            double dx = loc.getX() - origin.getX();
            double dy = loc.getY() - origin.getY();
            double dz = loc.getZ() - origin.getZ();
            if (fr.cantale.plugin.factions.FactionCreateRules.isWithinRadius(
                    dx, dy, dz, fr.cantale.plugin.factions.FactionCreateRules.FOUNDING_RADIUS)) {
                count++;
            }
        }
        return count;
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f invite <pseudo>"));
            return;
        }

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        // Vérifier permission FACCEPT (= droit d'inviter)
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.FACCEPT)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission d'inviter."));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(formatMessage("&cJoueur introuvable ou hors-ligne."));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(formatMessage("&cVous ne pouvez pas vous inviter vous-même."));
            return;
        }
        if (factionManager.getPlayerFaction(target.getUniqueId()) != null) {
            player.sendMessage(formatMessage("&c" + target.getName() + " est déjà dans une faction."));
            return;
        }

        // Stocker l'invitation
        long expire = System.currentTimeMillis() + INVITE_TTL_MS;
        pendingInvites.put(target.getUniqueId(), new int[]{faction.getId(), (int) (expire / 1000L)});

        player.sendMessage(formatMessage("&aInvitation envoyée à &e" + target.getName() + "&a."));
        target.sendMessage(formatMessage("&6" + player.getName() + " &avous invite dans la faction &e" + faction.getName() + "&a."));
        target.sendMessage(formatMessage("&7Tapez &e/f accept &7pour rejoindre (expire dans 5 min)."));
    }

    private void handleAccept(Player player, String[] args) {
        int[] invite = pendingInvites.get(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(formatMessage("&cAucune invitation en attente."));
            return;
        }
        long expireSec = invite[1];
        if (System.currentTimeMillis() / 1000L > expireSec) {
            pendingInvites.remove(player.getUniqueId());
            player.sendMessage(formatMessage("&cVotre invitation a expiré."));
            return;
        }
        if (factionManager.getPlayerFaction(player.getUniqueId()) != null) {
            player.sendMessage(formatMessage("&cVous êtes déjà dans une faction."));
            pendingInvites.remove(player.getUniqueId());
            return;
        }
        Faction faction = factionManager.getFaction(invite[0]);
        if (faction == null) {
            player.sendMessage(formatMessage("&cLa faction n'existe plus."));
            pendingInvites.remove(player.getUniqueId());
            return;
        }
        if (factionManager.addMember(faction.getId(), player.getUniqueId(), FactionRank.RECRUIT)) {
            pendingInvites.remove(player.getUniqueId());
            player.sendMessage(formatMessage("&aVous avez rejoint la faction &e" + faction.getName() + "&a !"));
            // Notifier les membres en ligne
            for (UUID memberUuid : faction.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline() && !member.equals(player)) {
                    member.sendMessage(formatMessage("&6[Faction] &e" + player.getName() + " &aa rejoint la faction."));
                }
            }
        } else {
            player.sendMessage(formatMessage("&cErreur lors de l'ajout à la faction."));
        }
    }

    private void handleDeny(Player player) {
        if (pendingInvites.remove(player.getUniqueId()) != null) {
            player.sendMessage(formatMessage("&7Invitation refusée."));
        } else {
            player.sendMessage(formatMessage("&cAucune invitation en attente."));
        }
    }

    private void handleLeave(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        if (faction.isLeader(player.getUniqueId())) {
            // Si le leader est le seul membre, il peut détruire la faction
            if (faction.getMemberCount() == 1) {
                if (factionManager.deleteFaction(faction.getId())) {
                    player.sendMessage(formatMessage("&aVotre faction &e" + faction.getName() + " &aa été détruite."));
                } else {
                    player.sendMessage(formatMessage("&cErreur lors de la destruction de la faction."));
                }
                return;
            }
            player.sendMessage(formatMessage("&cVous ne pouvez pas quitter votre propre faction. Transférez le lead avec /f give."));
            return;
        }

        factionManager.removeMember(faction.getId(), player.getUniqueId());
        player.sendMessage(formatMessage("&aVous avez quitté la faction &e" + faction.getName()));
    }

    private void handleDisband(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        if (!faction.isLeader(player.getUniqueId())) {
            player.sendMessage(formatMessage("&cSeul le chef de faction peut la supprimer."));
            return;
        }
        String name = faction.getName();
        if (factionManager.deleteFaction(faction.getId())) {
            player.sendMessage(formatMessage("&aLa faction &e" + name + " &aa été supprimée définitivement."));
            // Bridge Discord
            if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()) {
                long ch = plugin.getDiscordBot().getConfig().getMcFactionsChannelId();
                if (ch != 0L) {
                    plugin.getDiscordBot().sendEmbedToChannel(ch, java.awt.Color.RED,
                        "💥 Faction supprimée",
                        "**" + player.getName() + "** a dissous la faction **" + name + "**");
                }
            }
        } else {
            player.sendMessage(formatMessage("&cErreur lors de la suppression de la faction."));
        }
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f rename <nouveau_nom>"));
            return;
        }

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        if (!faction.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "faction.rename.leader");
            return;
        }

        String newName = args[1];
        String oldName = faction.getName();
        String error = factionManager.renameFaction(faction, newName);
        if (error != null) {
            plugin.messages().sendEncoded(player, error);
            return;
        }

        player.sendMessage(formatMessage(
                "&aFaction renommée : &e" + oldName + " &a→ &e" + faction.getName()
                        + " &7(tag &e[" + faction.getTag() + "]&7)"));
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline() && !member.getUniqueId().equals(player.getUniqueId())) {
                member.sendMessage(formatMessage(
                        "&6[Faction] &e" + player.getName() + " &aa renommé la faction en &e"
                                + faction.getName() + " &7[" + faction.getTag() + "]"));
            }
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            sendFactionInfo(player);
            return;
        }

        Faction faction = factionManager.getFaction(args[1]);
        if (faction == null) {
            player.sendMessage(formatMessage("&cFaction introuvable."));
            return;
        }

        displayFactionInfo(player, faction);
    }

    private void handleList(Player player) {
        List<Faction> factions = factionManager.getAllFactions();
        player.sendMessage(formatMessage("&6=== Liste des factions ==="));
        if (factions.isEmpty()) {
            player.sendMessage(formatMessage("&7Aucune faction n'existe pour le moment."));
        } else {
            for (Faction faction : factions) {
                player.sendMessage(formatMessage("&7- &e" + faction.getName() + " &7(" + faction.getMemberCount() + " membres)"));
            }
        }
    }

    private void handleJoin(Player player, String[] args) {
        player.sendMessage(formatMessage("&7Pour rejoindre une faction, vous devez être invité."));
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f kick <pseudo>"));
            return;
        }

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.FKICK)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission d'expulser."));
            return;
        }

        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || target.getName() == null) {
            player.sendMessage(formatMessage("&cJoueur introuvable."));
            return;
        }
        UUID targetUuid = target.getUniqueId();
        if (!faction.isMember(targetUuid)) {
            player.sendMessage(formatMessage("&c" + args[1] + " n'est pas dans votre faction."));
            return;
        }
        if (faction.isLeader(targetUuid)) {
            player.sendMessage(formatMessage("&cImpossible d'expulser le chef de faction."));
            return;
        }
        // Un officier ne peut pas kick un autre officier (sauf le chef)
        FactionRank targetRank = faction.getMemberRank(targetUuid);
        if (!faction.isLeader(player.getUniqueId()) && targetRank.isAtLeast(rank)) {
            player.sendMessage(formatMessage("&cVous ne pouvez pas expulser un membre de rang égal ou supérieur."));
            return;
        }

        factionManager.removeMember(faction.getId(), targetUuid);
        player.sendMessage(formatMessage("&a" + args[1] + " a été expulsé de la faction."));
        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(formatMessage("&cVous avez été expulsé de la faction " + faction.getName() + "."));
        }
    }

    private void handleBan(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f ban <pseudo>"));
            return;
        }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.BAN)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission de bannir."));
            return;
        }
        // Le ban revient à un kick + interdiction d'être invité à nouveau (TODO: persister la liste de bans)
        player.sendMessage(formatMessage("&7Ban : non encore persisté — application d'un kick équivalent."));
        handleKick(player, args);
    }

    private void handleMembers(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        player.sendMessage(formatMessage("&6=== Membres de " + faction.getName() + " ==="));
        for (UUID uuid : faction.getMembers()) {
            FactionRank r = faction.getMemberRank(uuid);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            Player p = Bukkit.getPlayer(uuid);
            String status = (p != null && p.isOnline()) ? "&aen ligne" : "&7hors-ligne";
            player.sendMessage(formatMessage("&7- &e" + name + " &8(&6" + r.getDisplayName() + "&8) " + status));
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(formatMessage("&cUsage : /f promote <pseudo>")); return; }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        if (!faction.isLeader(player.getUniqueId())) {
            player.sendMessage(formatMessage("&cSeul le chef peut promouvoir."));
            return;
        }
        UUID targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (!faction.isMember(targetUuid)) { player.sendMessage(formatMessage("&cMembre introuvable.")); return; }
        FactionRank current = faction.getMemberRank(targetUuid);
        FactionRank next = switch (current) {
            case RECRUIT -> FactionRank.MEMBER;
            case MEMBER -> FactionRank.VETERAN;
            case VETERAN -> FactionRank.OFFICER;
            default -> null;
        };
        if (next == null) { player.sendMessage(formatMessage("&cImpossible de promouvoir davantage.")); return; }
        factionManager.setMemberRank(faction.getId(), targetUuid, next);
        player.sendMessage(formatMessage("&a" + args[1] + " a été promu &6" + next.getDisplayName() + "&a."));
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(formatMessage("&cUsage : /f demote <pseudo>")); return; }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        if (!faction.isLeader(player.getUniqueId())) {
            player.sendMessage(formatMessage("&cSeul le chef peut rétrograder."));
            return;
        }
        UUID targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (!faction.isMember(targetUuid)) { player.sendMessage(formatMessage("&cMembre introuvable.")); return; }
        FactionRank current = faction.getMemberRank(targetUuid);
        FactionRank prev = switch (current) {
            case OFFICER -> FactionRank.VETERAN;
            case VETERAN -> FactionRank.MEMBER;
            case MEMBER -> FactionRank.RECRUIT;
            default -> null;
        };
        if (prev == null) { player.sendMessage(formatMessage("&cImpossible de rétrograder davantage.")); return; }
        factionManager.setMemberRank(faction.getId(), targetUuid, prev);
        player.sendMessage(formatMessage("&a" + args[1] + " a été rétrogradé à &6" + prev.getDisplayName() + "&a."));
    }

    private void handleFactionWarp(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getFactionWarpManager().listOwnFaction(player);
            return;
        }
        plugin.getFactionWarpManager().teleport(player, args[1]);
    }

    private void handleSetWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /fw create <nom> §7(alias /f setwarp)"));
            return;
        }
        plugin.getFactionWarpManager().create(player, args[1]);
    }

    private void handleDelWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /fw delete <nom> §7(alias /f delwarp)"));
            return;
        }
        plugin.getFactionWarpManager().delete(player, args[1]);
    }

    private void handleClaim(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.ADD_CLAIMS)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission de claim."));
            return;
        }

        if (!fr.cantale.plugin.world.WorldRules.claimsAllowed(player.getWorld().getName())) {
            plugin.messages().send(player, "faction.claim.forbidden",
                    fr.cantale.plugin.world.WorldTopology.FACTIONS);
            return;
        }

        // Vérifier si la location est protégée (spawn/warps)
        if (!plugin.getSpawnProtection().canClaim(player.getLocation())) {
            player.sendMessage(formatMessage("&cCette zone est protégée et ne peut pas être claimée."));
            return;
        }

        // Vérifier le quota max selon power
        int maxClaims = faction.getMaxClaims();
        if (faction.getClaimCount() >= maxClaims) {
            player.sendMessage(formatMessage("&cQuota de claims atteint (" + maxClaims + "). Augmentez le pouvoir de votre faction."));
            return;
        }

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        String world = player.getWorld().getName();

        // Distance minimale avec les claims d'autres factions
        if (factionManager.isTooCloseToOtherFaction(world, chunkX, chunkZ, faction.getId())) {
            player.sendMessage(formatMessage("&cTrop proche des claims d'une autre faction ("
                    + FactionManager.MIN_ENEMY_CLAIM_DISTANCE + " chunks minimum)."));
            return;
        }

        // Claims contigus : doit toucher un claim existant (sauf premier claim du monde)
        if (!factionManager.isAdjacentToOwnClaimOrFirst(world, chunkX, chunkZ, faction.getId())) {
            player.sendMessage(formatMessage(
                    "&cImpossible de claim ici : le chunk doit toucher un claim existant de votre faction "
                            + "(côtés N/S/E/O). Les claims séparés sont interdits."));
            return;
        }

        if (factionManager.addClaim(faction.getId(), world, chunkX, chunkZ)) {
            player.sendMessage(formatMessage("&aChunk claimé avec succès !"));
        } else {
            player.sendMessage(formatMessage("&cCe chunk est déjà claimé."));
        }
    }

    private void handleUnclaim(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.REMOVE_CLAIMS)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission d'unclaim."));
            return;
        }

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        String world = player.getWorld().getName();

        // Sécurité : on ne peut unclaim que les chunks de SA propre faction
        Claim claim = factionManager.getClaimAt(world, chunkX, chunkZ);
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(formatMessage("&cCe chunk n'est pas claimé par votre faction."));
            return;
        }

        if (factionManager.removeClaim(world, chunkX, chunkZ)) {
            player.sendMessage(formatMessage("&aChunk unclaimé avec succès !"));
        } else {
            player.sendMessage(formatMessage("&cErreur lors de l'unclaim."));
        }
    }

    private void handleSpawn(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        if (faction.getSpawnX() == null || faction.getSpawnWorld() == null) {
            player.sendMessage(formatMessage("&cVotre faction n'a pas de spawn défini. Utilisez /f setspawn dans un claim."));
            return;
        }

        teleportToFactionSpawn(player, faction);
    }

    private void handleGo(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        if (faction.getSpawnX() == null || faction.getSpawnWorld() == null) {
            player.sendMessage(formatMessage("&cVotre faction n'a pas de spawn défini. Utilisez /f setspawn dans un claim."));
            return;
        }

        teleportToFactionSpawn(player, faction);
    }

    private void teleportToFactionSpawn(Player player, Faction faction) {
        org.bukkit.World world = Bukkit.getWorld(faction.getSpawnWorld());
        if (world == null) {
            player.sendMessage(formatMessage("&cLe monde du spawn n'existe plus."));
            return;
        }

        org.bukkit.Location spawnLoc = new org.bukkit.Location(
            world,
            faction.getSpawnX(),
            faction.getSpawnY(),
            faction.getSpawnZ()
        );
        plugin.getTeleportDelayManager().requestTeleport(player, spawnLoc, false, true, () ->
                player.sendMessage(formatMessage("&aTéléporté au spawn de votre faction.")));
    }

    private void handleSetSpawn(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        // Vérifier si le joueur est le chef
        if (!faction.getLeaderUuid().equals(player.getUniqueId())) {
            player.sendMessage(formatMessage("&cSeul le chef de faction peut définir le spawn."));
            return;
        }

        // Vérifier si le joueur est dans un claim de sa faction
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        Claim claim = factionManager.getClaimAt(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(formatMessage("&cVous devez être dans un claim de votre faction pour définir le spawn."));
            return;
        }

        // Définir le spawn
        faction.setSpawnX(player.getLocation().getX());
        faction.setSpawnY(player.getLocation().getY());
        faction.setSpawnZ(player.getLocation().getZ());
        faction.setSpawnWorld(player.getWorld().getName());
        factionManager.saveFaction(faction);
        // Marqueur /f spawn à jour sur la carte des membres équipés du mod
        if (plugin.getClientMapBridge() != null) {
            plugin.getClientMapBridge().refreshMarkersForFaction(faction);
        }

        player.sendMessage(formatMessage("&aSpawn de faction défini à votre position."));
    }

    private void handleBank(Player player, String[] args) {
        new fr.cantale.plugin.commands.FactionBankCommand(plugin).onCommand(player, null, "f bank", java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    private void handlePower(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        plugin.getUIManager().getNavigationManager()
                .openUI(player, new fr.cantale.plugin.ui.inventory.FactionPowerShopUI(plugin, player, faction));
    }

    /**
     * Officier ou chef : lève le deathban d'un membre contre {@link FactionReviveRules#POWER_COST} pouvoir.
     * Le débit SQL est arithmétique ; la vie n'est accordée que si le joueur est encore à 0.
     */
    private void handleRevive(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "faction.revive.usage");
            return;
        }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            plugin.messages().send(player, "faction.revive.none");
            return;
        }
        FactionRank rank = faction.isLeader(player.getUniqueId())
                ? FactionRank.LEADER
                : faction.getMemberRank(player.getUniqueId());

        PlayerDAO dao = new PlayerDAO(plugin);
        UUID targetId = dao.getUuidByUsername(args[1]);
        if (targetId == null) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                targetId = online.getUniqueId();
            }
        }
        if (targetId == null) {
            plugin.messages().send(player, "faction.revive.unknown");
            return;
        }
        String targetName = dao.getUsername(targetId);
        if (targetName == null || targetName.isBlank()) {
            targetName = args[1];
        }

        int lives = plugin.getLifeManager().getLives(targetId);
        long now = System.currentTimeMillis() / 1000L;
        boolean deathban = LifeRules.deathbanActive(dao.getLastDeath(targetId), now);
        FactionReviveRules.Deny deny = FactionReviveRules.evaluate(
                rank,
                faction.isMember(targetId),
                lives,
                deathban,
                faction.getPower());
        if (deny != null) {
            plugin.messages().sendEncoded(player, reviveDenyKey(deny));
            return;
        }
        if (!factionManager.debitPowerAtomic(faction.getId(), FactionReviveRules.POWER_COST)) {
            plugin.messages().send(player, "faction.revive.debit", FactionReviveRules.POWER_COST);
            return;
        }
        if (!plugin.getLifeManager().grantLifeIfDepleted(targetId)) {
            factionManager.creditPowerAtomic(faction.getId(), FactionReviveRules.POWER_COST);
            plugin.messages().send(player, "faction.revive.raced", targetName);
            return;
        }
        plugin.getLifeManager().pardonLifeBan(targetId, targetName);
        plugin.messages().send(player, "faction.revive.ok", targetName,
                FactionReviveRules.LIVES_GRANTED, FactionReviveRules.POWER_COST);
        Player targetOnline = Bukkit.getPlayer(targetId);
        if (targetOnline != null && targetOnline.isOnline()) {
            plugin.messages().send(targetOnline, "faction.revive.target", player.getName(),
                    FactionReviveRules.LIVES_GRANTED);
        }
    }

    private static String reviveDenyKey(FactionReviveRules.Deny deny) {
        return switch (deny) {
            case NOT_OFFICER -> "faction.revive.not-officer";
            case NOT_SAME_FACTION -> "faction.revive.not-member";
            case NOT_DEATHBANNED -> "faction.revive.not-banned";
            case ALREADY_HAS_LIVES -> "faction.revive.has-lives";
            case INSUFFICIENT_POWER -> "faction.revive.power\u001f" + FactionReviveRules.POWER_COST;
        };
    }

    private void handleSkill(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            plugin.messages().send(player, "faction.revive.none");
            return;
        }
        fr.cantale.plugin.skills.SkillService skills = plugin.getSkillService();
        if (skills == null) {
            plugin.messages().send(player, "faction.skill.down");
            return;
        }
        if (args.length < 2) {
            fr.cantale.plugin.skills.Skill current = skills.skillOf(player.getUniqueId());
            plugin.messages().send(player, "faction.skill.current",
                    current == null ? plugin.messages().raw(player, "faction.skill.unset") : current.label());
            plugin.messages().send(player, "faction.skill.counts",
                    skills.count(faction, fr.cantale.plugin.skills.Skill.ARCHITECTE),
                    skills.count(faction, fr.cantale.plugin.skills.Skill.NEGOCIANT),
                    skills.count(faction, fr.cantale.plugin.skills.Skill.ARTISAN),
                    skills.count(faction, fr.cantale.plugin.skills.Skill.CHEVALIER));
            plugin.messages().send(player, "faction.skill.choices");
            plugin.messages().send(player, "faction.skill.cost",
                    (int) fr.cantale.plugin.skills.SkillFormulas.CHANGE_COST,
                    fr.cantale.plugin.skills.SkillFormulas.CHANGE_COOLDOWN_SECONDS / 3600);
            return;
        }
        fr.cantale.plugin.skills.Skill chosen = fr.cantale.plugin.skills.Skill.fromArgument(args[1]);
        if (chosen == null) {
            plugin.messages().send(player, "faction.skill.unknown");
            return;
        }
        boolean inCombat = plugin.getCombatTagManager() != null
                && plugin.getCombatTagManager().isInCombat(player);
        fr.cantale.plugin.skills.SkillChangeRules.Deny deny = skills.change(player.getUniqueId(), chosen, inCombat);
        if (deny == null) {
            plugin.messages().send(player, "faction.skill.ok", chosen.label());
            return;
        }
        String key = switch (deny) {
            case COMBAT -> "faction.skill.combat";
            case SAME -> "faction.skill.same";
            case COOLDOWN -> "faction.skill.cooldown\u001f"
                    + (fr.cantale.plugin.skills.SkillFormulas.CHANGE_COOLDOWN_SECONDS / 3600);
            case FUNDS -> "faction.skill.funds\u001f" + (int) fr.cantale.plugin.skills.SkillFormulas.CHANGE_COST;
            case NONE -> "faction.skill.none";
        };
        plugin.messages().sendEncoded(player, key);
    }

    private void handleVulnerability(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            plugin.messages().send(player, "faction.revive.none");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(player, "faction.vuln.window",
                    fr.cantale.plugin.siege.VulnerabilityWindow.format(faction.getVulnStartMinute())
                            + "–"
                            + fr.cantale.plugin.siege.VulnerabilityWindow.format(faction.getVulnEndMinute()));
            plugin.messages().send(player, "faction.vuln.hint");
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (rank == null || rank.isLessThan(FactionRank.OFFICER)) {
            plugin.messages().send(player, "faction.vuln.officer");
            return;
        }
        int start;
        int end;
        try {
            start = fr.cantale.plugin.siege.VulnerabilityWindow.parseMinute(args[1]);
            end = fr.cantale.plugin.siege.VulnerabilityWindow.parseMinute(args[2]);
        } catch (RuntimeException ex) {
            plugin.messages().send(player, "faction.vuln.bad");
            return;
        }
        if (start == end) {
            plugin.messages().send(player, "faction.vuln.empty");
            return;
        }
        faction.setVulnStartMinute(start);
        faction.setVulnEndMinute(end);
        plugin.getFactionDAO().updateVulnerability(faction.getId(), faction.getVulnStartMinute(), faction.getVulnEndMinute());
        plugin.messages().send(player, "faction.vuln.saved",
                fr.cantale.plugin.siege.VulnerabilityWindow.format(faction.getVulnStartMinute())
                        + "–"
                        + fr.cantale.plugin.siege.VulnerabilityWindow.format(faction.getVulnEndMinute()));
    }

    private void handleSiegeBanner(Player player) {
        fr.cantale.plugin.siege.SiegeManager siege = plugin.getSiegeManager();
        if (siege == null) {
            plugin.messages().send(player, "faction.siege.down");
            return;
        }
        String error = siege.giveBanner(player);
        if (error != null) {
            plugin.messages().sendEncoded(player, error);
            return;
        }
        plugin.messages().send(player, "faction.siege.given");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Commandes Factions ===");
        player.sendMessage(ChatColor.GRAY + "/f create <nom> - Créer une faction");
        player.sendMessage(ChatColor.GRAY + "/f rename <nouveau_nom> - Renommer (chef) — alias /f name");
        player.sendMessage(ChatColor.GRAY + "/f invite <pseudo> - Inviter un joueur");
        player.sendMessage(ChatColor.GRAY + "/f accept | /f deny - Répondre à une invitation");
        player.sendMessage(ChatColor.GRAY + "/f leave - Quitter la faction");
        player.sendMessage(ChatColor.GRAY + "/f kick <pseudo> - Expulser un membre");
        player.sendMessage(ChatColor.GRAY + "/f promote|demote <pseudo> - Changer le rang");
        player.sendMessage(ChatColor.GRAY + "/f info [nom] - Infos faction");
        player.sendMessage(ChatColor.GRAY + "/f list - Liste des factions");
        player.sendMessage(ChatColor.GRAY + "/f members - Liste des membres");
        player.sendMessage(ChatColor.GRAY + "/f claim - Claim le chunk actuel");
        player.sendMessage(ChatColor.GRAY + "/f unclaim - Unclaim le chunk actuel");
        player.sendMessage(ChatColor.GRAY + "/f autoclaim on|off");
        player.sendMessage(ChatColor.GRAY + "/f map - Carte GUI (claim/unclaim au clic)");
        player.sendMessage(ChatColor.GRAY + "/f map chat - Ancienne grille chat 11×11 (cooldown 30 s)");
        player.sendMessage(ChatColor.GRAY + "/f spawn | /f go | /f setspawn - Spawn de faction (interne)");
        player.sendMessage(ChatColor.GRAY + "/fw | /fw <nom> | /fw create <nom> | /fw delete <nom> - Warps publics");
        player.sendMessage(ChatColor.GRAY + "/f warp [nom] | /f setwarp <nom> | /f delwarp <nom> - alias des FW");
        player.sendMessage(ChatColor.GRAY + "/f c [message] - Basculer ou envoyer un message dans le chat de faction");
        player.sendMessage(ChatColor.GRAY + "/f bank - Banque GUI | /f bank power [n] (sans n = GUI pouvoir)");
        plugin.messages().send(player, "faction.help.revive", FactionReviveRules.POWER_COST);
        plugin.messages().send(player, "faction.help.skill");
        plugin.messages().send(player, "faction.help.vuln");
        plugin.messages().send(player, "faction.help.banner");
        player.sendMessage(ChatColor.GRAY + "/f power - Pouvoir, quotas, paliers + achat (GUI)");
        player.sendMessage(ChatColor.GRAY + "/f perms [grade] - Voir les permissions par grade");
        player.sendMessage(ChatColor.GRAY + "/f secret - Activer le mode secret (1h)");
        player.sendMessage(ChatColor.GRAY + "/f war <faction> - Déclarer la guerre (mutuel : démarre si l'ennemi déclare aussi)");
        player.sendMessage(ChatColor.GRAY + "/f peace <faction> - Proposer la paix (mutuel : fin si l'ennemi propose aussi)");
        player.sendMessage(ChatColor.GRAY + "/f surrender <faction> - Capituler (fin immédiate + réparations Cantox)");
        player.sendMessage(ChatColor.GRAY + "/f raid - Déclarer un raid sur le claim ennemi actuel (sans guerre requise)");
        if (player.hasPermission(AdminBypassManager.PERMISSION)) {
            player.sendMessage(ChatColor.GRAY + "/f warend <facA> <facB> - Terminer une guerre de force (staff)");
            player.sendMessage(ChatColor.GRAY + "/f bypass - Bypass admin des protections de claims (session uniquement)");
        }
    }

    public void handleChat(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }

        if (args.length > 1) {
            String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            factionManager.sendFactionMessage(player, faction, msg);
        } else {
            boolean enabled = factionManager.toggleFactionChat(player.getUniqueId());
            if (enabled) {
                player.sendMessage(formatMessage("&aChat de Faction &eACTIVÉ&a. Tous vos messages iront à votre faction. Tapez &e/f c &apour désactiver."));
            } else {
                player.sendMessage(formatMessage("&cChat de Faction &eDÉSACTIVÉ&c. Vos messages repassent en chat global."));
            }
        }
    }

    /**
     * /f bypass — toggle du bypass admin des protections de territoire.
     * Session uniquement (coupé à la déconnexion), chaque bascule est loguée en console.
     */
    private void handleBypass(Player player) {
        if (!player.hasPermission(AdminBypassManager.PERMISSION)) {
            player.sendMessage(formatMessage("&cPermission refusée. (" + AdminBypassManager.PERMISSION + ")"));
            return;
        }
        boolean active = plugin.getAdminBypassManager().toggle(player);
        if (active) {
            player.sendMessage("§8[§6Cantale§8] §eMode bypass admin §aactivé§e — toutes les protections de claims sont ignorées.");
            player.sendMessage("§8[§6Cantale§8] §7Session uniquement : le bypass se coupe à la déconnexion.");
        } else {
            player.sendMessage("§8[§6Cantale§8] §eMode bypass admin §cdésactivé§e — les protections de claims s'appliquent à nouveau.");
        }
    }

    private void sendFactionInfo(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction."));
            return;
        }
        displayFactionInfo(player, faction);
    }

    private void displayFactionInfo(Player player, Faction faction) {
        player.sendMessage(ChatColor.GOLD + "=== " + faction.getName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Tag: " + ChatColor.YELLOW + faction.getTag());
        player.sendMessage(ChatColor.GRAY + "Chef: " + ChatColor.YELLOW + Bukkit.getOfflinePlayer(faction.getLeaderUuid()).getName());
        player.sendMessage(ChatColor.GRAY + "Membres: " + ChatColor.YELLOW + faction.getMemberCount());
        player.sendMessage(ChatColor.GRAY + "Claims: " + ChatColor.YELLOW + faction.getClaimCount());
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + faction.getType());
        
        player.sendMessage(ChatColor.GRAY + "Pouvoir: " + ChatColor.YELLOW + faction.getPower());
        player.sendMessage(ChatColor.GRAY + "Claims max: " + ChatColor.YELLOW + faction.getMaxClaims());
    }

    private void handleMap(Player player, String[] args) {
        // /f map chat = ancienne grille ASCII ; /f map = GUI inventaire (claim/unclaim)
        if (args.length >= 2 && args[1].equalsIgnoreCase("chat")) {
            sendChatMap(player);
            return;
        }
        plugin.getUIManager().getNavigationManager()
                .openUI(player, new fr.cantale.plugin.ui.inventory.FactionMapUI(plugin, player));
    }

    /** Ancienne carte chat 11×11 (conservée via /f map chat). */
    private void sendChatMap(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (mapCooldowns.containsKey(uuid)) {
            long lastUse = mapCooldowns.get(uuid);
            long remaining = MAP_COOLDOWN_MS - (now - lastUse);
            if (remaining > 0) {
                player.sendMessage(formatMessage("&cCooldown ! Attendez encore " + ((remaining + 999) / 1000) + " s."));
                return;
            }
        }

        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();
        Faction playerFaction = factionManager.getPlayerFaction(uuid);
        var diplomacy = plugin.getDiplomatieManager();

        // Carte 11×11 chunks autour du joueur (N en haut, chaque case = 1 chunk)
        int radius = 5;

        boolean sawAlly = false;
        boolean sawEnemy = false;
        boolean sawPasdic = false;
        boolean sawOther = false;
        boolean sawOwn = false;
        LinkedHashMap<Integer, Faction> nearbyOthers = new LinkedHashMap<>();
        String standingOn = "§8wilderness";

        // En-tête compact (1 ligne) — pas de préfixe répété sur la grille
        player.sendMessage("§8[§6Cantale§8] §6Carte §7· chunk §e" + playerChunkX + "§7, §e" + playerChunkZ
                + " §8(" + world + ")");

        // Nord centré (préfixe «W » = 2 colonnes, chaque case = 2 colonnes «X »)
        player.sendMessage("  " + "  ".repeat(radius) + "§fN");

        for (int dz = -radius; dz <= radius; dz++) {
            int z = playerChunkZ + dz;
            StringBuilder line = new StringBuilder();
            line.append(dz == 0 ? "§fW " : "  ");

            for (int dx = -radius; dx <= radius; dx++) {
                int x = playerChunkX + dx;
                boolean here = dx == 0 && dz == 0;

                Claim claim = factionManager.getClaimAt(world, x, z);
                Faction claimFaction = null;
                if (claim != null) {
                    claimFaction = factionManager.getFaction(claim.getFactionId());
                    // Mode secret : claims invisibles aux non-membres (comme JourneyMap / site)
                    if (claimFaction != null && claimFaction.isSecret()
                            && (playerFaction == null || playerFaction.getId() != claimFaction.getId())) {
                        claim = null;
                        claimFaction = null;
                    }
                }

                String cell;
                if (claim == null || claimFaction == null) {
                    cell = here ? "§b+" : "§8-";
                    if (here) standingOn = "§8wilderness";
                } else if (claim.isPasdic()) {
                    sawPasdic = true;
                    cell = here ? "§d+" : "§dP";
                    if (here) {
                        standingOn = "§dPASDIC §7· §e" + claimFaction.getName()
                                + " §8[" + claimFaction.getTag() + "]";
                    }
                    if (playerFaction == null || claimFaction.getId() != playerFaction.getId()) {
                        nearbyOthers.putIfAbsent(claimFaction.getId(), claimFaction);
                    }
                } else if (playerFaction != null && claimFaction.getId() == playerFaction.getId()) {
                    sawOwn = true;
                    cell = here ? "§a+" : "§a#";
                    if (here) standingOn = "§aton claim §8[" + claimFaction.getTag() + "]";
                } else if (playerFaction != null && diplomacy != null
                        && diplomacy.isAllied(playerFaction.getId(), claimFaction.getId())) {
                    sawAlly = true;
                    nearbyOthers.putIfAbsent(claimFaction.getId(), claimFaction);
                    cell = here ? "§b+" : "§b@";
                    if (here) {
                        standingOn = "§ballié §e" + claimFaction.getName()
                                + " §8[" + claimFaction.getTag() + "]";
                    }
                } else if (playerFaction != null && diplomacy != null
                        && diplomacy.isAtWar(playerFaction.getId(), claimFaction.getId())) {
                    sawEnemy = true;
                    nearbyOthers.putIfAbsent(claimFaction.getId(), claimFaction);
                    cell = here ? "§c+" : "§cX";
                    if (here) {
                        standingOn = "§cennemi §e" + claimFaction.getName()
                                + " §8[" + claimFaction.getTag() + "]";
                    }
                } else {
                    sawOther = true;
                    nearbyOthers.putIfAbsent(claimFaction.getId(), claimFaction);
                    cell = here ? "§e+" : "§e=";
                    if (here) {
                        standingOn = "§e" + claimFaction.getName()
                                + " §8[" + claimFaction.getTag() + "]";
                    }
                }

                line.append(cell).append(' ');
            }

            if (dz == 0) {
                line.append("§fE");
            }
            player.sendMessage(line.toString());
        }

        player.sendMessage("  " + "  ".repeat(radius) + "§fS");

        // Légende dynamique : uniquement les symboles présents (moins de spam)
        StringBuilder legend = new StringBuilder("§7");
        legend.append("§b+§7 toi");
        if (sawOwn) legend.append("  §a#§7 ta fac");
        if (sawAlly) legend.append("  §b@§7 allié");
        if (sawEnemy) legend.append("  §cX§7 ennemi");
        if (sawOther) legend.append("  §e=§7 autre");
        if (sawPasdic) legend.append("  §dP§7 PASDIC");
        legend.append("  §8-§7 wild");
        player.sendMessage(legend.toString());

        player.sendMessage("§7Ici: " + standingOn);

        if (!nearbyOthers.isEmpty()) {
            StringBuilder around = new StringBuilder("§7Autour:");
            int shown = 0;
            for (Faction f : nearbyOthers.values()) {
                if (shown >= 6) {
                    around.append(" §8…");
                    break;
                }
                around.append(" §e").append(f.getTag())
                        .append("§8(").append(f.getName()).append(")");
                shown++;
            }
            player.sendMessage(around.toString());
        }

        mapCooldowns.put(uuid, now);
    }

    private void handleAutoclaim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f autoclaim <on|off>"));
            return;
        }

        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction pour utiliser l'auto-claim."));
            return;
        }

        // Vérifier la permission addclaims
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.ADD_CLAIMS)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission de claim des chunks."));
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on");
        plugin.getFactionManager().setAutoClaim(player.getUniqueId(), enable);
        player.sendMessage(formatMessage("&aAuto-claim " + (enable ? "activé" : "désactivé") + " pour votre faction !"));
        if (enable) {
            player.sendMessage(formatMessage("&7Marchez dans la wilderness pour claim automatiquement."));
        }
    }

    private void handleSecret(Player player) {
        player.sendMessage(formatMessage("&cLa commande /f secret n'existe plus. La protection de carte est désormais automatique (3 premiers jours de la faction et 24h après un raid)."));
    }

    private boolean chunkHasStorage(org.bukkit.Chunk chunk) {
        if (chunk == null) return false;
        for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
            if (state instanceof org.bukkit.block.Container) {
                return true;
            }
        }
        return false;
    }

    private void handlePasdic(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.PASDIC)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }

        // Confirmation
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            PasdicConfirmation conf = pendingPasdic.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (conf == null || now - conf.timestamp > PASDIC_CONFIRM_TTL_MS) {
                player.sendMessage(formatMessage("&cAucune confirmation PASDIC en attente."));
                return;
            }
            org.bukkit.Location loc = player.getLocation();
            Claim current = factionManager.getClaimAt(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
            if (current == null || current.getFactionId() != conf.claim.getFactionId()
                    || current.getChunkX() != conf.claim.getChunkX()
                    || current.getChunkZ() != conf.claim.getChunkZ()
                    || !current.getWorld().equals(conf.claim.getWorld())) {
                player.sendMessage(formatMessage("&cTu n'es plus dans le chunk visé."));
                return;
            }
            if (conf.enable) {
                if (chunkHasStorage(loc.getChunk())
                        && !player.isOp() && !player.hasPermission("cantale.admin")
                        && !plugin.getAdminBypassManager().isBypassing(player)) {
                    player.sendMessage(formatMessage("&cImpossible de protéger ce claim : il contient des éléments de stockage (coffres, barrels, etc.)."));
                    pendingPasdic.remove(player.getUniqueId());
                    return;
                }
                double cost = plugin.getConfigManager().getPasdicCost();
                if (faction.getBalance() < cost) {
                    player.sendMessage(formatMessage("&cLa banque de faction n'a pas assez (" + String.format("%.0f", cost) + " Cantox requis)."));
                    return;
                }
                faction.setBalance(faction.getBalance() - cost);
                plugin.getPasdicService().setPasdic(current, true, loc.getChunk());
                factionManager.saveFaction(faction);
                plugin.getBankManager().logTransaction(faction.getId(), player.getUniqueId(),
                        fr.cantale.plugin.economy.BankManager.ACTION_PASDIC, cost, faction.getBalance(),
                        "Conversion claim en PASDIC");
                player.sendMessage(formatMessage("&aCe claim est désormais un PASDIC."));
            } else {
                plugin.getPasdicService().setPasdic(current, false, loc.getChunk());
                player.sendMessage(formatMessage("&cLe PASDIC a été retiré de ce claim."));
            }
            pendingPasdic.remove(player.getUniqueId());
            return;
        }

        org.bukkit.Location loc = player.getLocation();
        Claim claim = factionManager.getClaimAt(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(formatMessage("&cTu dois être dans un claim de ta faction."));
            return;
        }
        if (claim.isPasdic()) {
            pendingPasdic.put(player.getUniqueId(), new PasdicConfirmation(claim, false, System.currentTimeMillis()));
            player.sendMessage(formatMessage("&eTape &a/f pasdic confirm &epour retirer le PASDIC de ce chunk (gratuit)."));
        } else {
            if (chunkHasStorage(loc.getChunk())
                    && !player.isOp() && !player.hasPermission("cantale.admin")
                    && !plugin.getAdminBypassManager().isBypassing(player)) {
                player.sendMessage(formatMessage("&cImpossible de protéger ce claim : il contient des éléments de stockage (coffres, barrels, etc.)."));
                return;
            }
            double cost = plugin.getConfigManager().getPasdicCost();
            pendingPasdic.put(player.getUniqueId(), new PasdicConfirmation(claim, true, System.currentTimeMillis()));
            player.sendMessage(formatMessage("&eTape &a/f pasdic confirm &epour convertir ce claim en PASDIC (&6"
                    + String.format("%.0f", cost) + " Cantox&e)."));
        }
    }

    private void handleRaid(Player player) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.RAID)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }
        org.bukkit.Location loc = player.getLocation();
        Claim claim = factionManager.getClaimAt(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
        if (claim == null || claim.getFactionId() == faction.getId()) {
            player.sendMessage(formatMessage("&cTu dois être dans un claim ennemi pour déclarer un raid."));
            return;
        }
        plugin.getRaidManager().startRaid(player, claim);
    }

    /**
     * /f war <faction> — déclaration de guerre MUTUELLE : la première déclaration est
     * « en attente » (expiration configurable) ; la guerre ne démarre que si l'autre
     * faction déclare à son tour.
     */
    private void handleWar(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.WAR)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f war <faction>"));
            return;
        }
        Faction target = resolveFactionByName(args[1]);
        if (target == null) {
            player.sendMessage(formatMessage("&cFaction introuvable."));
            return;
        }
        if (target.getId() == faction.getId()) {
            player.sendMessage(formatMessage("&cTu ne peux pas te déclarer la guerre."));
            return;
        }
        var result = plugin.getDiplomatieManager().declareWar(faction.getId(), target.getId());
        switch (result) {
            case PENDING -> player.sendMessage(formatMessage(
                    "&eDéclaration de guerre enregistrée. En attente de la déclaration de &c"
                            + target.getName() + "&e."));
            case STARTED -> player.sendMessage(formatMessage(
                    "&cLa guerre contre &e" + target.getName() + " &cest déclarée !"));
            case ALREADY_AT_WAR -> player.sendMessage(formatMessage(
                    "&cVous êtes déjà en guerre contre " + target.getName() + "."));
            case ALREADY_DECLARED -> player.sendMessage(formatMessage(
                    "&cVotre déclaration de guerre contre " + target.getName()
                            + " est déjà en attente (réponse adverse requise)."));
            case INVALID -> player.sendMessage(formatMessage("&cDéclaration impossible."));
        }
    }

    /**
     * /f peace <faction> — paix MUTUELLE : la première proposition est « en attente »
     * (même expiration que les déclarations) ; la guerre ne prend fin que si l'autre
     * faction propose aussi la paix.
     */
    private void handlePeace(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.WAR)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f peace <faction>"));
            return;
        }
        Faction target = resolveFactionByName(args[1]);
        if (target == null) {
            player.sendMessage(formatMessage("&cFaction introuvable."));
            return;
        }
        var result = plugin.getDiplomatieManager().proposePeace(faction.getId(), target.getId());
        switch (result) {
            case PENDING -> player.sendMessage(formatMessage(
                    "&eProposition de paix envoyée à &c" + target.getName()
                            + "&e. En attente de sa réponse."));
            case PEACE -> player.sendMessage(formatMessage(
                    "&aLa paix est conclue avec &e" + target.getName() + "&a."));
            case NOT_AT_WAR -> player.sendMessage(formatMessage(
                    "&cVous n'êtes pas en guerre contre " + target.getName() + "."));
            case ALREADY_PROPOSED -> player.sendMessage(formatMessage(
                    "&cVotre proposition de paix est déjà en attente (réponse adverse requise)."));
            case INVALID -> player.sendMessage(formatMessage("&cProposition impossible."));
        }
    }

    /**
     * /f surrender <faction> — capitulation unilatérale : fin immédiate de la guerre,
     * annonce publique de défaite et réparations Cantox banque → banque (configurable).
     */
    private void handleSurrender(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.WAR)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f surrender <faction>"));
            return;
        }
        Faction target = resolveFactionByName(args[1]);
        if (target == null) {
            player.sendMessage(formatMessage("&cFaction introuvable."));
            return;
        }
        String error = plugin.getDiplomatieManager().surrender(faction.getId(), target.getId());
        if (error != null) {
            player.sendMessage(formatMessage("&c" + error));
        } else {
            player.sendMessage(formatMessage("&eVotre faction a capitulé face à &c" + target.getName() + "&e."));
        }
    }

    /**
     * /f warend <factionA> <factionB> — fin de guerre forcée (staff).
     * Permission cantale.faction.bypass (même perm staff que /f bypass) ; log console d'audit.
     */
    private void handleWarEnd(Player player, String[] args) {
        if (!player.hasPermission(AdminBypassManager.PERMISSION)) {
            player.sendMessage(formatMessage("&cPermission refusée. (" + AdminBypassManager.PERMISSION + ")"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(formatMessage("&cUsage : /f warend <factionA> <factionB>"));
            return;
        }
        Faction factionA = resolveFactionByName(args[1]);
        Faction factionB = resolveFactionByName(args[2]);
        if (factionA == null || factionB == null) {
            player.sendMessage(formatMessage("&cFaction introuvable (" + args[1] + " ou " + args[2] + ")."));
            return;
        }
        boolean ended = plugin.getDiplomatieManager()
                .forceEndWar(factionA.getId(), factionB.getId(), player.getName());
        if (ended) {
            player.sendMessage(formatMessage("&aGuerre terminée entre &e" + factionA.getName()
                    + " &aet &e" + factionB.getName() + "&a."));
        } else {
            player.sendMessage(formatMessage("&c" + factionA.getName() + " et " + factionB.getName()
                    + " ne sont pas en guerre."));
        }
    }

    private void handleAlly(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.WAR)) {
            player.sendMessage(formatMessage("&cRang insuffisant (Officier requis)."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f ally <faction>"));
            return;
        }
        Faction target = resolveFactionByName(args[1]);
        if (target == null) {
            player.sendMessage(formatMessage("&cFaction introuvable."));
            return;
        }
        if (target.getId() == faction.getId()) {
            player.sendMessage(formatMessage("&cTu ne peux pas être allié avec toi-même."));
            return;
        }
        if (plugin.getDiplomatieManager().isAllied(faction.getId(), target.getId())) {
            player.sendMessage(formatMessage("&cVous êtes déjà alliés avec " + target.getName() + "."));
            return;
        }
        plugin.getDiplomatieManager().proposeAlliance(faction.getId(), target.getId());
        Bukkit.broadcastMessage(formatMessage("&a[Alliance] &e" + faction.getName() + " &7s'allie avec &e" + target.getName() + "&7."));
    }

    private Faction resolveFactionByName(String input) {
        if (input == null || input.isBlank()) return null;
        String name = input.trim();
        Faction byName = factionManager.getFaction(name);
        if (byName != null) return byName;
        for (Faction f : factionManager.getAllFactions()) {
            if (f.getName().equalsIgnoreCase(name) || f.getTag().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    private void handlePerms(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction."));
            return;
        }

        FactionRank[] ranks = FactionRank.values();
        if (args.length >= 2) {
            FactionRank filtered = FactionRank.fromString(args[1]);
            if (filtered != null) {
                ranks = new FactionRank[]{filtered};
            }
        }

        player.sendMessage(formatMessage("&7=== Permissions par grade (fixes) ==="));
        for (FactionRank rank : ranks) {
            player.sendMessage(formatMessage("&6" + rank.getDisplayName() + " &7:"));
            for (FactionPermission perm : FactionPermissions.getGranted(rank)) {
                player.sendMessage(formatMessage("  &a✓ &7" + formatPermissionLabel(perm)));
            }
            if (rank == FactionRank.RECRUIT) {
                player.sendMessage(formatMessage("  &8(grade minimal — peu de droits)"));
            }
        }
        player.sendMessage(formatMessage("&7Le &6Leader &7(créateur) a toutes les permissions."));
        player.sendMessage(formatMessage("&7Promouvez vos membres avec &e/f promote &7pour leur donner plus de droits."));
    }

    private String formatPermissionLabel(FactionPermission perm) {
        return switch (perm) {
            case ADD_CLAIMS -> "Claimer des chunks";
            case REMOVE_CLAIMS -> "Unclaim des chunks";
            case FACCEPT -> "Inviter des joueurs";
            case FKICK -> "Expulser des membres";
            case BAN -> "Bannir des joueurs";
            case UNBAN -> "Débannir des joueurs";
            case BANK_ADD -> "Déposer à la banque";
            case BANK_TAKE -> "Retirer de la banque";
            case INFO_BANK -> "Voir la banque";
            case FWARP -> "Créer / supprimer les warps publics (FW)";
            case SECRET -> "Activer le mode secret";
            case CULTURES -> "Fermes publiques (récolte par non-membres)";
            case SET_PERMS -> "Gérer les permissions";
            case RAID -> "Déclarer des raids";
            case PASDIC -> "Gérer les PASDIC";
            case WAR -> "Déclarer la guerre / la paix";
        };
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> subs = new ArrayList<>(Arrays.asList("create", "rename", "name", "renommer", "invite", "accept", "deny", "leave", "info", "list", "members",
                    "join", "kick", "ban", "promote", "demote", "claim", "unclaim", "autoclaim",
                    "spawn", "go", "setspawn", "warp", "setwarp", "delwarp",
                    "map", "bank", "power", "pouvoir", "secret", "pasdic", "protect", "raid", "revive", "reanimer",
                    "metier", "skill", "vuln", "creneau", "banniere", "siege",
                    "war", "enemy", "peace", "surrender", "ally", "perms", "help", "disband"));
            // Sous-commandes staff : ne pas les suggérer aux joueurs sans la permission.
            if (sender instanceof Player p && p.hasPermission(AdminBypassManager.PERMISSION)) {
                subs.add("bypass");
                subs.add("warend");
            }
            return subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("ban")
                    || sub.equals("promote") || sub.equals("demote")
                    || sub.equals("revive") || sub.equals("reanimer")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("autoclaim")) return Arrays.asList("on", "off");
            if (sub.equals("map")) {
                return Arrays.asList("chat").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("perms")) return Arrays.asList("recruit", "member", "officer", "leader");
            if (sub.equals("bank")) {
                return Arrays.asList("add", "take", "deposit", "withdraw", "power", "pouvoir").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("metier") || sub.equals("skill")) {
                return java.util.Arrays.asList("architecte", "negociant", "artisan", "chevalier").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("pasdic") || sub.equals("protect")) {
                return Arrays.asList("confirm").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("warp") || sub.equals("setwarp") || sub.equals("delwarp")) {
                String prefix = args[1].toLowerCase();
                Faction fac = sender instanceof Player p
                        ? factionManager.getPlayerFaction(p.getUniqueId()) : null;
                if (sub.equals("warp")) {
                    return plugin.getFactionWarpManager().getAll().stream()
                            .map(fr.cantale.plugin.factions.FactionWarp::getName)
                            .filter(n -> n.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
                if (fac == null) return Collections.emptyList();
                return plugin.getFactionWarpManager().getByFaction(fac.getId()).stream()
                        .map(fr.cantale.plugin.factions.FactionWarp::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
            if (sub.equals("war") || sub.equals("enemy") || sub.equals("peace") || sub.equals("surrender")
                    || sub.equals("ally") || sub.equals("warend")) {
                String prefix = args[1].toLowerCase();
                return factionManager.getAllFactions().stream()
                        .map(Faction::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("warend")) {
            String prefix = args[2].toLowerCase();
            return factionManager.getAllFactions().stream()
                    .map(Faction::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
