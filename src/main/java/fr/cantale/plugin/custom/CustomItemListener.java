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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CustomItemListener implements Listener {

    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final FactionManager factionManager;
    private final CantalameManager cantalameManager;
    private final SerialKillerManager serialKillerManager;
    private final MultiCantoolVisualAdapter visualAdapter;

    public CustomItemListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.customItemManager = new CustomItemManager();
        this.factionManager = plugin.getFactionManager();
        this.cantalameManager = new CantalameManager(plugin);
        this.serialKillerManager = new SerialKillerManager(plugin);
        this.visualAdapter = new MultiCantoolVisualAdapter(plugin, customItemManager);
        this.visualAdapter.start();
    }

    /**
     * Lorsqu'un joueur commence à miner un block avec un Multi-Cantool,
     * applique un effet HASTE calibré pour égaliser la vitesse sur les blocks
     * non-pioche (bois, feuilles, dirt, etc.). Sans ça, la pioche de base
     * mine bois/dirt/feuilles plus lentement qu'une hache/pelle/cisailles.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!customItemManager.isCustomItem(tool)) return;

        CustomItemType type = customItemManager.getCustomItemType(tool);
        if (type == null || !type.isMultiCantool()) return;

        Material mat = event.getBlock().getType();
        String matName = mat.name();

        boolean isPickaxeBlock =
            matName.contains("STONE") || matName.contains("ORE")
            || matName.contains("COBBLE") || matName.contains("DEEPSLATE")
            || matName.contains("GRANITE") || matName.contains("DIORITE")
            || matName.contains("ANDESITE") || matName.contains("TUFF")
            || matName.contains("BASALT") || matName.contains("BLACKSTONE")
            || matName.contains("OBSIDIAN") || matName.contains("END_STONE")
            || matName.contains("NETHERRACK") || matName.contains("QUARTZ")
            || matName.contains("AMETHYST") || matName.contains("CALCITE")
            || matName.contains("COPPER") || matName.contains("ANCIENT");

        if (isPickaxeBlock) {
            player.removePotionEffect(PotionEffectType.FAST_DIGGING);
            return;
        }

        int hasteLevel;
        String typeName = type.name();
        if (matName.contains("LEAVES") || matName.contains("WOOL")
                || matName.contains("CARPET") || matName.contains("VINE")) {
            hasteLevel = typeName.contains("NETHERITE") ? 3 : typeName.contains("DIAMOND") ? 2 : 1;
        } else if (matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM")) {
            hasteLevel = typeName.contains("NETHERITE") ? 4 : typeName.contains("DIAMOND") ? 3 : 2;
        } else {
            hasteLevel = typeName.contains("NETHERITE") ? 3 : typeName.contains("DIAMOND") ? 2 : 1;
        }

        player.addPotionEffect(new PotionEffect(
            PotionEffectType.FAST_DIGGING,
            15,
            hasteLevel,
            true, false, false
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
        }
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
            // Zone de minage : bas aux pieds du joueur, vers le haut
            int playerFeetY = player.getLocation().getBlockY();
            int blockY = centerLoc.getBlockY();
            // Offset Y pour que le bas de la zone soit au niveau des pieds
            int yBottom = playerFeetY - blockY; // décalage par rapport au bloc cassé
            int yTop = yBottom + (radius - 1); // radius blocs de haut

            float normalizedYaw = (yaw % 360 + 360) % 360;

            if ((normalizedYaw >= 315 || normalizedYaw < 45) || (normalizedYaw >= 135 && normalizedYaw < 225)) {
                // Nord/Sud -> surface X,Y (1 bloc de profondeur en Z)
                for (int x = -spread; x <= spread; x++) {
                    for (int y = yBottom; y <= yTop; y++) {
                        Block targetBlock = centerLoc.clone().add(x, y, 0).getBlock();
                        if (targetBlock != center && isMineable(targetBlock.getType())) {
                            blocksToBreak.add(targetBlock);
                        }
                    }
                }
            } else {
                // Est/Ouest -> surface Z,Y (1 bloc de profondeur en X)
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

        // Casser les blocks
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block block : blocksToBreak) {
            if (block.getType().name().contains("SAND") || block.getType().name().contains("GRAVEL") 
                    || block.getType().name().contains("CONCRETE_POWDER")) {
                block.setType(org.bukkit.Material.AIR);
            } else {
                block.breakNaturally(tool);
            }
        }
    }

    private void handleCantaxe(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        // One-shot tous les logs
        if (type.name().contains("LOG") || type.name().contains("WOOD") || type.name().contains("STEM")) {
            // Casser tout l'arbre (implémentation simple pour l'instant)
            breakTree(block);
        }
    }

    private void breakTree(Block startBlock) {
        Set<Block> logs = new HashSet<>();
        findConnectedLogs(startBlock, logs);

        for (Block log : logs) {
            if (log != startBlock) {
                log.breakNaturally();
            }
        }
    }

    private void findConnectedLogs(Block block, Set<Block> logs) {
        if (logs.size() > 100) return; // Limite pour éviter lag

        if (logs.contains(block)) return;

        Material type = block.getType();
        if (!type.name().contains("LOG") && !type.name().contains("WOOD") && !type.name().contains("STEM")) {
            return;
        }

        logs.add(block);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Block neighbor = block.getLocation().clone().add(x, y, z).getBlock();
                    findConnectedLogs(neighbor, logs);
                }
            }
        }
    }

    private void handleMultiCantool(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material mat = block.getType();
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        CustomItemType type = customItemManager.getCustomItemType(tool);
        boolean is1x1 = type != null && type.getMiningRadius() == 1;

        String matName = mat.name();

        // Determine which virtual tool to use based on block type
        // Order matters: check shears FIRST (most specific), then axe, shovel, pickaxe (default)

        // 1. COMPORTEMENT CISAILLES - Feuillage, laine, tapis, fleurs
        // NOTE: GRASS_BLOCK is a SHOVEL block, NOT shears. TALL_GRASS/SHORT_GRASS are shears.
        if (matName.contains("LEAVES") || matName.contains("WOOL") || matName.contains("CARPET")
                || matName.contains("VINE") || matName.contains("FLOWER") || matName.contains("MUSHROOM")
                || matName.contains("FERN") || matName.contains("TALL_GRASS") || matName.contains("SHORT_GRASS")
                || matName.contains("LILY") || matName.contains("MOSS_BLOCK") || matName.contains("SPORE")
                || matName.contains("HANGING") || matName.contains("AZALEA") || matName.contains("BLOSSOM")) {
            ItemStack virtualShears = new ItemStack(Material.SHEARS);
            ItemMeta shearMeta = virtualShears.getItemMeta();
            if (shearMeta != null) {
                shearMeta.addEnchant(Enchantment.DIG_SPEED, 10, true);
                virtualShears.setItemMeta(shearMeta);
            }
            // Center block: cancel default drops, spawn correct shears drops
            event.setDropItems(false);
            for (ItemStack drop : block.getDrops(virtualShears)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
            if (!is1x1) {
                breakMultiZone(block, virtualShears, player, true);
            }
            return;
        }

        // 2. COMPORTEMENT HACHE - Logs et bois
        if (matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM") 
                || matName.contains("PLANK") || matName.contains("FENCE") || matName.contains("DOOR")) {
            ItemStack virtualAxe = getVirtualTool(mat, type);
            // Center block: cancel default drops, spawn correct axe drops
            event.setDropItems(false);
            for (ItemStack drop : block.getDrops(virtualAxe)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
            if (!is1x1) {
                breakTreeWithTool(block, virtualAxe);
            }
            return;
        }

        // 3. COMPORTEMENT PELLE - Terre, sable, gravier, GRASS_BLOCK
        if (matName.contains("DIRT") || matName.contains("GRASS") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("CLAY") || matName.contains("PODZOL")
                || matName.contains("MYCELIUM") || matName.contains("FARMLAND") || matName.contains("SOUL_SAND")
                || matName.contains("SOUL_SOIL") || matName.contains("ROOTED") || matName.contains("MOSS")) {
            ItemStack virtualShovel = getVirtualTool(mat, type);
            // Center block: cancel default drops, spawn correct shovel drops
            event.setDropItems(false);
            for (ItemStack drop : block.getDrops(virtualShovel)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
            if (!is1x1) {
                breakMultiZone(block, virtualShovel, player, true);
            }
            return;
        }

        // 4. COMPORTEMENT PIOCHE - Pierre, minerais, etc (défaut)
        ItemStack virtualPick = getVirtualTool(mat, type);
        // Center block: cancel default drops, spawn correct pickaxe drops
        event.setDropItems(false);
        for (ItemStack drop : block.getDrops(virtualPick)) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
        if (!is1x1) {
            breakMultiZone(block, virtualPick, player, false);
        }
    }

    private ItemStack getVirtualTool(Material blockMaterial, CustomItemType type) {
        String matName = blockMaterial.name();
        Material toolMaterial;
        
        // Choisir le bon type d'outil
        if (matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM") 
                || matName.contains("PLANK") || matName.contains("FENCE") || matName.contains("DOOR")) {
            // Hache
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_AXE;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_AXE;
            else toolMaterial = Material.IRON_AXE;
        } else if (matName.contains("DIRT") || matName.contains("GRASS") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("CLAY") || matName.contains("PODZOL")
                || matName.contains("MYCELIUM") || matName.contains("FARMLAND") || matName.contains("SOUL_SAND")
                || matName.contains("SOUL_SOIL") || matName.contains("ROOTED") || matName.contains("MOSS")) {
            // Pelle
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_SHOVEL;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_SHOVEL;
            else toolMaterial = Material.IRON_SHOVEL;
        } else {
            // Pioche (par défaut)
            if (type.name().contains("NETHERITE")) toolMaterial = Material.NETHERITE_PICKAXE;
            else if (type.name().contains("DIAMOND")) toolMaterial = Material.DIAMOND_PICKAXE;
            else toolMaterial = Material.IRON_PICKAXE;
        }
        
        ItemStack virtualTool = new ItemStack(toolMaterial);
        // Transférer les enchantements de l'outil original
        ItemStack original = null; // on n'a pas accès à l'original ici, mais les enchantements sont gérés par le type
        ItemMeta meta = virtualTool.getItemMeta();
        if (meta != null) {
            int effLevel;
            if (type.name().contains("NETHERITE")) effLevel = 10;
            else if (type.name().contains("DIAMOND")) effLevel = 7;
            else effLevel = 5;
            meta.addEnchant(Enchantment.DIG_SPEED, effLevel, true);
            meta.addEnchant(Enchantment.LOOT_BONUS_BLOCKS, 3, true);
            virtualTool.setItemMeta(meta);
        }
        return virtualTool;
    }

    private void breakTreeWithTool(Block startBlock, ItemStack tool) {
        Set<Block> logs = new HashSet<>();
        findConnectedLogs(startBlock, logs);

        for (Block log : logs) {
            if (log != startBlock) {
                log.breakNaturally(tool);
            }
        }
    }

    private void breakMultiZone(Block center, ItemStack tool, Player player, boolean isSoft) {
        Location centerLoc = center.getLocation();
        Set<Block> blocksToBreak = new HashSet<>();

        float pitch = player.getLocation().getPitch();

        if (pitch > 45 || pitch < -45) {
            // Regarde vers le haut/bas -> zone horizontale 3x3
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block target = centerLoc.clone().add(x, 0, z).getBlock();
                    if (target != center) {
                        blocksToBreak.add(target);
                    }
                }
            }
        } else {
            // Regarde horizontalement -> zone verticale 3x3
            // Bas de la zone au niveau des pieds du joueur
            int playerFeetY = player.getLocation().getBlockY();
            int blockY = centerLoc.getBlockY();
            int yBottom = playerFeetY - blockY;
            int yTop = yBottom + 2; // 3 blocs de haut

            float yaw = player.getLocation().getYaw();
            float normalizedYaw = (yaw % 360 + 360) % 360;

            if ((normalizedYaw >= 315 || normalizedYaw < 45) || (normalizedYaw >= 135 && normalizedYaw < 225)) {
                // Nord/Sud -> surface X,Y
                for (int x = -1; x <= 1; x++) {
                    for (int y = yBottom; y <= yTop; y++) {
                        Block target = centerLoc.clone().add(x, y, 0).getBlock();
                        if (target != center) {
                            blocksToBreak.add(target);
                        }
                    }
                }
            } else {
                // Est/Ouest -> surface Z,Y
                for (int z = -1; z <= 1; z++) {
                    for (int y = yBottom; y <= yTop; y++) {
                        Block target = centerLoc.clone().add(0, y, z).getBlock();
                        if (target != center) {
                            blocksToBreak.add(target);
                        }
                    }
                }
            }
        }

        // Casser les blocks
        for (Block target : blocksToBreak) {
            Material targetType = target.getType();
            if (targetType == Material.AIR || targetType == Material.BEDROCK) continue;
            
            // En mode non-soft (pioche), ne casser que les blocks minables
            if (!isSoft && !isMineable(targetType)) continue;
            
            // Optimisation : utiliser setType(AIR) pour les blocks de gravité pour éviter le crash
            if (targetType.name().contains("SAND") || targetType.name().contains("GRAVEL") 
                    || targetType.name().contains("CONCRETE_POWDER")) {
                target.setType(org.bukkit.Material.AIR);
            } else {
                target.breakNaturally(tool);
            }
        }
    }

    private void breakArea(Block center, int radiusX, int radiusY, int radiusZ, ItemStack tool) {
        Set<Block> blocksToBreak = new HashSet<>();
        Location centerLoc = center.getLocation();

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    Block block = centerLoc.clone().add(x, y, z).getBlock();
                    if (block != center && isMineable(block.getType())) {
                        blocksToBreak.add(block);
                    }
                }
            }
        }

        for (Block block : blocksToBreak) {
            // Optimisation : utiliser setType(AIR) pour les blocks de gravité pour éviter le crash
            if (block.getType().name().contains("SAND") || block.getType().name().contains("GRAVEL") 
                    || block.getType().name().contains("CONCRETE_POWDER")) {
                block.setType(org.bukkit.Material.AIR);
            } else {
                block.breakNaturally(tool);
            }
        }
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
            
            // Add 1 life
            plugin.getLifeManager().addLife(player.getUniqueId(), 1);
            
            // Consume the item
            item.setAmount(item.getAmount() - 1);
            
            player.sendMessage("§8[§6Cantale§8] §aVous avez utilisé une §eVie§a ! Vie(s) restante(s) : §e" 
                    + plugin.getLifeManager().getLives(player.getUniqueId()));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Check Chevre rank flight (unlimited flight in claims without needing Statio-Lytras)
        fr.cantale.plugin.rank.PlayerRank rank = plugin.getRankManager().getRank(player);
        boolean hasChevreFlight = rank.hasUnlimitedFlight();
        
        ItemStack chestplate = player.getInventory().getChestplate();
        boolean hasStatioLytras = chestplate != null 
            && customItemManager.isCustomItem(chestplate)
            && customItemManager.getCustomItemType(chestplate) == CustomItemType.STATIO_LYTRAS;
        
        if (!hasChevreFlight && !hasStatioLytras) {
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
                    if (hasChevreFlight) {
                        player.sendMessage("§8[§6Cantale§8] §6Vol Chèvre activé — Double-tap espace pour voler !");
                    } else {
                        player.sendMessage("§8[§6Cantale§8] §aStatio-Lytras activé — Double-tap espace pour voler !");
                    }
                }
            }
            // Mark player as in claim
            if (!wasInClaim) {
                player.setMetadata("cantale_in_claim", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            }
            
            // Réduire la durabilité doucement (1 chance sur 100 par mouvement) — only for Statio-Lytras
            if (hasStatioLytras && player.isFlying() && Math.random() < 0.01) {
                if (chestplate.getDurability() < chestplate.getType().getMaxDurability()) {
                    chestplate.setDurability((short) (chestplate.getDurability() + 1));
                    player.getInventory().setChestplate(chestplate);
                }
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
        ItemStack result = event.getResult();
        if (result == null) return;

        // Gérer l'enchantement des custom items
        if (customItemManager.isCustomItem(result)) {
            CustomItemType type = customItemManager.getCustomItemType(result);
            
            // Statio-Lytras : non-enchantable
            if (type.isStatioLytras()) {
                event.setResult(null);
                return;
            }
            
            // Pickantaxe et Cantaxe : autoriser tous les enchantements
            // (Le système vanilla gère déjà la compatibilité)
            if (type.isPickantise() || type.isCantaxe()) {
                // Ne rien bloquer, laisser passer
            }
        }
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
        
        if (!cantalameManager.isCantalame(weapon)) return;
        
        // Ajouter le kill à la Cantalame
        cantalameManager.addKill(weapon, victim);
        
        killer.sendMessage("§6[Cantalame] §7Kill ajouté ! §e" + cantalameManager.getKillCount(weapon) + "/100");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        serialKillerManager.onPlayerJoin(event.getPlayer());
    }
}
