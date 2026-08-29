package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.permissions.FactionPermissions;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.block.BlockState;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.BlockInventoryHolder;
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
    private final Set<Material> pasdicBannedContainers;
    private final Set<CreatureSpawnEvent.SpawnReason> pasdicAllowedSpawnReasons;

    public ClaimListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.factionManager = plugin.getFactionManager();
        this.protectedStorageBlocks = plugin.getConfigManager().getClaimProtectedStorageBlocks();
        this.explosivePlaceBlocks = plugin.getConfigManager().getClaimExplosivePlaceBlocks();
        this.pasdicBannedContainers = plugin.getConfigManager().getPasdicBannedContainers();
        this.pasdicAllowedSpawnReasons = plugin.getConfigManager().getPasdicAllowedSpawnReasons();
        if (this.pasdicAllowedSpawnReasons.isEmpty()) {
            plugin.getLogger().info("[PASDIC] allowed-spawn-reasons vide : limitation des spawns désactivée.");
        }
    }

    /**
     * PASDIC : seuls les spawns de la allow-list (spawners) sont autorisés.
     * Sortie rapide avant tout lookup de chunk : la majorité des spawns ont lieu hors PASDIC.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (pasdicAllowedSpawnReasons.isEmpty()) return;
        if (pasdicAllowedSpawnReasons.contains(event.getSpawnReason())) return;

        Claim claim = getClaimAt(event.getLocation());
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
        }
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

        // PASDIC : seuls les membres de la faction peuvent casser.
        Claim claim = getClaimAt(loc);
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }

        // Raid : les assaillants en guerre peuvent casser pendant un raid actif.
        if (claim != null && isRaiding(player, claim)) {
            return;
        }

        // Hors raid : aucun étranger ne peut casser dans un claim.
        event.setCancelled(true);
        player.sendMessage(formatMessage("&cTu ne peux pas casser dans ce claim sans un raid actif."));
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

        Claim claim = getClaimAt(loc);
        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        Faction claimFaction = claim == null ? null : factionManager.getFaction(claim.getFactionId());
        boolean ownClaim = claim != null && playerFaction != null && claimFaction != null
                && playerFaction.getId() == claimFaction.getId();

        // PASDIC : interdiction de poser des conteneurs de stockage, même pour les membres.
        // Admins / ops / bypass admin : exemptés.
        if (claim != null && claim.isPasdic() && isPasdicBannedContainer(type)
                && !player.isOp() && !player.hasPermission("cantale.admin")
                && !plugin.getAdminBypassManager().isBypassing(player)) {
            event.setCancelled(true);
            player.sendMessage(formatMessage("&cLes conteneurs de stockage sont interdits dans un PASDIC."));
            return;
        }

        // Stockage : uniquement dans les claims de SA faction (pas wilderness, pas claims étrangers/alliés).
        // Exception wilderness : CRAFTING_TABLE + fours (FURNACE, BLAST_FURNACE, SMOKER) — ateliers utiles hors claim.
        if (isProtectedStorageBlock(type) && !player.isOp() && !player.hasPermission("cantale.admin")
                && !plugin.getAdminBypassManager().isBypassing(player)) {
            if (!(claim == null && isWildernessAllowedStorage(type))) {
                if (!ownClaim) {
                    event.setCancelled(true);
                    player.sendMessage(formatMessage("&cTu ne peux poser du stockage que dans les claims de ta faction."));
                    return;
                }
            }
        }

        // Membres, alliés et admins peuvent tout poser.
        if (canInteractFor(player, loc, false)) {
            return;
        }

        // PASDIC : seuls les membres peuvent poser.
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }

        // Raid : les assaillants en guerre peuvent poser des blocs pendant un raid actif.
        if (claim != null && isRaiding(player, claim)) {
            return;
        }

        // Hors raid : aucun étranger ne peut poser dans un claim.
        event.setCancelled(true);
        player.sendMessage(formatMessage("&cTu ne peux pas poser de bloc dans ce claim sans un raid actif."));
    }

    /**
     * PvP one-way dans les claims (indépendant du tag combat) :
     * <ul>
     *   <li>Étranger → membre dans le claim de SA fac : toujours annulé</li>
     *   <li>Membre / allié → étranger dans ce claim : autorisé</li>
     *   <li>PASDIC : aucun PvP</li>
     *   <li>Wilderness : inchangé (libre)</li>
     * </ul>
     * HIGHEST pour que le tag combat (MONITOR) ne voie que les coups non annulés
     * et ne puisse jamais « autoriser » un hit chez soi.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Admins / ops / bypass admin (/f bypass) : pas de restriction claim
        if (attacker.isOp() || attacker.hasPermission("cantale.admin")
                || plugin.getAdminBypassManager().isBypassing(attacker)) return;

        Claim claim = getClaimAt(victim.getLocation());
        if (claim == null) return; // wilderness : PvP libre

        // Zones PASDIC (spawn/warp) : impossible d'initier du PvP
        if (claim.isPasdic()) {
            event.setCancelled(true);
            attacker.sendMessage(formatMessage("&cPvP interdit en zone PASDIC (spawn / warp)."));
            return;
        }

        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        if (claimFaction == null) return;

        Faction victimFaction = factionManager.getPlayerFaction(victim.getUniqueId());

        // One-way : un membre chez lui ne peut pas être frappé (tag combat ou non).
        // Les membres / alliés peuvent frapper les étrangers présents dans le claim.
        if (victimFaction != null && victimFaction.getId() == claimFaction.getId()) {
            event.setCancelled(true);
            attacker.sendMessage(formatMessage("&cCe joueur est protégé dans le claim de sa faction."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        org.bukkit.Location loc = event.getClickedBlock().getLocation();
        Claim claim = getClaimAt(loc);

        // Hors claim (wilderness / structures générées) : tout le monde peut ouvrir coffres, portes, etc.
        // Pose stockage restreinte (voir onBlockPlace) ; CRAFTING_TABLE + fours autorisés en wilderness.
        if (claim == null) {
            return;
        }

        Player player = event.getPlayer();
        Material type = event.getClickedBlock().getType();

        // Conteneurs lootables : membres de la fac OK ; alliés & ennemis = vol Chèvre uniquement.
        // Ateliers (table de craft, enclume…) restent sur canInteract (alliés OK).
        if (isStealableContainer(type)) {
            if (!canAccessClaimStorage(player, claim)) {
                event.setCancelled(true);
                if (!player.isOp() && !player.hasPermission("cantale.admin")) {
                    player.sendMessage(formatMessage(
                            "&cFouiller les conteneurs d'un claim étranger est réservé au grade Chèvre."));
                }
            }
            return;
        }

        // Raid : TNT / cristaux / ancres / lodestone / briquet / seaux / wither — uniquement pendant un raid.
        if (!claim.isPasdic() && isRaiding(player, claim) && isRaidInteract(event)) {
            return;
        }

        // Portes, boutons, etc. : membres + alliés (+ admin) — pas les ennemis.
        if (!canInteract(player, loc)) {
            event.setCancelled(true);
            player.sendMessage(formatMessage("&cVous ne pouvez pas interagir avec ce block dans ce claim."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlockClicked().getLocation();
        if (canInteractFor(player, loc, false)) return;

        Claim claim = getClaimAt(loc);
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }
        // Raid : eau et lave autorisées pour les assaillants.
        if (claim != null && isRaiding(player, claim)) {
            return;
        }
        Material bucket = event.getBucket();
        if (bucket == Material.WATER_BUCKET || bucket == Material.LAVA_BUCKET) {
            event.setCancelled(true);
            player.sendMessage(formatMessage("&cTu ne peux utiliser des seaux que pendant un raid actif."));
            return;
        }
        event.setCancelled(true);
        player.sendMessage(formatMessage("&cVous ne pouvez pas utiliser ça dans ce claim."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(org.bukkit.event.entity.EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        org.bukkit.Location loc = event.getEntity().getLocation();
        if (canInteractFor(player, loc, false)) return;

        Claim claim = getClaimAt(loc);
        if (claim != null && claim.isPasdic()) {
            event.setCancelled(true);
            return;
        }
        // Raid : cristaux, TNT minecart et TNT autorisées pour les assaillants.
        if (claim != null && isRaiding(player, claim)) {
            org.bukkit.entity.EntityType type = event.getEntityType();
            if (type == org.bukkit.entity.EntityType.END_CRYSTAL
                    || type == org.bukkit.entity.EntityType.TNT_MINECART
                    || type == org.bukkit.entity.EntityType.TNT) {
                return;
            }
        }
        event.setCancelled(true);
    }

    /**
     * Filet de sécurité : ouverture d'inventaire bloc (coffre, hopper…) même si
     * {@link PlayerInteractEvent} a été contourné.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof BlockInventoryHolder holder)) return;

        BlockState state = holder.getBlock().getState();
        org.bukkit.Location loc = state.getLocation();
        Claim claim = getClaimAt(loc);
        if (claim == null) return;

        Material type = state.getType();
        if (!isStealableContainer(type)) return;

        if (!canAccessClaimStorage(player, claim)) {
            event.setCancelled(true);
            player.sendMessage(formatMessage(
                    "&cFouiller les conteneurs d'un claim étranger est réservé au grade Chèvre."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (shouldCancelExplosion(event.getLocation(), event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(this::isExplosionProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (shouldCancelExplosion(event.getBlock().getLocation(), null)) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(this::isExplosionProtectedBlock);
    }

    private boolean isPasdicBlock(org.bukkit.block.Block block) {
        Claim claim = getClaimAt(block.getLocation());
        return claim != null && claim.isPasdic();
    }

    private boolean isExplosionProtectedBlock(org.bukkit.block.Block block) {
        Claim claim = getClaimAt(block.getLocation());
        if (claim == null) return false;
        if (claim.isPasdic()) return true;
        return !plugin.getRaidManager().isRaidActive(claim);
    }

    private boolean shouldCancelExplosion(org.bukkit.Location origin, org.bukkit.entity.Entity source) {
        Claim originClaim = getClaimAt(origin);
        if (originClaim == null) return false;
        if (originClaim.isPasdic()) return true;
        if (plugin.getRaidManager().isRaidActive(originClaim)) return false;
        // Wither : exception pour casser l'obsidienne (hors raid et hors PASDIC).
        if (source != null && isWitherEntity(source)) return false;
        Player sourcePlayer = getPlayerSource(source);
        if (sourcePlayer != null && isMemberOfClaim(sourcePlayer, originClaim)) return false;
        // Bypass admin (/f bypass) : les explosions déclenchées par l'admin ne sont pas annulées.
        if (sourcePlayer != null && plugin.getAdminBypassManager().isBypassing(sourcePlayer)) return false;
        return true;
    }

    private boolean isWitherEntity(org.bukkit.entity.Entity entity) {
        if (entity == null) return false;
        org.bukkit.entity.EntityType type = entity.getType();
        return type == org.bukkit.entity.EntityType.WITHER
                || type == org.bukkit.entity.EntityType.WITHER_SKULL;
    }

    private Player getPlayerSource(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        return null;
    }

    private boolean isMemberOfClaim(Player player, Claim claim) {
        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        return playerFaction != null && playerFaction.getId() == claim.getFactionId();
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
     * Accès aux conteneurs / blocs de stockage d'un claim.
     * <ul>
     *   <li>Membres de la fac propriétaire : toujours</li>
     *   <li>Alliés / ennemis / sans fac : uniquement grade Chèvre ({@code canStealInClaims})</li>
     *   <li>PASDIC : membres seulement (pas de vol, même Chèvre)</li>
     *   <li>op / cantale.admin : bypass modération (pas un avantage de grade)</li>
     * </ul>
     */
    private boolean canAccessClaimStorage(Player player, Claim claim) {
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;
        // Bypass admin (/f bypass) : ouvre tous les conteneurs, y compris en PASDIC.
        if (plugin.getAdminBypassManager().isBypassing(player)) return true;

        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());

        if (playerFaction != null && claimFaction != null
                && playerFaction.getId() == claimFaction.getId()) {
            return true;
        }

        // Zones spawn/warp : pas de fouille étrangère
        if (claim.isPasdic()) {
            return false;
        }

        var rank = plugin.getRankManager().getRank(player);
        return rank != null && rank.canStealInClaims();
    }

    /**
     * @param isCulture true si l'action est considérée comme "culture" (cassage de plantes/cultures).
     */
    private boolean canInteractFor(Player player, org.bukkit.Location location, boolean isCulture) {
        // Bypass admin/op
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;
        // Bypass admin (/f bypass) : ignore toutes les protections de claims (y compris PASDIC).
        if (plugin.getAdminBypassManager().isBypassing(player)) return true;

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

        // Faction alliancée (portes / build — PAS les conteneurs : voir canAccessClaimStorage)
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

    private boolean isPasdicBannedContainer(Material mat) {
        return mat != null && pasdicBannedContainers.contains(mat);
    }

    private boolean isRaiding(Player player, Claim claim) {
        if (claim == null || claim.isPasdic()) return false;
        return plugin.getRaidManager() != null && plugin.getRaidManager().isAttackingInRaid(player, claim);
    }

    /**
     * Conteneurs dont le contenu est volable (coffres, fours, hoppers, shulkers…).
     * Exclut les ateliers sans inventaire de loot partagé (craft, enclume, loom…).
     */
    private boolean isStealableContainer(Material mat) {
        if (mat == null) return false;
        String n = mat.name();
        if (n.contains("SHULKER_BOX")) return true;
        return switch (mat) {
            case CHEST, TRAPPED_CHEST, BARREL,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 HOPPER, DISPENSER, DROPPER,
                 BREWING_STAND, ENDER_CHEST,
                 CHISELED_BOOKSHELF, DECORATED_POT,
                 CRAFTER, JUKEBOX -> true;
            default -> false;
        };
    }

    /** Ateliers autorisés en wilderness (pose) malgré storage-blocks ; casse en claim inchangée. */
    private boolean isWildernessAllowedStorage(Material mat) {
        return mat == Material.CRAFTING_TABLE
                || mat == Material.FURNACE
                || mat == Material.BLAST_FURNACE
                || mat == Material.SMOKER;
    }

    private boolean isExplosivePlaceBlock(Material mat) {
        return mat != null && explosivePlaceBlocks.contains(mat);
    }

    /** Briquet, cristaux, seaux, lodestone déjà posé, TNT à allumer, etc. */
    private boolean isRaidInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Material clicked = event.getClickedBlock() != null ? event.getClickedBlock().getType() : null;
        if (isExplosivePlaceBlock(clicked) || isIgnitableRaidBlock(clicked)) {
            return true;
        }
        Material hand = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (isExplosivePlaceBlock(hand)) return true;
        return hand == Material.FLINT_AND_STEEL || hand == Material.FIRE_CHARGE;
    }

    private static boolean isIgnitableRaidBlock(Material mat) {
        if (mat == null) return false;
        return mat == Material.TNT || mat == Material.RESPAWN_ANCHOR || mat == Material.LODESTONE;
    }

    /**
     * Attaquant joueur : coup direct, projectile, nuage de potion, pet apprivoisé.
     * TNT / cristaux / wither ne passent pas ici (raid, pas un « hit » PvP).
     */
    private static Player resolvePlayerAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player direct) {
            return direct;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud cloud
                && cloud.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof Tameable tameable
                && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
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
