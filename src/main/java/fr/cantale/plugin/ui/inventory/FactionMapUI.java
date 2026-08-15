package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.diplomacy.DiplomacyManager;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import fr.cantale.plugin.factions.permissions.FactionPermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carte des claims en inventaire : chaque case = 1 chunk, centrée sur le joueur.
 * Grille 9×5 (lignes 0–4), barre d'actions en bas.
 * <ul>
 *   <li>Clic gauche sur gris = claim (mêmes règles que /f claim)</li>
 *   <li>Clic droit sur claim de sa fac = unclaim (mêmes règles que /f unclaim)</li>
 * </ul>
 */
public class FactionMapUI extends BaseUI {

    /** Demi-largeur (cols 0–8 → dx −4…+4). */
    private static final int RADIUS_X = 4;
    /** Demi-hauteur (rows 0–4 → dz −2…+2). */
    private static final int RADIUS_Z = 2;
    private static final int MAP_ROWS = RADIUS_Z * 2 + 1; // 5

    private final FactionManager factionManager;
    private final FactionPermissionManager permissionManager;
    private final Map<Integer, int[]> slotToChunk = new HashMap<>();

    private int centerChunkX;
    private int centerChunkZ;
    private String worldName;

    public FactionMapUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Carte des claims", 6);
        this.factionManager = plugin.getFactionManager();
        this.permissionManager = new FactionPermissionManager();
    }

    @Override
    protected void init() {
        inventory.clear();
        slotToChunk.clear();

        World world = player.getWorld();
        worldName = world.getName();
        centerChunkX = player.getLocation().getChunk().getX();
        centerChunkZ = player.getLocation().getChunk().getZ();

        Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
        DiplomacyManager diplomacy = plugin.getDiplomatieManager();

        for (int row = 0; row < MAP_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int dx = col - RADIUS_X;
                int dz = row - RADIUS_Z;
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                int slot = row * 9 + col;
                boolean here = dx == 0 && dz == 0;
                slotToChunk.put(slot, new int[]{chunkX, chunkZ});
                inventory.setItem(slot, buildChunkItem(world, chunkX, chunkZ, here, playerFaction, diplomacy));
            }
        }

        // Barre du bas
        fillBottomPadding();
        placeInfoItem(playerFaction);
        addNavigationBar(true, true);
        setItem(size - 8, Material.BOOK, "§e§lLégende",
                "§aVert §7= ta faction",
                "§aLime §7= ta position",
                "§7Gris §7= claimable (clic gauche)",
                "§cRouge §7= interdit / ennemi",
                "§bBleu §7= allié",
                "§6Orange §7= autre faction",
                "§5Violet §7= PASDIC",
                "",
                "§7Nord = haut de l'inventaire",
                "§eClic droit §7sur ton claim = unclaim");
    }

    private void fillBottomPadding() {
        for (int i = 45; i < 54; i++) {
            if (inventory.getItem(i) == null) {
                setItem(i, Material.BLACK_STAINED_GLASS_PANE, " ");
            }
        }
    }

    private void placeInfoItem(Faction faction) {
        if (faction == null) {
            setItem(47, Material.MAP, "§e§lCarte",
                    "§7Chunk : §e" + centerChunkX + "§7, §e" + centerChunkZ,
                    "§7Monde : §e" + worldName,
                    "",
                    "§cPas de faction — lecture seule.");
            return;
        }
        setItem(47, Material.MAP, "§6§l" + faction.getName(),
                "§7Tag : §e[" + faction.getTag() + "]",
                "§7Pouvoir : §e" + faction.getPower(),
                "§7Claims : §e" + faction.getClaimCount() + "§7/§e" + faction.getMaxClaims(),
                "§7Chunk : §e" + centerChunkX + "§7, §e" + centerChunkZ,
                "§7Monde : §e" + worldName,
                "",
                "§aClic gauche §7gris = claim",
                "§cClic droit §7vert/lime (ta fac) = unclaim");
    }

    private ItemStack buildChunkItem(World world, int chunkX, int chunkZ, boolean here,
                                    Faction playerFaction, DiplomacyManager diplomacy) {
        Claim claim = factionManager.getClaimAt(worldName, chunkX, chunkZ);
        Faction claimFaction = null;
        if (claim != null) {
            claimFaction = factionManager.getFaction(claim.getFactionId());
            if (claimFaction != null && claimFaction.isSecret()
                    && (playerFaction == null || playerFaction.getId() != claimFaction.getId())) {
                claim = null;
                claimFaction = null;
            }
        }

        Material mat;
        String title;
        List<String> lore = new ArrayList<>();
        lore.add("§7Chunk §e" + chunkX + "§7, §e" + chunkZ);
        lore.add("§8Blocs ≈ " + (chunkX * 16) + ", " + (chunkZ * 16));
        if (here) {
            lore.add("§e★ Vous êtes ici");
        }

        ChunkVisual visual = classify(world, chunkX, chunkZ, claim, claimFaction, playerFaction, diplomacy, here);
        mat = visual.material;
        title = visual.title;
        lore.addAll(visual.lore);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(colored);
            if (here) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ChunkVisual classify(World world, int chunkX, int chunkZ,
                                 Claim claim, Faction claimFaction,
                                 Faction playerFaction, DiplomacyManager diplomacy,
                                 boolean here) {
        // Claim occupé
        if (claim != null && claimFaction != null) {
            if (claim.isPasdic()) {
                return new ChunkVisual(
                        Material.PURPLE_STAINED_GLASS_PANE,
                        "§5PASDIC §8· §e" + claimFaction.getName(),
                        List.of(
                                "§7Tag : §e[" + claimFaction.getTag() + "]",
                                "§cNon claimable"
                        ));
            }
            if (playerFaction != null && claimFaction.getId() == playerFaction.getId()) {
                Material mat = here ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
                return new ChunkVisual(mat, "§aTon claim §8· §e" + claimFaction.getName(),
                        List.of(
                                "§7Tag : §e[" + claimFaction.getTag() + "]",
                                "",
                                "§cClic droit §7: unclaim"
                        ));
            }
            if (playerFaction != null && diplomacy != null
                    && diplomacy.isAllied(playerFaction.getId(), claimFaction.getId())) {
                return new ChunkVisual(
                        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        "§bAllié §8· §e" + claimFaction.getName(),
                        List.of("§7Tag : §e[" + claimFaction.getTag() + "]", "§cNon claimable"));
            }
            if (playerFaction != null && diplomacy != null
                    && diplomacy.isAtWar(playerFaction.getId(), claimFaction.getId())) {
                return new ChunkVisual(
                        Material.RED_STAINED_GLASS_PANE,
                        "§cEnnemi §8· §e" + claimFaction.getName(),
                        List.of("§7Tag : §e[" + claimFaction.getTag() + "]", "§cNon claimable"));
            }
            return new ChunkVisual(
                    Material.ORANGE_STAINED_GLASS_PANE,
                    "§6Autre §8· §e" + claimFaction.getName(),
                    List.of("§7Tag : §e[" + claimFaction.getTag() + "]", "§cNon claimable"));
        }

        // Wilderness
        if (playerFaction == null) {
            Material mat = here ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            return new ChunkVisual(mat, here ? "§aWilderness §8· §etoi" : "§7Wilderness",
                    List.of("§cRejoins une faction pour claimer."));
        }

        Location center = chunkCenter(world, chunkX, chunkZ);
        List<String> blockReasons = new ArrayList<>();
        if (!plugin.getSpawnProtection().canClaim(center)) {
            blockReasons.add("§cZone protégée (spawn/warp)");
        }
        if (factionManager.isTooCloseToOtherFaction(worldName, chunkX, chunkZ, playerFaction.getId())) {
            blockReasons.add("§cTrop proche d'une autre fac (§e"
                    + FactionManager.MIN_ENEMY_CLAIM_DISTANCE + " §cchunks min)");
        }
        if (!factionManager.isAdjacentToOwnClaimOrFirst(worldName, chunkX, chunkZ, playerFaction.getId())) {
            blockReasons.add("§cPas adjacent à un claim (N/S/E/O)");
        }
        if (playerFaction.getClaimCount() >= playerFaction.getMaxClaims()) {
            blockReasons.add("§cQuota atteint (§e" + playerFaction.getClaimCount()
                    + "§c/§e" + playerFaction.getMaxClaims() + "§c)");
        }

        boolean canAdd = canPlayerClaim(playerFaction);
        if (!blockReasons.isEmpty() || !canAdd) {
            if (!canAdd && blockReasons.isEmpty()) {
                blockReasons.add("§cPermission claim manquante");
            }
            Material mat = here ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            List<String> lore = new ArrayList<>();
            lore.add("§7Wilderness — §cnon claimable");
            lore.addAll(blockReasons);
            return new ChunkVisual(mat, here ? "§cInterdit §8· §etoi" : "§cNon claimable", lore);
        }

        Material mat = here ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        return new ChunkVisual(mat, here ? "§aClaimable §8· §etoi" : "§7Claimable",
                List.of(
                        "§7Wilderness libre",
                        "§7Claims : §e" + playerFaction.getClaimCount()
                                + "§7/§e" + playerFaction.getMaxClaims(),
                        "",
                        "§aClic gauche §7: claim"
                ));
    }

    private boolean canPlayerClaim(Faction faction) {
        if (faction.isLeader(player.getUniqueId())) return true;
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        return permissionManager.hasPermission(faction.getId(), rank, FactionPermission.ADD_CLAIMS);
    }

    private boolean canPlayerUnclaim(Faction faction) {
        if (faction.isLeader(player.getUniqueId())) return true;
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        return permissionManager.hasPermission(faction.getId(), rank, FactionPermission.REMOVE_CLAIMS);
    }

    private Location chunkCenter(World world, int chunkX, int chunkZ) {
        return new Location(world, chunkX * 16 + 8.0, player.getLocation().getY(), chunkZ * 16 + 8.0);
    }

    @Override
    protected void handleClick(int slot) {
        // Remplacé par onClick (gauche / droit).
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == top || event.isShiftClick()) {
            event.setCancelled(true);
        }
        if (clicked != top) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= size) return;

        if (slot == size - 9 && inventory.getItem(slot) != null
                && inventory.getItem(slot).getType() == Material.ARROW) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == size - 5 && inventory.getItem(slot) != null
                && inventory.getItem(slot).getType() == Material.BARRIER) {
            close();
            return;
        }
        if (slot == size - 1 && inventory.getItem(slot) != null
                && inventory.getItem(slot).getType() == Material.COMPASS) {
            // Recentre sur la position actuelle du joueur
            init();
            return;
        }
        // Légende / info : pas d'action
        if (slot == size - 8 || slot == 47) return;

        int[] coords = slotToChunk.get(slot);
        if (coords == null) return;

        // Shift+clic gauche sur un claim de sa faction : conversion PASDIC
        if (event.isShiftClick() && event.isLeftClick()) {
            Claim clickedClaim = factionManager.getClaimAt(worldName, coords[0], coords[1]);
            Faction playerFaction = factionManager.getPlayerFaction(player.getUniqueId());
            if (clickedClaim != null && playerFaction != null && clickedClaim.getFactionId() == playerFaction.getId()) {
                player.performCommand("f pasdic");
                close();
                return;
            }
        }

        if (event.isRightClick()) {
            tryUnclaim(coords[0], coords[1]);
        } else if (event.isLeftClick()) {
            tryClaim(coords[0], coords[1]);
        }
    }

    private void tryClaim(int chunkX, int chunkZ) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'êtes pas dans une faction.");
            return;
        }
        if (!canPlayerClaim(faction)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas la permission de claim.");
            return;
        }

        Claim existing = factionManager.getClaimAt(worldName, chunkX, chunkZ);
        if (existing != null) {
            // Y compris claims secrets masqués sur la carte : impossible de claimer par-dessus
            player.sendMessage("§8[§6Cantale§8] §cCe chunk est déjà claimé.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§8[§6Cantale§8] §cMonde introuvable.");
            return;
        }
        Location center = chunkCenter(world, chunkX, chunkZ);
        if (!plugin.getSpawnProtection().canClaim(center)) {
            player.sendMessage("§8[§6Cantale§8] §cCette zone est protégée et ne peut pas être claimée.");
            return;
        }
        if (faction.getClaimCount() >= faction.getMaxClaims()) {
            player.sendMessage("§8[§6Cantale§8] §cQuota de claims atteint ("
                    + faction.getMaxClaims() + "). Augmentez le pouvoir de votre faction.");
            return;
        }
        if (factionManager.isTooCloseToOtherFaction(worldName, chunkX, chunkZ, faction.getId())) {
            player.sendMessage("§8[§6Cantale§8] §cTrop proche des claims d'une autre faction ("
                    + FactionManager.MIN_ENEMY_CLAIM_DISTANCE + " chunks minimum).");
            return;
        }
        if (!factionManager.isAdjacentToOwnClaimOrFirst(worldName, chunkX, chunkZ, faction.getId())) {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de claim ici : le chunk doit toucher un claim "
                    + "existant de votre faction (côtés N/S/E/O).");
            return;
        }

        if (factionManager.addClaim(faction.getId(), worldName, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Cantale§8] §aChunk §e(" + chunkX + ", " + chunkZ + ") §aclaimé !");
            init();
        } else {
            player.sendMessage("§8[§6Cantale§8] §cImpossible de claimer ce chunk.");
        }
    }

    private void tryUnclaim(int chunkX, int chunkZ) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'êtes pas dans une faction.");
            return;
        }
        if (!canPlayerUnclaim(faction)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas la permission d'unclaim.");
            return;
        }

        Claim claim = factionManager.getClaimAt(worldName, chunkX, chunkZ);
        if (claim == null || claim.getFactionId() != faction.getId()) {
            // Clic droit ailleurs : silencieux (évite le spam)
            return;
        }

        if (factionManager.removeClaim(worldName, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Cantale§8] §aChunk §e(" + chunkX + ", " + chunkZ + ") §aunclaimé !");
            init();
        } else {
            player.sendMessage("§8[§6Cantale§8] §cErreur lors de l'unclaim.");
        }
    }

    private record ChunkVisual(Material material, String title, List<String> lore) {}
}
