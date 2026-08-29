package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.serialkiller.SerialKillerManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomItemListener implements Listener {

    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final FactionManager factionManager;
    private final CantalameManager cantalameManager;
    private final SerialKillerManager serialKillerManager;
    private final Set<Material> protectedStorageBlocks;
    /** Dernier chunk testé pour le vol Chèvre — claims = chunks, ignore le reste. */
    private final Map<UUID, String> lastChevreCheckChunk = new ConcurrentHashMap<>();

    public CustomItemListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.customItemManager = new CustomItemManager();
        this.factionManager = plugin.getFactionManager();
        this.cantalameManager = new CantalameManager(plugin);
        this.serialKillerManager = new SerialKillerManager(plugin);
        this.protectedStorageBlocks = plugin.getConfigManager().getClaimProtectedStorageBlocks();

        // Adaptateur visuel : faux outil côté client uniquement.
        // Sûr tant que l'instaBreak serveur reste actif sur tous les blocs.
        if (plugin.getConfigManager().isMultiCantoolVisualAdapterEnabled()) {
            MultiCantoolVisualAdapter visualAdapter = new MultiCantoolVisualAdapter(plugin, customItemManager);
            visualAdapter.start();
        }
    }

    /**
     * Multi-Cantool : insta-break sur tous les blocs minables (sauf bedrock/air).
     * Le BlockBreakEvent vérifie ensuite les protections de claim.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!customItemManager.isCustomItem(tool)) return;

        CustomItemType type = customItemManager.getCustomItemType(tool);
        if (type == null || !type.isMultiCantool()) return;

        Block block = event.getBlock();
        Material mat = block.getType();
        if (mat == Material.AIR || mat == Material.BEDROCK) return;

        // Protection spawn : pas d'insta-break dans une zone protégée
        if (!plugin.getSpawnProtection().canBuild(block.getLocation())) return;

        event.setInstaBreak(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!customItemManager.isCustomItem(tool)) return;

        CustomItemType type = customItemManager.getCustomItemType(tool);
        if (type == null) return;

        if (type.isPickantise()) {
            handlePickantise(event, type);
        } else if (type.isCantaxe()) {
            handleCantaxe(event);
        } else if (type.isMultiCantool()) {
            handleMultiCantool(event);
        } else {
            return; // Pas un outil à utilisations (Cantalame, Vie, armure…) : rien à décompter.
        }

        // Point unique de consommation : 1 utilisation par COUP (par BlockBreakEvent),
        // quelle que soit la zone minée. La durabilité vanilla n'est plus touchée
        // (voir onItemDamage) : le compteur d'utilisations est la seule jauge.
        consumeToolUse(player, tool, type);
    }

    /**
     * Décompte 1 utilisation et gère la casse à 0.
     * Sémantique du dernier coup : le coup qui amène le compteur à 0 MINE le bloc
     * (et toute la zone associée, déjà cassée ci-dessus), puis l'outil se brise —
     * plus satisfaisant qu'une casse « avant » le coup. L'event n'est donc jamais
     * annulé pour cette raison.
     */
    private void consumeToolUse(Player player, ItemStack tool, CustomItemType type) {
        int left = customItemManager.consumeOneUse(tool, type);
        if (left > 0) return;

        // Dernier coup consommé : l'outil se brise proprement (son + particules + message).
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        player.spawnParticle(org.bukkit.Particle.ITEM,
                player.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.05,
                new ItemStack(type.getMaterial()));
        player.sendMessage("§8[§6Cantale§8] §cTon " + type.getDisplayName()
                + " §cs'est épuisé après son dernier coup.");
    }

    /**
     * La durabilité vanilla ne sert plus de jauge pour les outils custom : on annule
     * toute perte pour que la barre reste pleine (le compteur d'utilisations est la
     * seule jauge). L'armure du Garde n'est PAS concernée (durabilité de combat vanilla).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!customItemManager.isCustomItem(item)) return;
        CustomItemType type = customItemManager.getCustomItemType(item);
        if (customItemManager.isToolWithUses(type)) {
            event.setCancelled(true);
        }
    }

    private void breakNaturallyWithDrops(Block block, ItemStack tool) {
        for (ItemStack drop : block.getDrops(tool)) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
        block.setType(Material.AIR);
    }

    /**
     * breakNaturally / setType(AIR) ne déclenchent pas BlockBreakEvent :
     * ClaimListener ne voit pas ces casses. Toute casse manuelle hors event
     * doit passer par canBreakExtraBlock / breakExtraBlock.
     */
    private boolean canBreakExtraBlock(Player player, Block block) {
        if (player.isOp() || player.hasPermission("cantale.admin")) return true;
        // Bypass admin (/f bypass) : la casse à l'aire (Pickantise/Cantaxe/Multi-Cantool) ignore les claims.
        if (plugin.getAdminBypassManager().isBypassing(player)) return true;
        if (factionManager == null) return true;

        Claim claim = factionManager.getClaimAt(
                block.getWorld().getName(), block.getChunk().getX(), block.getChunk().getZ());
        if (claim == null) return true;

        Faction claimFaction = factionManager.getFaction(claim.getFactionId());
        if (claimFaction == null) return true;

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        if (playerFaction != null && playerFaction.getId() == claimFaction.getId()) {
            return true;
        }

        // PASDIC : aucune destruction par un non-membre
        if (claim.isPasdic()) return false;

        // Vérifier les alliances
        var diplomacy = plugin.getDiplomatieManager();
        boolean isAlly = playerFaction != null
                && diplomacy != null
                && diplomacy.isAllied(playerFaction.getId(), claimFaction.getId());
        if (isAlly) return true;

        // Raid : les assaillants peuvent tout casser pendant un raid actif
        if (plugin.getRaidManager() != null && plugin.getRaidManager().isAttackingInRaid(player, claim)) {
            return true;
        }

        // Hors raid : aucune destruction par un étranger
        return false;
    }

    private boolean isCultureBlock(String matName) {
        return matName.contains("WHEAT") || matName.contains("CARROT") || matName.contains("POTATO") || matName.contains("BEETROOT")
                || matName.contains("NETHER_WART") || matName.contains("PUMPKIN") || matName.contains("MELON")
                || matName.contains("CACTUS") || matName.contains("SUGAR_CANE") || matName.contains("BAMBOO")
                || matName.contains("COCOA") || matName.contains("BERRIES") || matName.contains("SEED")
                || matName.contains("FLOWER") || matName.contains("MUSHROOM") || matName.contains("KELP")
                || matName.contains("SAPLING") || matName.contains("FERN") || matName.contains("GRASS")
                || matName.contains("VINE") || matName.contains("LEAVES");
    }

    /**
     * @return true si le bloc a effectivement été cassé (droits de claim inclus)
     */
    private boolean breakExtraBlock(Player player, Block block, ItemStack tool) {
        Material targetType = block.getType();
        if (targetType == Material.AIR || targetType == Material.BEDROCK) return false;
        if (!canBreakExtraBlock(player, block)) return false;

        String name = targetType.name();
        if (name.contains("SAND") || name.contains("GRAVEL") || name.contains("CONCRETE_POWDER")) {
            block.setType(Material.AIR);
        } else if (tool != null) {
            block.breakNaturally(tool);
        } else {
            block.breakNaturally();
        }
        return true;
    }

    private void handlePickantise(BlockBreakEvent event, CustomItemType type) {
        int radius = type.getMiningRadius();
        if (radius <= 1) return;

        Block center = event.getBlock();
        Location centerLoc = center.getLocation();
        Player player = event.getPlayer();
        Set<Block> blocksToBreak = new HashSet<>();

        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();

        int spread = (radius - 1) / 2; // 1 pour 3x3, 2 pour 5x5, 3 pour 7x7

        if (pitch > 45 || pitch < -45) {
            // Regarde vers le haut ou le bas -> surface horizontale (X,Z)
            for (int x = -spread; x <= spread; x++) {
                for (int z = -spread; z <= spread; z++) {
                    Block targetBlock = centerLoc.clone().add(x, 0, z).getBlock();
                    if (targetBlock != center && isMineable(targetBlock.getType())) {
                        blocksToBreak.add(targetBlock);
                    }
                }
            }
        } else {
            // Regarde horizontalement -> surface verticale
            int playerFeetY = player.getLocation().getBlockY();
            int blockY = centerLoc.getBlockY();
            int yBottom = playerFeetY - blockY;
            int yTop = yBottom + (radius - 1);

            float normalizedYaw = (yaw % 360 + 360) % 360;

            if ((normalizedYaw >= 315 || normalizedYaw < 45) || (normalizedYaw >= 135 && normalizedYaw < 225)) {
                for (int x = -spread; x <= spread; x++) {
                    for (int y = yBottom; y <= yTop; y++) {
                        Block targetBlock = centerLoc.clone().add(x, y, 0).getBlock();
                        if (targetBlock != center && isMineable(targetBlock.getType())) {
                            blocksToBreak.add(targetBlock);
                        }
                    }
                }
            } else {
                for (int z = -spread; z <= spread; z++) {
                    for (int y = yBottom; y <= yTop; y++) {
                        Block targetBlock = centerLoc.clone().add(0, y, z).getBlock();
                        if (targetBlock != center && isMineable(targetBlock.getType())) {
                            blocksToBreak.add(targetBlock);
                        }
                    }
                }
            }
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block block : blocksToBreak) {
            // Toute la zone est minée pour 1 seule utilisation (décomptée une fois
            // le coup terminé, dans onBlockBreak) — plus de coût par bloc.
            breakExtraBlock(player, block, tool);
        }
    }

    private void handleCantaxe(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        String name = type.name();

        if (name.contains("LOG") || name.contains("WOOD") || name.contains("STEM")) {
            breakTree(event.getPlayer(), block);
        }
    }

    /** BFS itératif : évite StackOverflow sur arbres très hauts. */
    private void breakTree(Player player, Block startBlock) {
        Set<Block> logs = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        logs.add(startBlock);
        queue.add(startBlock);

        while (!queue.isEmpty() && logs.size() < 100) {
            Block current = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block neighbor = current.getRelative(x, y, z);
                        if (logs.contains(neighbor)) continue;
                        String n = neighbor.getType().name();
                        if (!n.contains("LOG") && !n.contains("WOOD") && !n.contains("STEM")) continue;
                        logs.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block log : logs) {
            if (log.equals(startBlock)) continue;
            // L'arbre entier est abattu pour 1 seule utilisation (décomptée une fois
            // le coup terminé, dans onBlockBreak) — plus de coût par bûche.
            breakExtraBlock(player, log, tool);
        }
    }

    private void handleMultiCantool(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material mat = block.getType();
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        CustomItemType type = customItemManager.getCustomItemType(tool);
        if (type == null) return;

        String matName = mat.name();
        boolean isShears = isShearsBlock(matName);
        boolean isWood = isWoodBlock(matName);
        boolean isSoft = isSoftBlock(matName);

        if (isShears || isWood || isSoft) {
            ItemStack virtualTool = isShears ? createShearsTool(type) : getVirtualTool(mat, type);
            event.setDropItems(false);
            for (ItemStack drop : block.getDrops(virtualTool, player)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
        // Usure : 1 utilisation décomptée dans onBlockBreak après ce handler.
        // La durabilité vanilla est neutralisée (onItemDamage) — ne rien faire ici.
    }

    private boolean isShearsBlock(String matName) {
        return matName.contains("LEAVES") || matName.contains("WOOL") || matName.contains("CARPET")
                || matName.contains("VINE") || matName.contains("FLOWER") || matName.contains("MUSHROOM")
                || matName.contains("FERN") || matName.contains("TALL_GRASS") || matName.contains("SHORT_GRASS")
                || matName.contains("LILY") || matName.contains("MOSS_BLOCK") || matName.contains("SPORE")
                || matName.contains("HANGING") || matName.contains("AZALEA") || matName.contains("BLOSSOM");
    }

    private boolean isWoodBlock(String matName) {
        return matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM")
                || matName.contains("PLANK") || matName.contains("FENCE") || matName.contains("DOOR");
    }

    private boolean isSoftBlock(String matName) {
        return matName.contains("DIRT") || matName.contains("GRASS") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("CLAY") || matName.contains("PODZOL")
                || matName.contains("MYCELIUM") || matName.contains("FARMLAND") || matName.contains("SOUL_SAND")
                || matName.contains("SOUL_SOIL") || matName.contains("ROOTED") || matName.contains("MOSS");
    }

    private ItemStack createShearsTool(CustomItemType type) {
        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta shearMeta = shears.getItemMeta();
        if (shearMeta != null) {
            int effLevel = type.name().contains("NETHERITE") ? 10
                    : type.name().contains("DIAMOND") ? 7 : 5;
            shearMeta.addEnchant(Enchantment.EFFICIENCY, effLevel, true);
            shears.setItemMeta(shearMeta);
        }
        return shears;
    }

    private ItemStack getVirtualTool(Material blockMaterial, CustomItemType type) {
        String matName = blockMaterial.name();
        Material toolMaterial;
        if (isWoodBlock(matName)) {
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_AXE;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_AXE;
            else toolMaterial = Material.IRON_AXE;
        } else if (isSoftBlock(matName)) {
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_SHOVEL;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_SHOVEL;
            else toolMaterial = Material.IRON_SHOVEL;
        } else {
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_PICKAXE;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_PICKAXE;
            else toolMaterial = Material.IRON_PICKAXE;
        }

        ItemStack virtualTool = new ItemStack(toolMaterial);
        ItemMeta meta = virtualTool.getItemMeta();
        if (meta != null) {
            int effLevel;
            if (type.name().contains("NETHERITE")) effLevel = 10;
            else if (type.name().contains("DIAMOND")) effLevel = 7;
            else effLevel = 5;
            meta.addEnchant(Enchantment.EFFICIENCY, effLevel, true);
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
            virtualTool.setItemMeta(meta);
        }
        return virtualTool;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !customItemManager.isCustomItem(item)) return;
        
        CustomItemType type = customItemManager.getCustomItemType(item);
        if (type != CustomItemType.VIE) return;
        
        // Right-click (air or block) to consume the VIE item and gain 1 life
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR 
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            
            if (plugin.getLifeManager().getLives(player.getUniqueId()) >= 3 && !player.hasPermission("cantale.admin")) {
                player.sendMessage("§8[§6Cantale§8] §cVous avez déjà atteint le nombre maximum de 3 vies !");
                return;
            }
            
            // Add 1 life
            plugin.getLifeManager().addLife(player.getUniqueId(), 1);
            
            // Consume the item
            item.setAmount(item.getAmount() - 1);
            
            player.sendMessage("§8[§6Cantale§8] §aVous avez utilisé une §eVie§a ! Vie(s) restante(s) : §e" 
                    + plugin.getLifeManager().getLives(player.getUniqueId()) + "§7/§e3");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        Location from = event.getFrom();
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)
                && from.getWorld() == to.getWorld()) {
            return;
        }

        Player player = event.getPlayer();
        String chunkKey = to.getWorld().getName() + "," + (to.getBlockX() >> 4) + "," + (to.getBlockZ() >> 4);
        if (chunkKey.equals(lastChevreCheckChunk.get(player.getUniqueId()))) {
            return;
        }
        lastChevreCheckChunk.put(player.getUniqueId(), chunkKey);

        // Vol créatif réservé au grade Chèvre uniquement (pas modos/admin/owner) dans ses claims
        fr.cantale.plugin.rank.PlayerRank rank = plugin.getRankManager().getRank(player);
        boolean hasChevreFlight = rank.hasUnlimitedFlight();

        if (!hasChevreFlight) {
            // Clean up metadata if player had it
            if (player.hasMetadata("cantale_in_claim")) {
                player.removeMetadata("cantale_in_claim", plugin);
                if (player.isFlying() && !player.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
            return;
        }

        boolean inClaim = isInFactionClaim(player);
        boolean wasInClaim = player.hasMetadata("cantale_in_claim");

        if (inClaim) {
            // Activer le vol créatif dans le claim (allowFlight, pas forcer flying)
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                if (!wasInClaim) {
                    player.sendMessage("§8[§6Cantale§8] §6Vol Chèvre activé — Double-tap espace pour voler !");
                }
            }
            // Mark player as in claim
            if (!wasInClaim) {
                player.setMetadata("cantale_in_claim", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            }
        } else if (wasInClaim) {
            // Just left a claim — disable flight with grace warning
            player.removeMetadata("cantale_in_claim", plugin);
            if (player.isFlying()) {
                // Give 3 second grace period before disabling flight
                player.sendMessage("§8[§6Cantale§8] §cVous quittez le claim ! Vol désactivé dans 3 secondes.");
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isInFactionClaim(player) && player.isOnline()
                        && !player.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                }, 60L); // 3 seconds
            } else {
                player.setAllowFlight(false);
            }
        }
    }

    private boolean isInFactionClaim(Player player) {
        if (factionManager == null) return false;

        var chunk = player.getLocation().getChunk();
        var faction = factionManager.getFactionAtClaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (faction == null) return false;

        return faction.isMember(player.getUniqueId());
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        // Contrôler les ENTRÉES : un livre enchanté sur une Cantalame produit un
        // résultat netherite « nu » (sans PDC custom) que l'ancien check sur
        // getResult() laissait passer.
        if (cantalameManager.isCantalame(first) || cantalameManager.isCantalame(second)
                || customItemManager.isCustomItem(first) || customItemManager.isCustomItem(second)) {
            event.setResult(null);
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;
        if (customItemManager.isCustomItem(result) || cantalameManager.isCantalame(result)) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (cantalameManager.isCantalame(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (!cantalameManager.isCantalame(event.getItem())) return;
        event.setCancelled(true);
        event.getEnchanter().sendMessage("§8[§6Cantale§8] §cLa Cantalame ne s'enchante pas à la table — elle progresse avec tes kills.");
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;
        if (customItemManager.isCustomItem(result)) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        org.bukkit.inventory.Inventory inv = event.getInventory();
        if (inv.getType() != org.bukkit.event.inventory.InventoryType.GRINDSTONE) return;
        // Si un item custom est présent dans les entrées (slots 0 et 1), bloquer le clic sur le résultat (slot 2).
        if (event.getRawSlot() == 2) {
            if (isCustomItemAt(inv, 0) || isCustomItemAt(inv, 1)) {
                event.setCancelled(true);
                player.sendMessage("§8[§6Cantale§8] §cLes items custom ne peuvent pas être désenchantés.");
            }
        }
    }

    private boolean isCustomItemAt(org.bukkit.inventory.Inventory inv, int slot) {
        if (slot < 0 || slot >= inv.getSize()) return false;
        ItemStack item = inv.getItem(slot);
        return item != null && customItemManager.isCustomItem(item);
    }

    private boolean isMineable(Material material) {
        return material.name().contains("ORE") || material.name().contains("STONE")
                || material.name().contains("COAL") || material.name().contains("IRON")
                || material.name().contains("GOLD") || material.name().contains("DIAMOND")
                || material.name().contains("REDSTONE") || material.name().contains("LAPIS")
                || material.name().contains("EMERALD") || material.name().contains("COPPER")
                || material.name().contains("DEEPSLATE") || material.name().contains("NETHER")
                || material.name().contains("ANCIENT") || material.name().contains("SAND")
                || material.name().contains("GRAVEL") || material.name().contains("DIRT")
                || material.name().contains("GRASS") || material.name().contains("MYCELIUM")
                || material.name().contains("CLAY") || material.name().contains("TERRACOTTA")
                || material.name().contains("GRANITE") || material.name().contains("DIORITE")
                || material.name().contains("ANDESITE") || material.name().contains("TUFF")
                || material.name().contains("COBBLESTONE") || material.name().contains("OBSIDIAN")
                || material.name().contains("CRYSTAL") || material.name().contains("PODZOL")
                || material.name().contains("ROOTED");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer == null) return;
        
        // Incrémenter les kills du killer dans la base de données
        plugin.getPlayerDAO().incrementKills(killer.getUniqueId());
        
        // Vérifier si le killer devient Serial Killer
        serialKillerManager.checkKillStreak(killer.getUniqueId(), killer.getName());
        
        // Gérer la mort d'un Serial Killer
        serialKillerManager.onPlayerDeath(victim.getUniqueId(), victim.getName(), killer.getUniqueId());
        
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        
        // Wanted : récompenser le tueur si la victime est recherchée
        var wantedMgr = plugin.getWantedManager();
        if (wantedMgr != null && wantedMgr.isWanted(victim.getUniqueId())) {
            wantedMgr.completeWanted(killer, victim);
        }
        
        if (!cantalameManager.isCantalame(weapon)) return;
        
        // Ajouter le kill à la Cantalame
        cantalameManager.addKill(weapon, victim);
        
        // Dropper la tête de la victime à l'endroit de sa mort
        dropPlayerHead(victim, victim.getLocation());
        killer.sendMessage("§6[Cantalame] §7Kill ajouté ! §e" + cantalameManager.getKillCount(weapon) + "/100");
    }

    private void dropPlayerHead(Player victim, Location location) {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName("§cTête de §e" + victim.getName());
            head.setItemMeta(meta);
        }
        location.getWorld().dropItemNaturally(location, head);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        serialKillerManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastChevreCheckChunk.remove(event.getPlayer().getUniqueId());
    }
}
