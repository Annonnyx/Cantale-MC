package fr.cantale.plugin.commands;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.database.SecretCooldownDAO;
import fr.cantale.plugin.cooldown.CooldownManager;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
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
            case "power", "pouvoir" -> handlePower(player);
            case "secret" -> handleSecret(player);
            case "perms", "permissions" -> handlePerms(player, args);
            case "members", "membres" -> handleMembers(player);
            case "warp" -> handleFactionWarp(player, args);
            case "setwarp" -> handleSetWarp(player, args);
            case "delwarp" -> handleDelWarp(player, args);
            case "home" -> handleFactionHome(player, args);
            case "sethome" -> handleSetFactionHome(player, args);
            case "promote", "promo" -> handlePromote(player, args);
            case "demote", "demo" -> handleDemote(player, args);
            case "c", "chat" -> handleChat(player, args);
            case "help" -> sendHelp(player);
            default -> player.sendMessage(formatMessage("&cSous-commande inconnue. Utilisez /f help"));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(formatMessage("&cUsage : /f create <nom>"));
            return;
        }

        String name = args[1];
        String nameError = factionManager.validateFactionName(name);
        if (nameError != null) {
            player.sendMessage(formatMessage("&c" + nameError));
            return;
        }
        if (factionManager.getPlayerFaction(player.getUniqueId()) != null) {
            player.sendMessage(formatMessage("&cVous êtes déjà dans une faction !"));
            return;
        }

        if (factionManager.getFaction(name) != null) {
            player.sendMessage(formatMessage("&cCe nom de faction existe déjà."));
            return;
        }

        // Vérifications de zone AVANT création : impossible dans une zone protégée ou déjà claim
        org.bukkit.Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (!plugin.getSpawnProtection().canClaim(loc)) {
            player.sendMessage(formatMessage("&cImpossible de créer une faction dans une zone protégée (spawn/warp)."));
            return;
        }

        if (factionManager.getClaimAt(world, chunkX, chunkZ) != null) {
            player.sendMessage(formatMessage("&cCe chunk est déjà claimé. Vous ne pouvez pas créer de faction ici."));
            return;
        }

        int minMembers = plugin.getConfigManager().getMinMembersCreate();
        // TODO: Vérifier qu'il y a assez de joueurs autour

        // Générer un tag unique (4 caractères max)
        String tag = factionManager.generateUniqueTag(name, -1);

        Faction faction = factionManager.createFaction(name, tag, "", player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cErreur lors de la création de la faction."));
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

        player.sendMessage(formatMessage("&aFaction &e" + name + " &acrée avec succès !"));
        if (claimed) {
            player.sendMessage(formatMessage("&aChunk actuel claimé automatiquement."));
        }
        player.sendMessage(formatMessage("&aSpawn de faction défini à votre position."));

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
            player.sendMessage(formatMessage("&cSeul le chef de faction peut la renommer."));
            return;
        }

        String newName = args[1];
        String oldName = faction.getName();
        String error = factionManager.renameFaction(faction, newName);
        if (error != null) {
            player.sendMessage(formatMessage("&c" + error));
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
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.FWARP)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission d'utiliser les warps de faction."));
            return;
        }
        if (args.length < 2) {
            // Lister
            player.sendMessage(formatMessage("&6=== Warps de faction ==="));
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement st = c.prepareStatement("SELECT name FROM faction_warps WHERE faction_id = ?")) {
                st.setInt(1, faction.getId());
                java.sql.ResultSet rs = st.executeQuery();
                int count = 0;
                while (rs.next()) { player.sendMessage(formatMessage("&7- &e" + rs.getString("name"))); count++; }
                if (count == 0) player.sendMessage(formatMessage("&7Aucun warp défini. Utilisez /f setwarp <nom>"));
            } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
            return;
        }
        // Tp à un warp
        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT world, x, y, z FROM faction_warps WHERE faction_id = ? AND name = ?")) {
            st.setInt(1, faction.getId());
            st.setString(2, args[1]);
            java.sql.ResultSet rs = st.executeQuery();
            if (!rs.next()) { player.sendMessage(formatMessage("&cWarp introuvable.")); return; }
            org.bukkit.World w = Bukkit.getWorld(rs.getString("world"));
            if (w == null) { player.sendMessage(formatMessage("&cMonde inexistant.")); return; }
            org.bukkit.Location destination = new org.bukkit.Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
            int delay = plugin.getTeleportDelayManager().getTeleportDelay(player);
            if (delay > 0) {
                long ticksBefore = Math.max(0, (delay - 1) * 20L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (destination.getWorld() != null) destination.getChunk().load(true);
                }, ticksBefore);
            } else {
                destination.getChunk().load(true);
            }
            plugin.getTeleportDelayManager().delayTeleport(player, () -> {
                player.teleport(destination);
                player.sendMessage(formatMessage("&aTéléporté au warp &e" + args[1] + "&a."));
            });
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
    }

    private void handleSetWarp(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(formatMessage("&cUsage : /f setwarp <nom>")); return; }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.FWARP)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission.")); return;
        }
        // Vérifier que le warp est dans un claim de la faction
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        Claim claim = factionManager.getClaimAt(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(formatMessage("&cVous devez être dans un claim de votre faction."));
            return;
        }
        // Vérifier le quota max selon power
        int maxWarps = faction.getMaxWarps();
        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM faction_warps WHERE faction_id = ?")) {
            st.setInt(1, faction.getId());
            java.sql.ResultSet rs = st.executeQuery();
            if (rs.next() && rs.getInt(1) >= maxWarps) {
                player.sendMessage(formatMessage("&cQuota de warps atteint (" + maxWarps + "). Augmentez le pouvoir de votre faction."));
                return;
            }
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); return; }

        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("INSERT INTO faction_warps (faction_id, name, world, x, y, z) VALUES (?,?,?,?,?,?)")) {
            st.setInt(1, faction.getId());
            st.setString(2, args[1]);
            st.setString(3, player.getWorld().getName());
            st.setDouble(4, player.getLocation().getX());
            st.setDouble(5, player.getLocation().getY());
            st.setDouble(6, player.getLocation().getZ());
            st.executeUpdate();
            player.sendMessage(formatMessage("&aWarp &e" + args[1] + " &acréé."));
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
    }

    private void handleDelWarp(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(formatMessage("&cUsage : /f delwarp <nom>")); return; }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.FWARP)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission.")); return;
        }
        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("DELETE FROM faction_warps WHERE faction_id = ? AND name = ?")) {
            st.setInt(1, faction.getId());
            st.setString(2, args[1]);
            int rows = st.executeUpdate();
            if (rows > 0) player.sendMessage(formatMessage("&aWarp supprimé."));
            else player.sendMessage(formatMessage("&cWarp introuvable."));
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
    }

    private void handleFactionHome(Player player, String[] args) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        UUID uuid = player.getUniqueId();
        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT name, world, x, y, z FROM faction_homes WHERE faction_id = ? AND player_uuid = ?" + (args.length >= 2 ? " AND name = ?" : ""))) {
            st.setInt(1, faction.getId());
            st.setString(2, uuid.toString());
            if (args.length >= 2) st.setString(3, args[1]);
            java.sql.ResultSet rs = st.executeQuery();
            if (args.length < 2) {
                player.sendMessage(formatMessage("&6=== Vos homes de faction ==="));
                int count = 0;
                while (rs.next()) { player.sendMessage(formatMessage("&7- &e" + rs.getString("name"))); count++; }
                if (count == 0) player.sendMessage(formatMessage("&7Aucun home. Utilisez /f sethome <nom>"));
            } else if (rs.next()) {
                org.bukkit.World w = Bukkit.getWorld(rs.getString("world"));
                if (w == null) { player.sendMessage(formatMessage("&cMonde inexistant.")); return; }
                org.bukkit.Location destination = new org.bukkit.Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                plugin.getTeleportDelayManager().delayTeleport(player, () -> {
                    player.teleport(destination);
                    player.sendMessage(formatMessage("&aTéléporté à votre home &e" + args[1] + "&a."));
                });
            } else {
                player.sendMessage(formatMessage("&cHome introuvable."));
            }
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
    }

    private void handleSetFactionHome(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(formatMessage("&cUsage : /f sethome <nom>")); return; }
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) { player.sendMessage(formatMessage("&cVous n'êtes pas dans une faction.")); return; }
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId()) && rank == FactionRank.RECRUIT) {
            player.sendMessage(formatMessage("&cLes recrues ne peuvent pas créer de home. Montez au grade Membre."));
            return;
        }
        // Doit être dans un claim de la faction
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        Claim claim = factionManager.getClaimAt(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (claim == null || claim.getFactionId() != faction.getId()) {
            player.sendMessage(formatMessage("&cVous devez être dans un claim de votre faction."));
            return;
        }
        // Vérifier quota fhomes (par joueur)
        int maxFhomes = faction.getMaxFhomes();
        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM faction_homes WHERE faction_id = ? AND player_uuid = ?")) {
            st.setInt(1, faction.getId());
            st.setString(2, player.getUniqueId().toString());
            java.sql.ResultSet rs = st.executeQuery();
            if (rs.next() && rs.getInt(1) >= Math.max(1, maxFhomes)) {
                player.sendMessage(formatMessage("&cQuota de homes atteint (" + Math.max(1, maxFhomes) + ")."));
                return;
            }
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); return; }

        try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("INSERT INTO faction_homes (faction_id, player_uuid, name, world, x, y, z) VALUES (?,?,?,?,?,?,?)")) {
            st.setInt(1, faction.getId());
            st.setString(2, player.getUniqueId().toString());
            st.setString(3, args[1]);
            st.setString(4, player.getWorld().getName());
            st.setDouble(5, player.getLocation().getX());
            st.setDouble(6, player.getLocation().getY());
            st.setDouble(7, player.getLocation().getZ());
            st.executeUpdate();
            player.sendMessage(formatMessage("&aHome &e" + args[1] + " &acréé."));
        } catch (java.sql.SQLException ex) { player.sendMessage(formatMessage("&cErreur DB.")); }
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

        // Vérifier le cooldown (20 secondes)
        CooldownManager cooldownManager = plugin.getCooldownManager();
        long COOLDOWN_MS = 20000;
        if (cooldownManager.hasCooldown(player, "fspawn", COOLDOWN_MS)) {
            long remaining = cooldownManager.getRemainingCooldown(player, "fspawn", COOLDOWN_MS);
            player.sendMessage(formatMessage("&cCooldown ! Attendez encore " + (remaining / 1000) + " secondes."));
            return;
        }

        teleportToFactionSpawn(player, faction, true);
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

        teleportToFactionSpawn(player, faction, false);
    }

    private void teleportToFactionSpawn(Player player, Faction faction, boolean applyCooldown) {
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
        int delay = plugin.getTeleportDelayManager().getTeleportDelay(player);
        if (spawnLoc.getChunk() != null) {
            if (delay > 0) {
                long ticksBefore = Math.max(0, (delay - 1) * 20L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (spawnLoc.getWorld() != null) spawnLoc.getChunk().load(true);
                }, ticksBefore);
            } else {
                spawnLoc.getChunk().load(true);
            }
        }

        plugin.getTeleportDelayManager().delayTeleport(player, () -> {
            player.teleport(spawnLoc);
            player.sendMessage(formatMessage("&aTéléporté au spawn de votre faction."));
            if (applyCooldown) {
                plugin.getCooldownManager().setCooldown(player, "fspawn");
            }
        });
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
        player.sendMessage(ChatColor.GRAY + "/f spawn | /f go | /f setspawn - Spawn de faction");
        player.sendMessage(ChatColor.GRAY + "/f warp [nom] | /f setwarp <nom> | /f delwarp <nom>");
        player.sendMessage(ChatColor.GRAY + "/f home [nom] | /f sethome <nom>");
        player.sendMessage(ChatColor.GRAY + "/f c [message] - Basculer ou envoyer un message dans le chat de faction");
        player.sendMessage(ChatColor.GRAY + "/f bank - Banque GUI | /f bank power [n] (sans n = GUI pouvoir)");
        player.sendMessage(ChatColor.GRAY + "/f power - Pouvoir, quotas, paliers + achat (GUI)");
        player.sendMessage(ChatColor.GRAY + "/f perms [grade] - Voir les permissions par grade");
        player.sendMessage(ChatColor.GRAY + "/f secret - Activer le mode secret (1h)");
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
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(formatMessage("&cVous devez être dans une faction pour utiliser cette commande."));
            return;
        }

        // Vérifier la permission
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        if (!faction.isLeader(player.getUniqueId())
                && !permissionManager.hasPermission(faction.getId(), rank, FactionPermission.SECRET)) {
            player.sendMessage(formatMessage("&cVous n'avez pas la permission d'utiliser cette commande."));
            return;
        }

        // Vérifier le cooldown (24 heures)
        SecretCooldownDAO cooldownDAO = new SecretCooldownDAO(plugin);
        long cooldownMillis = 24 * 60 * 60 * 1000; // 24 heures
        if (!cooldownDAO.canUseSecret(player.getUniqueId(), cooldownMillis)) {
            long lastUsed = cooldownDAO.getLastUsed(player.getUniqueId());
            long remaining = cooldownMillis - (System.currentTimeMillis() - lastUsed * 1000);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            player.sendMessage(formatMessage("&cCooldown ! Attendez encore " + hours + "h " + minutes + "min."));
            return;
        }

        // Activer le mode secret (1 heure)
        long secretDuration = 60 * 60; // 1 heure en secondes
        long secretUntil = (System.currentTimeMillis() / 1000) + secretDuration;
        faction.setSecretUntil(secretUntil);
        factionManager.saveFaction(faction);

        // Enregistrer le cooldown
        cooldownDAO.setLastUsed(player.getUniqueId(), System.currentTimeMillis() / 1000);

        player.sendMessage(formatMessage("&aMode secret activé pour 1 heure !"));
        player.sendMessage(formatMessage("&7Vos claims et spawn sont cachés aux autres factions."));
        Bukkit.broadcastMessage(formatMessage("&6[Faction] &e" + faction.getName() + " &7est entré en mode secret."));
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
            case FWARP -> "Gérer / utiliser les warps";
            case SECRET -> "Activer le mode secret";
            case CULTURES -> "Fermes publiques (récolte par non-membres)";
            case SET_PERMS -> "Gérer les permissions";
            case FHOME -> "Créer des homes";
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
            return Arrays.asList("create", "rename", "name", "renommer", "invite", "accept", "deny", "leave", "info", "list", "members",
                    "join", "kick", "ban", "promote", "demote", "claim", "unclaim", "autoclaim",
                    "spawn", "go", "setspawn", "warp", "setwarp", "delwarp", "home", "sethome",
                    "map", "bank", "power", "pouvoir", "secret", "perms", "help", "disband")
                    .stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("ban")
                    || sub.equals("promote") || sub.equals("demote")) {
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
        }
        return Collections.emptyList();
    }
}
