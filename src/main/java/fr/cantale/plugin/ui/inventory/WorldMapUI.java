package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Claim;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionManager;
import fr.cantale.plugin.factions.FactionRank;
import fr.cantale.plugin.factions.permissions.FactionPermission;
import fr.cantale.plugin.factions.permissions.FactionPermissionManager;
import fr.cantale.plugin.ui.map.MapSession;
import fr.cantale.plugin.ui.map.MapUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Carte du monde interactive : grille 7×4 de tuiles {@link Material#FILLED_MAP}
 * (terrain réel + claims colorés + warps/homes), barre d'actions, téléportation au clic.
 *
 * <ul>
 *   <li>Clic gauche sur une tuile = téléportation au centre (cooldown RTP 5 min + warmup)</li>
 *   <li>Clic droit = claim (wilderness) / menu PASDIC (son claim) / infos (autre faction)</li>
 *   <li>Shift + clic droit sur son claim = unclaim</li>
 *   <li>Clic molette = recentrer la carte sur la tuile</li>
 * </ul>
 */
public class WorldMapUI extends BaseUI {

    private static final int SLOT_ZOOM_OUT = 36;
    private static final int SLOT_ZOOM_IN = 37;
    private static final int SLOT_MY_POS = 38;
    private static final int SLOT_INFO = 39;
    private static final int SLOT_TOGGLE_CLAIMS = 40;
    private static final int SLOT_TOGGLE_MARKERS = 41;
    private static final int SLOT_TOGGLE_TERRAIN = 42;
    private static final int SLOT_LEGEND = 43;
    private static final int SLOT_MINIMAP = 44;

    private final FactionManager factionManager;
    private final FactionPermissionManager permissionManager;
    private final MapSession session;
    private final int[] tileSlots = new int[MapUI.TILE_COUNT];

    public WorldMapUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Carte du monde", 6);
        this.factionManager = plugin.getFactionManager();
        this.permissionManager = new FactionPermissionManager();
        this.session = plugin.getMapUI().openSession(player);
    }

    @Override
    public void open() {
        // Survit à un « Retour » depuis un dialog ouvert par-dessus (ex. confirmation PASDIC)
        plugin.getMapUI().ensureSession(player, session);
        super.open();
        // Le GUI n'est pas l'inventaire du joueur : pousser les tuiles manuellement
        for (int i = 0; i < MapUI.TILE_COUNT; i++) {
            player.sendMap(plugin.getMapUI().tileView(i));
        }
    }

    @Override
    protected void init() {
        inventory.clear();
        session.markAllDirty();
        MapUI mapUI = plugin.getMapUI();

        for (int row = 0; row < MapUI.GRID_ROWS; row++) {
            for (int col = 0; col < MapUI.GRID_COLS; col++) {
                int tile = row * MapUI.GRID_COLS + col;
                int slot = row * 9 + 1 + col;
                tileSlots[tile] = slot;

                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                if (meta != null) {
                    meta.setMapView(mapUI.tileView(tile));
                    int ox = session.tileOriginX(col, MapUI.GRID_COLS);
                    int oz = session.tileOriginZ(row, MapUI.GRID_ROWS);
                    meta.setDisplayName("§eZone §6" + ox + "§7, §6" + oz);
                    meta.setLore(List.of(
                            "§7Tuile " + session.tileSpan() + "×" + session.tileSpan() + " blocs",
                            "§8Clic gauche : s'y téléporter",
                            "§8Clic droit : claim / PASDIC / infos",
                            "§8Clic molette : recentrer ici"));
                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
        }

        // Bordure colonnes 0 et 8 des rangées de carte
        for (int row = 0; row < MapUI.GRID_ROWS; row++) {
            setItem(row * 9, Material.BLACK_STAINED_GLASS_PANE, " ");
            setItem(row * 9 + 8, Material.BLACK_STAINED_GLASS_PANE, " ");
        }

        placeControls();
        addNavigationBar(true, true);
    }

    private void placeControls() {
        int zoom = session.zoom();
        setItem(SLOT_ZOOM_OUT, Material.ORANGE_DYE, "§e§lZoom −",
                "§7Actuel : §e" + zoom + " §7bloc(s)/px",
                "§7Max : §e16 §7(vue large)");
        setItem(SLOT_ZOOM_IN, Material.LIME_DYE, "§a§lZoom +",
                "§7Actuel : §e" + zoom + " §7bloc(s)/px",
                "§7Min : §e1 §7(précision chunk)");
        setItem(SLOT_MY_POS, Material.RECOVERY_COMPASS, "§b§lMa position",
                "§7Recentrer la carte sur toi");
        updateInfoItem(null, null);
        setItem(SLOT_TOGGLE_CLAIMS, session.showClaims ? Material.OAK_FENCE : Material.BARRIER,
                "§6Claims : " + (session.showClaims ? "§aON" : "§cOFF"),
                "§7Affiche les territoires colorés par relation");
        setItem(SLOT_TOGGLE_MARKERS, session.showMarkers ? Material.TORCH : Material.BARRIER,
                "§6Marqueurs : " + (session.showMarkers ? "§aON" : "§cOFF"),
                "§7Warps (cyan/or), tes homes (vert), f spawn (orange)");
        setItem(SLOT_TOGGLE_TERRAIN, session.showTerrain ? Material.GRASS_BLOCK : Material.BARRIER,
                "§6Terrain : " + (session.showTerrain ? "§aON" : "§cOFF"),
                "§7Relief réel (avec cache) ou grille simple");
        setItem(SLOT_LEGEND, Material.BOOK, "§e§lLégende",
                "§aVert §7= ta faction §8│ §5Violet §7= PASDIC",
                "§bBleu §7= allié §8│ §cRouge §7= en guerre",
                "§6Orange §7= autre faction",
                "§8── Marqueurs ──",
                "§b◆ §7warp §8│ §6◆ §7warp event",
                "§a■ §7ton home §8│ §c■ §7f spawn §8│ §f➤ §7toi",
                "§8── Actions ──",
                "§7Clic G §7: TP §8│ §7Clic D §7: claim/PASDIC",
                "§7Shift+D §7: unclaim §8│ §7Molette §7: recentrer");
        setItem(SLOT_MINIMAP, Material.FILLED_MAP, "§d§lMinimap",
                "§7Reçois une carte live à tenir en main",
                "§7(terrain + claims + ta position)");
    }

    private void updateInfoItem(Integer chunkX, Integer chunkZ) {
        if (chunkX == null) {
            setItem(SLOT_INFO, Material.PAPER, "§f§lInfos",
                    "§7Monde : §e" + session.world,
                    "§7Centre : §e" + session.centerX + "§7, §e" + session.centerZ,
                    "§7Zoom : §e" + session.zoom() + " §7bloc(s)/px",
                    "",
                    "§7Clique une tuile pour les détails du chunk.");
            return;
        }
        Claim claim = factionManager.getClaimAt(session.world, chunkX, chunkZ);
        Faction own = factionManager.getPlayerFaction(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("§7Chunk : §e" + chunkX + "§7, §e" + chunkZ);
        lore.add("§7Blocs ≈ §e" + (chunkX * 16) + "§7, §e" + (chunkZ * 16));
        if (claim != null) {
            Faction owner = factionManager.getFaction(claim.getFactionId());
            if (owner != null && owner.isSecret() && (own == null || own.getId() != owner.getId())) {
                lore.add("§7Wilderness (en apparence)");
            } else if (owner != null) {
                lore.add((claim.isPasdic() ? "§5PASDIC" : "§6Faction") + " §8: §e" + owner.getName()
                        + " §8[" + owner.getTag() + "]");
                if (own != null && owner.getId() == own.getId()) {
                    lore.add("§aTon territoire");
                    lore.add(claim.isPasdic() ? "§7Clic droit : retirer le PASDIC"
                            : "§7Clic droit : convertir en PASDIC");
                    lore.add("§7Shift+clic droit : unclaim");
                }
            }
        } else {
            lore.add("§7Wilderness");
            lore.add("§aClic droit : claim §8(si perm + règles)");
        }
        lore.add("§eClic gauche : se téléporter");
        setItem(SLOT_INFO, Material.PAPER, "§f§lChunk " + chunkX + ", " + chunkZ,
                lore.toArray(new String[0]));
    }

    @Override
    protected void handleClick(int slot) {
        // Remplacé par onClick (types de clic nécessaires — pattern FactionMapUI).
    }

    /**
     * Remplace BaseUI.onClick (même signature = override, un seul handler enregistré) :
     * il faut donc y répliquer l'annulation + la barre de navigation (pattern FactionMapUI).
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getWhoClicked().equals(player)) return;

        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        org.bukkit.inventory.Inventory clicked = event.getClickedInventory();

        if (clicked == top || event.isShiftClick()) {
            event.setCancelled(true);
        }
        if (clicked != top) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= size) return;

        ItemStack navItem = inventory.getItem(slot);
        Material navType = navItem != null ? navItem.getType() : null;
        if (slot == size - 9 && navType == Material.ARROW) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == size - 5 && navType == Material.BARRIER) {
            close();
            return;
        }
        if (slot == size - 1 && navType == Material.COMPASS) {
            init();
            return;
        }

        MapUI mapUI = plugin.getMapUI();
        switch (slot) {
            case SLOT_ZOOM_OUT -> { session.zoomOut(); refresh(); }
            case SLOT_ZOOM_IN -> { session.zoomIn(); refresh(); }
            case SLOT_MY_POS -> {
                session.centerX = player.getLocation().getBlockX();
                session.centerZ = player.getLocation().getBlockZ();
                refresh();
            }
            case SLOT_TOGGLE_CLAIMS -> { session.showClaims = !session.showClaims; refresh(); }
            case SLOT_TOGGLE_MARKERS -> { session.showMarkers = !session.showMarkers; refresh(); }
            case SLOT_TOGGLE_TERRAIN -> { session.showTerrain = !session.showTerrain; refresh(); }
            case SLOT_MINIMAP -> mapUI.giveMinimap(player);
            case SLOT_LEGEND, SLOT_INFO -> { /* lecture seule */ }
            default -> handleTileClick(slot, event);
        }
    }

    private void handleTileClick(int slot, InventoryClickEvent event) {
        int tile = tileIndexOf(slot);
        if (tile < 0) return;
        int col = tile % MapUI.GRID_COLS;
        int row = tile / MapUI.GRID_COLS;
        int centerBlockX = session.tileOriginX(col, MapUI.GRID_COLS) + session.tileSpan() / 2;
        int centerBlockZ = session.tileOriginZ(row, MapUI.GRID_ROWS) + session.tileSpan() / 2;
        int chunkX = centerBlockX >> 4;
        int chunkZ = centerBlockZ >> 4;

        updateInfoItem(chunkX, chunkZ);

        if (event.getClick() == ClickType.MIDDLE) {
            session.centerX = centerBlockX;
            session.centerZ = centerBlockZ;
            refresh();
            return;
        }
        if (event.isLeftClick()) {
            tryTeleport(centerBlockX, centerBlockZ);
            return;
        }
        if (event.isRightClick() && event.isShiftClick()) {
            tryUnclaim(chunkX, chunkZ);
            return;
        }
        if (event.isRightClick()) {
            handleRightClick(chunkX, chunkZ);
        }
    }

    private int tileIndexOf(int slot) {
        for (int i = 0; i < tileSlots.length; i++) {
            if (tileSlots[i] == slot) return i;
        }
        return -1;
    }

    private void refresh() {
        plugin.getMapUI().invalidate(session);
        init();
        for (int i = 0; i < MapUI.TILE_COUNT; i++) {
            player.sendMap(plugin.getMapUI().tileView(i));
        }
    }

    // ─── Actions ────────────────────────────────────────────────────────

    private void tryTeleport(int blockX, int blockZ) {
        if (!player.getWorld().getName().equals(session.world)) {
            player.sendMessage("§8[§6Carte§8] §cLa carte montre le monde §e" + session.world
                    + " §c— tu n'y es plus.");
            return;
        }
        World world = player.getWorld();
        Location dest = new Location(world, blockX + 0.5, 64, blockZ + 0.5);
        boolean started = plugin.getTeleportDelayManager().requestTeleport(player, dest, true, true, () -> {
            if (!player.isOnline()) return false;
            // Chunk préchargé par le warmup — getHighestBlockYAt est sûr ici
            int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
            player.teleport(new Location(world, blockX + 0.5, y, blockZ + 0.5,
                    player.getLocation().getYaw(), player.getLocation().getPitch()));
            player.sendMessage("§8[§6Carte§8] §aTéléporté en §e" + blockX + "§7, §e" + blockZ);
            return true;
        });
        if (started) {
            close();
        }
    }

    private void handleRightClick(int chunkX, int chunkZ) {
        Claim claim = factionManager.getClaimAt(session.world, chunkX, chunkZ);
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (claim == null) {
            tryClaim(chunkX, chunkZ);
            return;
        }
        Faction owner = factionManager.getFaction(claim.getFactionId());
        if (owner == null) return;
        if (faction == null || owner.getId() != faction.getId()) {
            if (owner.isSecret()) return; // reste invisible
            player.sendMessage("§8[§6Carte§8] §7" + (claim.isPasdic() ? "§5PASDIC §7· " : "")
                    + "§e" + owner.getName() + " §8[" + owner.getTag() + "]");
            return;
        }
        openPasdicMenu(claim);
    }

    /** Règles identiques à {@link FactionMapUI#tryClaim} (cf. /f claim). */
    private void tryClaim(int chunkX, int chunkZ) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§8[§6Carte§8] §cVous n'êtes pas dans une faction.");
            return;
        }
        if (!canPlayerDo(faction, FactionPermission.ADD_CLAIMS)) {
            player.sendMessage("§8[§6Carte§8] §cVous n'avez pas la permission de claim.");
            return;
        }
        if (factionManager.getClaimAt(session.world, chunkX, chunkZ) != null) {
            player.sendMessage("§8[§6Carte§8] §cCe chunk est déjà claimé.");
            return;
        }
        World world = Bukkit.getWorld(session.world);
        if (world == null) return;
        Location center = new Location(world, chunkX * 16 + 8.0, 64, chunkZ * 16 + 8.0);
        if (!plugin.getSpawnProtection().canClaim(center)) {
            player.sendMessage("§8[§6Carte§8] §cCette zone est protégée et ne peut pas être claimée.");
            return;
        }
        if (faction.getClaimCount() >= faction.getMaxClaims()) {
            player.sendMessage("§8[§6Carte§8] §cQuota de claims atteint (§e" + faction.getMaxClaims() + "§c).");
            return;
        }
        if (factionManager.isTooCloseToOtherFaction(session.world, chunkX, chunkZ, faction.getId())) {
            player.sendMessage("§8[§6Carte§8] §cTrop proche d'une autre faction (§e"
                    + FactionManager.MIN_ENEMY_CLAIM_DISTANCE + " §cchunks min).");
            return;
        }
        if (!fr.cantale.plugin.world.WorldRules.claimsAllowed(session.world)) {
            player.sendMessage("§8[§6Carte§8] §cLes claims sont interdits dans ce monde.");
            return;
        }
        if (!factionManager.isAdjacentToOwnClaimOrFirst(session.world, chunkX, chunkZ, faction.getId())) {
            player.sendMessage("§8[§6Carte§8] §cLe chunk doit toucher un claim de ta faction (N/S/E/O).");
            return;
        }
        if (factionManager.addClaim(faction.getId(), session.world, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Carte§8] §aChunk §e(" + chunkX + ", " + chunkZ + ") §aclaimé !");
            plugin.getMapUI().invalidateChunk(session, chunkX, chunkZ);
        } else {
            player.sendMessage("§8[§6Carte§8] §cImpossible de claimer ce chunk.");
        }
    }

    /** Règles identiques à {@link FactionMapUI#tryUnclaim} (cf. /f unclaim). */
    private void tryUnclaim(int chunkX, int chunkZ) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) return;
        if (!canPlayerDo(faction, FactionPermission.REMOVE_CLAIMS)) {
            player.sendMessage("§8[§6Carte§8] §cVous n'avez pas la permission d'unclaim.");
            return;
        }
        Claim claim = factionManager.getClaimAt(session.world, chunkX, chunkZ);
        if (claim == null || claim.getFactionId() != faction.getId()) return;
        if (factionManager.removeClaim(session.world, chunkX, chunkZ)) {
            player.sendMessage("§8[§6Carte§8] §aChunk §e(" + chunkX + ", " + chunkZ + ") §aunclaimé !");
            plugin.getMapUI().invalidateChunk(session, chunkX, chunkZ);
        } else {
            player.sendMessage("§8[§6Carte§8] §cErreur lors de l'unclaim.");
        }
    }

    /** Conversion/retrait PASDIC à distance — mêmes règles que /f pasdic (Officier+, taxe banque). */
    private void openPasdicMenu(Claim claim) {
        Faction faction = factionManager.getPlayerFaction(player.getUniqueId());
        if (faction == null) return;
        if (!canPlayerDo(faction, FactionPermission.PASDIC)) {
            player.sendMessage("§8[§6Carte§8] §cRang insuffisant (Officier requis pour le PASDIC).");
            return;
        }
        boolean enable = !claim.isPasdic();
        double cost = plugin.getConfigManager().getPasdicCost();
        String message = enable
                ? "Convertir ce chunk en PASDIC (" + String.format("%.0f", cost) + " Cantox, banque de faction) ?"
                : "Retirer le PASDIC de ce chunk (gratuit) ?";
        plugin.getUIManager().getNavigationManager().openUI(player, new ConfirmDialogUI(plugin, player,
                "§5§lPASDIC", message, confirmed -> {
            if (!confirmed) return;
            applyPasdic(claim, faction, enable, cost);
        }));
    }

    private void applyPasdic(Claim claim, Faction faction, boolean enable, double cost) {
        if (enable && faction.getBalance() < cost) {
            player.sendMessage("§8[§6Carte§8] §cBanque insuffisante (§e" + String.format("%.0f", cost)
                    + " §cCantox requis).");
            return;
        }
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) {
            player.sendMessage("§8[§6Carte§8] §cMonde introuvable.");
            return;
        }
        // Le scan conteneurs exige le chunk chargé : chargement async puis application main
        world.getChunkAtAsync(claim.getChunkX(), claim.getChunkZ(), false).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (chunk == null) {
                        player.sendMessage("§8[§6Carte§8] §cChunk introuvable.");
                        return;
                    }
                    String error = plugin.getPasdicService().setPasdic(claim, enable, chunk);
                    if (error != null) {
                        player.sendMessage("§8[§6Carte§8] §c" + error);
                        return;
                    }
                    if (enable) {
                        faction.setBalance(faction.getBalance() - cost);
                        factionManager.saveFaction(faction);
                        plugin.getBankManager().logTransaction(faction.getId(), player.getUniqueId(),
                                fr.cantale.plugin.economy.BankManager.ACTION_PASDIC, cost, faction.getBalance(),
                                "Conversion claim en PASDIC (carte)");
                        player.sendMessage("§8[§6Carte§8] §aCe claim est désormais un §5PASDIC§a.");
                    } else {
                        player.sendMessage("§8[§6Carte§8] §aPASDIC retiré de ce claim.");
                    }
                    plugin.getMapUI().invalidateChunk(session, claim.getChunkX(), claim.getChunkZ());
                }));
    }

    private boolean canPlayerDo(Faction faction, FactionPermission permission) {
        if (faction.isLeader(player.getUniqueId())) return true;
        FactionRank rank = faction.getMemberRank(player.getUniqueId());
        return permissionManager.hasPermission(faction.getId(), rank, permission);
    }

    @EventHandler
    public void onCloseSession(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (event.getView().getTopInventory() != inventory) return;
        plugin.getMapUI().closeSession(player.getUniqueId());
    }
}
