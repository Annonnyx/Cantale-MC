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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimListener implements Listener {

    private final CantalePlugin plugin;
    private final FactionManager factionManager;
    private final Map<UUID, Long> lastClaimMessage = new HashMap<>();
    private static final long CLAIM_MESSAGE_COOLDOWN_MS = 2000;

    public ClaimListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getActionLogger().logBlockBreak(
            event.getPlayer().getName(),
            event.getBlock().getType().name(),
            event.getBlock().getLocation().toString()
        );

        boolean isCulture = isCultureBlock(event.getBlock().getType());
        if (!canInteractFor(event.getPlayer(), event.getBlock().getLocation(), isCulture)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(formatMessage("&cVous ne pouvez pas casser ce block dans ce claim."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getActionLogger().logBlockPlace(
            event.getPlayer().getName(),
            event.getBlock().getType().name(),
            event.getBlock().getLocation().toString()
        );
        
        if (!canInteract(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(formatMessage("&cVous ne pouvez pas poser ce block dans ce claim."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            Claim claim = getClaimAt(victim.getLocation());
            if (claim != null) {
                // PvP désactivé dans les warps (claims spéciaux)
                // Pour l'instant, PvP autorisé dans les claims de faction
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            if (!canInteract(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(formatMessage("&cVous ne pouvez pas interagir avec ce block dans ce claim."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Vérifier si l'explosion est dans un chunk PASDIC
        for (org.bukkit.block.Block block : event.blockList()) {
            Claim claim = getClaimAt(block.getLocation());
            if (claim != null && claim.isPasdic()) {
                event.setCancelled(true);
                return;
            }
        }
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
        boolean fromPasdic = fromClaim != null && fromClaim.isPasdic();
        boolean toPasdic = toClaim != null && toClaim.isPasdic();

        if (!fromPasdic && toPasdic) {
            player.setGameMode(GameMode.ADVENTURE);
            sendClaimMessage(player, "&bMode Adventure activé - Zone PASDIC");
        } else if (fromPasdic && !toPasdic) {
            player.setGameMode(GameMode.SURVIVAL);
            sendClaimMessage(player, "&aMode Survival rétabli");
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
