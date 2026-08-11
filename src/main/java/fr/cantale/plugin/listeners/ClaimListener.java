package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.permissions.FactionPermissions;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimListener implements Listener {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final Map<UUID, Long> lastClaimMessage = new HashMap<>();
    private static final long CLAIM_MESSAGE_COOLDOWN_MS = 2000;

    private final Set<Material> protectedStorageBlocks;
    private final Set<Material> explosivePlaceBlocks;

    public ClaimListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.protectedStorageBlocks = plugin.getConfigManager().getClaimProtectedStorageBlocks();
        this.explosivePlaceBlocks = plugin.getConfigManager().getClaimExplosivePlaceBlocks();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getActionLogger().logBlockBreak(
            event.getPlayer().getName(),
            event.getBlock().getType().name(),
            event.getBlock().getLocation().toString()
        );

        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlock().getLocation();
        Material type = event.getBlock().getType();

        // Membres, alliés et admins peuvent tout casser.
        if (canInteractFor(player, loc, false)) {
            return;
        }

        // PASDIC : claim spécial — TOUT le claim est protégé, aucun bloc cassable.
        // (canInteractFor a déjà envoyé le message PASDIC au joueur.)
        Claim claim = getClaimAt(loc);
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }

        // Étranger : interdiction de casser les blocs de stockage et les cultures.
        if (isProtectedStorageBlock(type) || isCultureBlock(type)) {
            event.setCancelled(true);
            player.sendMessage(formatMessage("&cVous ne pouvez pas casser ce block dans ce claim."));
        }
        // Sinon l'étranger peut casser le bloc normalement.
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getActionLogger().logBlockPlace(
            event.getPlayer().getName(),
            event.getBlock().getType().name(),
            event.getBlock().getLocation().toString()
        );

        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlock().getLocation();
        Material type = event.getBlock().getType();

        // Stockage : uniquement dans les claims de SA faction (pas wilderness, pas claims étrangers/alliés).
        if (isProtectedStorageBlock(type) && !player.isOp() && !player.hasPermission("cantale.admin")) {
            Claim claim = getClaimAt(loc);
            Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
            Faction claimFaction = claim == null ? null : factionManager.getFaction(claim.getFactionId());
            boolean ownClaim = claim != null && playerFaction != null && claimFaction != null
                    && playerFaction.getId() == claimFaction.getId();
            if (!ownClaim) {
                event.setCancelled(true);
                if (claim == null) {
                    player.sendMessage(formatMessage("&cTu ne peux poser du stockage que dans les claims de ta faction."));
                } else {
                    player.sendMessage(formatMessage("&cTu ne peux poser du stockage que dans les claims de ta faction."));
                }
                return;
            }
        }

        // Membres, alliés et admins peuvent tout poser.
        if (canInteractFor(player, loc, false)) {
            return;
        }

        // PASDIC : claim spécial — TOUT le claim est protégé, aucune pose (TNT incluse).
        Claim claim = getClaimAt(loc);
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }

        // Étranger : autorisation uniquement des blocs "explosifs" configurés.
        if (!isExplosivePlaceBlock(type)) {
            event.setCancelled(true);
            player.sendMessage(formatMessage("&cVous ne pouvez pas poser ce block dans ce claim."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Admins / ops : pas de restriction claim
        if (attacker.isOp() || attacker.hasPermission("cantale.admin")) return;

        Claim claim = getClaimAt(victim.getLocation());
        if (claim == null) return; // wilderness : PvP libre

        // Zones PASDIC (spawn/warp) : pas de PvP
        if (claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }

        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        if (claimFaction == null) return;

        Faction victimFaction = factionManager.getPlayerFaction(victim.getUniqueId());

        // Safe : on ne peut pas être tué dans le claim de SA propre faction
        if (victimFaction != null && victimFaction.getId() == claimFaction.getId()) {
            event.setCancelled(true);
            attacker.sendMessage(formatMessage("&cCe joueur est protégé dans le claim de sa faction."));
            return;
        }

        // Sinon (intrus dans ton claim, claim ennemi, etc.) : PvP autorisé
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        org.bukkit.Location loc = event.getClickedBlock().getLocation();
        Claim claim = getClaimAt(loc);

        // Hors claim (wilderness / structures générées) : tout le monde peut ouvrir coffres, portes, etc.
        // Seule la pose de stockage est restreinte (voir onBlockPlace).
        if (claim == null) {
            return;
        }

        if (!canInteract(event.getPlayer(), loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(formatMessage("&cVous ne pouvez pas interagir avec ce block dans ce claim."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Seuls les blocs dans des claims PASDIC sont protégés ; le reste explose normalement.
        event.blockList().removeIf(this::isPasdicBlock);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        // Gère les explosions déclenchées par des blocs (lit, ancre de réapparition, cristal de l'End...)
        event.blockList().removeIf(this::isPasdicBlock);
    }

    private boolean isPasdicBlock(org.bukkit.block.Block block) {
        Claim claim = getClaimAt(block.getLocation());
        return claim != null && claim.isPasdic();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().getX() == event.getTo().getChunk().getX() &&
            event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()) {
            return; // Même chunk
        }

        Player player = event.getPlayer();
        Claim fromClaim = getClaimAt(event.getFrom());
        Claim toClaim = getClaimAt(event.getTo());

        // Auto-claim si activé et dans wilderness (silencieux si échec)
        if (toClaim == null && factionManager.isAutoClaimEnabled(player.getUniqueId())) {
            int chunkX = event.getTo().getBlockX() >> 4;
            int chunkZ = event.getTo().getBlockZ() >> 4;
            if (factionManager.autoClaim(player.getUniqueId(), event.getTo().getWorld().getName(), chunkX, chunkZ)) {
                sendClaimMessage(player, "&aAuto-claim du chunk actuel !");
            }
        }

        // Gestion du mode PASDIC (Adventure Mode)
        // Exemptés : membres de la faction propriétaire, admins/ops, et joueurs
        // en créatif/spectateur (on ne touche qu'au survival).
        boolean fromPasdic = fromClaim != null && fromClaim.isPasdic();
        boolean toPasdic = toClaim != null && toClaim.isPasdic();

        if (!fromPasdic && toPasdic) {
            if (player.getGameMode() == GameMode.SURVIVAL && !isPasdicExempt(player, toClaim)) {
                player.setGameMode(GameMode.ADVENTURE);
                sendClaimMessage(player, "&bMode Adventure activé - Zone PASDIC");
            }
        } else if (fromPasdic && !toPasdic) {
            // L'Adventure n'étant imposé que par PASDIC sur ce serveur, tout joueur
            // non exempté qui sort d'un PASDIC en Adventure repasse en Survival.
            if (player.getGameMode() == GameMode.ADVENTURE && !isPasdicExempt(player, fromClaim)) {
                player.setGameMode(GameMode.SURVIVAL);
                sendClaimMessage(player, "&aMode Survival rétabli");
            }
        }

        // Messages d'entrée/sortie de claim (avec anti-spam)
        int fromFactionId = fromClaim == null ? -1 : fromClaim.getFactionId();
        int toFactionId = toClaim == null ? -1 : toClaim.getFactionId();

        if (fromFactionId != toFactionId) {
            if (fromClaim == null && toClaim != null) {
                Faction faction = factionManager.getFaction(toClaim.getFactionId());
                if (faction != null) {
                    sendClaimMessage(player, "&eEntrée dans le claim de &6" + faction.getName() + " &e(&7" + faction.getTag() + "&e)");
                }
            } else if (fromClaim != null && toClaim == null) {
                sendClaimMessage(player, "&eSortie du claim - Wilderness");
            } else if (fromClaim != null && toClaim != null) {
                Faction fromFaction = factionManager.getFaction(fromClaim.getFactionId());
                Faction toFaction = factionManager.getFaction(toClaim.getFactionId());
                if (fromFaction != null && toFaction != null) {
                    sendClaimMessage(player, "&eSortie de &6" + fromFaction.getName() + " &e→ Entrée dans &6" + toFaction.getName());
                }
            }
            player.setMetadata("cantale_last_claim_faction", new FixedMetadataValue(plugin, toFactionId));
        }
    }

    private void sendClaimMessage(Player player, String message) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long last = lastClaimMessage.get(uuid);
        if (last != null && now - last < CLAIM_MESSAGE_COOLDOWN_MS) return;
        lastClaimMessage.put(uuid, now);
        player.sendMessage(formatMessage(message));
    }

    private boolean canInteract(Player player, org.bukkit.Location location) {
        return canInteractFor(player, location, false);
    }

    /** Membres de la faction propriétaire et staff : pas de mode Adventure forcé en PASDIC. */
    private boolean isPasdicExempt(Player player, Claim claim) {
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;
        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        return claimFaction != null && playerFaction != null
                && claimFaction.getId() == playerFaction.getId();
    }

    /**
     * @param isCulture true si l'action est considérée comme "culture" (cassage de plantes/cultures).
     */
    private boolean canInteractFor(Player player, org.bukkit.Location location, boolean isCulture) {
        // Bypass admin/op
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;

        Claim claim = getClaimAt(location);
        if (claim == null) return true; // wilderness

        if (claim.isPasdic()) {
            // PASDIC : aucune destruction autorisée (sauf membre de la faction)
            Faction claimFaction = factionManager.getFaction(claim.getFactionId());
            Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
            if (playerFaction != null && claimFaction != null && playerFaction.getId() == claimFaction.getId()) {
                return true;
            }
            player.sendMessage(formatMessage("&cCe chunk est en mode PASDIC — aucune destruction autorisée."));
            return false;
        }

        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        if (claimFaction == null) return true;

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());

        // Membre de la faction propriétaire
        if (playerFaction != null && playerFaction.getId() == claimFaction.getId()) {
            return true;
        }

        // Faction alliancée
        DiplomacyManager diplomacy = plugin.getDiplomatieManager();
        if (playerFaction != null && diplomacy != null && diplomacy.isAllied(playerFaction.getId(), claimFaction.getId())) {
            return true;
        }

        // Permission CULTURES sur la faction propriétaire pour le rang publique (recruit)
        // Permet à tout le monde de récolter dans le claim si la faction l'autorise au rang RECRUIT
        if (isCulture && FactionPermissions.allowsPublicFarming()) {
            return true;
        }

        return false;
    }

    /** Liste de matériaux considérés comme "cultures" : permettent l'interaction si le claim a CULTURES activé. */
    private boolean isCultureBlock(org.bukkit.Material mat) {
        String n = mat.name();
        return n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO") || n.contains("BEETROOT")
                || n.contains("NETHER_WART") || n.contains("PUMPKIN") || n.contains("MELON")
                || n.contains("CACTUS") || n.contains("SUGAR_CANE") || n.contains("BAMBOO")
                || n.contains("COCOA") || n.contains("BERRIES") || n.contains("SEED")
                || n.contains("FLOWER") || n.contains("MUSHROOM") || n.contains("KELP")
                || n.contains("SAPLING") || n.contains("FERN") || n.contains("GRASS")
                || n.contains("VINE") || n.contains("LEAVES");
    }

    private boolean isProtectedStorageBlock(Material mat) {
        return protectedStorageBlocks.contains(mat);
    }

    private boolean isExplosivePlaceBlock(Material mat) {
        return explosivePlaceBlocks.contains(mat);
    }

    private Claim getClaimAt(org.bukkit.Location location) {
        String world = location.getWorld().getName();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return factionManager.getClaimAt(world, chunkX, chunkZ);
    }

    private String formatMessage(String msg) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagePrefix());
        return prefix + ChatColor.translateAlternateColorCodes('&', msg);
    }
}
