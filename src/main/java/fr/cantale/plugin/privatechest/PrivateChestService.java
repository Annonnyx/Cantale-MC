package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PrivateChestService {

    private final CantalePlugin plugin;
    private final PrivateChestDAO chestDAO;
    private final org.bukkit.NamespacedKey navKey;
    private final Map<UUID, Map<ChestType, ItemStack[]>> cache = new ConcurrentHashMap<>();
    /** Génération monotone par coffre — empêche une save async obsolète d'écraser la plus récente. */
    private final Map<String, AtomicLong> saveGenerations = new ConcurrentHashMap<>();
    /**
     * Joueurs dont un close/save PC est déjà en cours sur ce thread.
     * Empêche closeInventory() → InventoryCloseEvent → save → closeInventory() en boucle.
     */
    private final ThreadLocal<Set<UUID>> closeSaveGuard = ThreadLocal.withInitial(HashSet::new);

    public PrivateChestService(CantalePlugin plugin) {
        this.plugin = plugin;
        this.chestDAO = new PrivateChestDAO(plugin);
        this.navKey = new org.bukkit.NamespacedKey(plugin, "pc_nav");
    }

    /** @return false si ce joueur est déjà gardé (appel réentrant — ignorer le close). */
    public boolean enterCloseSaveGuard(UUID playerId) {
        return playerId != null && closeSaveGuard.get().add(playerId);
    }

    public void leaveCloseSaveGuard(UUID playerId) {
        if (playerId != null) {
            closeSaveGuard.get().remove(playerId);
        }
    }

    public boolean isCloseSaveGuarded(UUID playerId) {
        return playerId != null && closeSaveGuard.get().contains(playerId);
    }

    /**
     * Ferme l'inventaire d'un autre viewer sans re-déclencher une save via onInventoryClose.
     * No-op si le viewer est déjà dans le garde (ex. le joueur qui ferme actuellement).
     */
    private void closeViewerWithoutResave(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        UUID id = viewer.getUniqueId();
        if (!enterCloseSaveGuard(id)) {
            return;
        }
        try {
            viewer.closeInventory();
        } finally {
            leaveCloseSaveGuard(id);
        }
    }

    public int getPrivateChestCount(PlayerRank rank) {
        // Modo = aucun avantage de grade : 1 coffre comme un joueur sans grade.
        return switch (rank) {
            case NONE, AVENTURIER, MODERATOR -> 1;
            case VIP -> 2;
            case CHEVRE, ADMIN, OWNER -> 3;
        };
    }

    public int getPrivateChestSize(PlayerRank rank) {
        return switch (rank) {
            case NONE, MODERATOR -> 27;
            default -> 54;
        };
    }

    public boolean canAccess(PlayerRank rank, ChestType type) {
        return switch (type) {
            case EC -> true;
            case PC1 -> getPrivateChestCount(rank) >= 1;
            case PC2 -> getPrivateChestCount(rank) >= 2;
            case PC3 -> getPrivateChestCount(rank) >= 3;
        };
    }

    /**
     * Lecture sans ouvrir d'inventaire (cache mémoire ou DB) — pour l'inspection admin web.
     * Ne renvoie pas les boutons de navigation PC.
     */
    public ItemStack[] peekStorage(UUID uuid, ChestType type) {
        if (uuid == null || type == null) return new ItemStack[0];
        ItemStack[] cached = getCached(uuid, type);
        if (cached != null) {
            return cloneContents(cached);
        }
        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        int size = type == ChestType.EC ? 27 : getPrivateChestSize(rank);
        ChestSnapshot snapshot = chestDAO.loadChest(uuid, type, size);
        ItemStack[] contents = snapshot.contents() != null ? snapshot.contents() : new ItemStack[size];
        return resizeContents(contents, size);
    }

    /**
     * Remplace le contenu d'un PC (ou EC legacy) côté admin web, persiste en DB,
     * et ferme l'inventaire ouvert du joueur s'il regarde ce coffre.
     */
    public void adminReplaceStorage(UUID uuid, ChestType type, ItemStack[] contents) {
        if (uuid == null || type == null) return;
        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        int size = type == ChestType.EC ? 27 : getPrivateChestSize(rank);
        ItemStack[] resized = resizeContents(contents, size);
        updateCache(uuid, type, resized);

        String key = saveKey(uuid, type);
        long gen = saveGenerations.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        ItemStack[] snapshot = cloneContents(resized);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicLong current = saveGenerations.get(key);
            if (current == null || current.get() != gen) return;
            chestDAO.saveChest(uuid, type, snapshot);
        });

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline() && !isCloseSaveGuarded(uuid)) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PrivateChestHolder holder && holder.getType() == type) {
                closeViewerWithoutResave(online);
            }
        }
    }

    public void adminClearChest(UUID uuid, ChestType type) {
        if (uuid == null || type == null) return;
        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        int size = type == ChestType.EC ? 27 : getPrivateChestSize(rank);
        adminReplaceStorage(uuid, type, new ItemStack[size]);
    }

    public ChestType getNextPrivateChest(PlayerRank rank, ChestType current) {
        int count = getPrivateChestCount(rank);
        return switch (current) {
            case PC1 -> count >= 2 ? ChestType.PC2 : ChestType.PC1;
            case PC2 -> count >= 3 ? ChestType.PC3 : ChestType.PC1;
            case PC3 -> ChestType.PC1;
            default -> ChestType.PC1;
        };
    }

    /**
     * Ouvre l'ender chest vanilla Minecraft (persistance native — plus de double stockage).
     * Migre une éventuelle ancienne sauvegarde virtuelle {@code ec} si le vanilla est vide.
     */
    public void openVanillaEnderChest(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] vanilla = player.getEnderChest().getContents();
        boolean vanillaEmpty = isEmpty(vanilla);

        if (!vanillaEmpty) {
            player.openInventory(player.getEnderChest());
            return;
        }

        // Migration one-shot : anciennes données virtuelles → vanilla
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ChestSnapshot snap = chestDAO.loadChest(uuid, ChestType.EC, 27);
            ItemStack[] virtual = snap.contents();
            boolean hasVirtual = !isEmpty(virtual);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (hasVirtual && isEmpty(player.getEnderChest().getContents())) {
                    ItemStack[] migrated = resizeContents(virtual, 27);
                    player.getEnderChest().setContents(migrated);
                    cache.computeIfAbsent(uuid, k -> new HashMap<>()).remove(ChestType.EC);
                    player.sendMessage("§8[§6Cantale§8] §aTes items d'ender chest ont été restaurés.");
                    List<ItemStack> overflow = collectOverflow(virtual, 27);
                    for (ItemStack extra : overflow) {
                        HashMap<Integer, ItemStack> left = player.getInventory().addItem(extra);
                        left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                    }
                    // Effacer l'ancienne ligne virtuelle hors du thread principal
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                        chestDAO.saveChest(uuid, ChestType.EC, new ItemStack[27]));
                }
                player.openInventory(player.getEnderChest());
            });
        });
    }

    public void openChest(Player player, ChestType type, boolean isStaff) {
        if (type == ChestType.EC) {
            openVanillaEnderChest(player);
            return;
        }

        PlayerRank rank = isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);
        if (!canAccess(rank, type)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas accès à ce coffre.");
            return;
        }

        openOwnedChest(player, player.getUniqueId(), type, rank, false, null);
    }

    /**
     * Ouvre le coffre privé d'un autre joueur pour inspection/édition staff
     * (en ligne ou hors ligne — contenu DB + cache).
     */
    public void openForeignChest(Player viewer, UUID ownerUuid, String ownerName, ChestType type) {
        if (type == ChestType.EC) {
            viewer.sendMessage("§8[§6Cantale§8] §cUtilise §e/adminec §cpour l'ender chest.");
            return;
        }
        if (ownerUuid == null) {
            viewer.sendMessage("§8[§6Cantale§8] §cJoueur introuvable.");
            return;
        }

        PlayerRank ownerRank = plugin.getRankManager().getRank(ownerUuid);
        if (!canAccess(ownerRank, type)) {
            viewer.sendMessage("§8[§6Cantale§8] §c" + (ownerName != null ? ownerName : "Ce joueur")
                    + " n'a pas accès à " + type.getTitle() + "§c.");
            return;
        }

        openOwnedChest(viewer, ownerUuid, type, ownerRank, true, ownerName);
    }

    private boolean openWithMod(Player viewer, Inventory inventory, String title, boolean hasNavigation) {
        fr.cantale.plugin.ui.net.ClientUiBridge bridge = plugin.getClientUiBridge();
        return bridge != null && bridge.presentStorage(viewer, inventory, title, hasNavigation);
    }

    private void openOwnedChest(Player viewer, UUID ownerUuid, ChestType type, PlayerRank sizeRank,
                                boolean foreignInspect, String ownerName) {
        int size = getPrivateChestSize(sizeRank);
        boolean hasNavigation = getPrivateChestCount(sizeRank) > 1;
        PrivateChestHolder holder = new PrivateChestHolder(type, foreignInspect ? ownerUuid : null, foreignInspect);
        String title = foreignInspect
                ? "§c[Staff] " + type.getTitle() + " §8— §e" + (ownerName != null ? ownerName : "joueur")
                : type.getTitle();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        ItemStack[] cached = getCached(ownerUuid, type);
        if (cached != null) {
            applyContents(viewer, inventory, cached, size, hasNavigation, sizeRank, type, foreignInspect);
            if (!openWithMod(viewer, inventory, title, hasNavigation)) {
                viewer.openInventory(inventory);
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ChestSnapshot snapshot = chestDAO.loadChest(ownerUuid, type, size);
            ItemStack[] contents = snapshot.contents() != null ? snapshot.contents() : new ItemStack[size];
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                applyContents(viewer, inventory, contents, size, hasNavigation, sizeRank, type, foreignInspect);
                updateCache(ownerUuid, type, extractStorage(inventory));
                if (!openWithMod(viewer, inventory, title, hasNavigation)) {
                    viewer.openInventory(inventory);
                }
            });
        });
    }

    private void applyContents(Player player, Inventory inventory, ItemStack[] raw,
                               int size, boolean hasNavigation, PlayerRank rank, ChestType type,
                               boolean foreignInspect) {
        ItemStack[] resized = resizeContents(raw, size);
        inventory.setContents(resized);

        // Overflow de resize : uniquement pour le propriétaire (évite de dump dans l'inv staff)
        if (!foreignInspect) {
            List<ItemStack> overflow = collectOverflow(raw, size);
            for (ItemStack extra : overflow) {
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(extra);
                if (!left.isEmpty()) {
                    left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                    player.sendMessage("§8[§6Cantale§8] §eDes items en trop ont été déposés (inventaire plein).");
                } else {
                    player.sendMessage("§8[§6Cantale§8] §eDes items ont été déplacés dans ton inventaire (changement de taille de coffre).");
                }
            }
        }

        if (hasNavigation) {
            addNavigationButtons(inventory, rank, type);
        }
    }

    public void saveChest(Player player, Inventory inventory, ChestType type, boolean isStaff) {
        UUID owner = player.getUniqueId();
        if (inventory.getHolder() instanceof PrivateChestHolder holder && holder.isForeignInspect()
                && holder.getOwnerUuid() != null) {
            owner = holder.getOwnerUuid();
        }
        saveChestForOwner(owner, inventory, type);
    }

    private void saveChestForOwner(UUID ownerUuid, Inventory inventory, ChestType type) {
        if (type == ChestType.EC) {
            // Vanilla — Paper persiste tout seul
            return;
        }

        ItemStack[] storageContents = extractStorage(inventory);
        updateCache(ownerUuid, type, storageContents.clone());

        String key = saveKey(ownerUuid, type);
        long gen = saveGenerations.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        ItemStack[] snapshot = cloneContents(storageContents);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicLong current = saveGenerations.get(key);
            if (current == null || current.get() != gen) {
                return; // une save plus récente existe
            }
            chestDAO.saveChest(ownerUuid, type, snapshot);
        });

        // Si le propriétaire a ce coffre ouvert (autre viewer), fermer pour éviter un écrasement croisé.
        // Ne jamais closeInventory() le joueur déjà en train de fermer — pendant InventoryCloseEvent
        // getOpenInventory() pointe encore sur le coffre et re-déclencherait une boucle infinie.
        Player online = Bukkit.getPlayer(ownerUuid);
        if (online != null && online.isOnline() && !isCloseSaveGuarded(ownerUuid)) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PrivateChestHolder holder
                    && holder.getType() == type
                    && !holder.isForeignInspect()) {
                closeViewerWithoutResave(online);
            }
        }
    }

    /**
     * Tente d'ajouter des items dans les PC (PC1→PC3). Retourne le surplus.
     */
    public List<ItemStack> storeItemsInPrivateChest(UUID uuid, List<ItemStack> items) {
        List<ItemStack> remaining = new ArrayList<>();
        if (items == null || items.isEmpty()) return remaining;

        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        Map<ChestType, ItemStack[]> playerCache = cache.computeIfAbsent(uuid, k -> new HashMap<>());

        for (ChestType type : new ChestType[]{ChestType.PC1, ChestType.PC2, ChestType.PC3}) {
            if (!canAccess(rank, type)) continue;
            if (playerCache.containsKey(type)) continue;
            int size = getPrivateChestSize(rank);
            ChestSnapshot snapshot = chestDAO.loadChest(uuid, type, size);
            playerCache.put(type, resizeContents(
                snapshot.contents() != null ? snapshot.contents() : new ItemStack[size], size));
        }

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            ItemStack toStore = item.clone();
            boolean stored = false;

            for (ChestType type : new ChestType[]{ChestType.PC1, ChestType.PC2, ChestType.PC3}) {
                if (!canAccess(rank, type)) continue;
                ItemStack[] contents = playerCache.get(type);
                if (contents == null) continue;

                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) {
                        contents[i] = toStore;
                        stored = true;
                        break;
                    }
                    if (contents[i].isSimilar(toStore)
                        && contents[i].getAmount() + toStore.getAmount() <= contents[i].getMaxStackSize()) {
                        contents[i].setAmount(contents[i].getAmount() + toStore.getAmount());
                        stored = true;
                        break;
                    }
                }
                if (stored) {
                    String key = saveKey(uuid, type);
                    long gen = saveGenerations.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
                    ItemStack[] snap = cloneContents(contents);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        AtomicLong current = saveGenerations.get(key);
                        if (current != null && current.get() == gen) {
                            chestDAO.saveChest(uuid, type, snap);
                        }
                    });
                    break;
                }
            }

            if (!stored) remaining.add(toStore);
        }
        return remaining;
    }

    public ChestType resolveTypeFromInventory(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof PrivateChestHolder holder) {
            return holder.getType();
        }
        return null;
    }

    public ChestType resolveTypeFromTitle(String title) {
        if (title == null) return null;
        for (ChestType type : ChestType.values()) {
            if (type == ChestType.EC) continue;
            if (type.getTitle().equals(title)) return type;
        }
        return null;
    }

    public boolean isNavItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(navKey, PersistentDataType.BYTE);
    }

    public int getNavRowStart(Inventory inventory) {
        return inventory.getSize() - 9;
    }

    private void addNavigationButtons(Inventory inventory, PlayerRank rank, ChestType current) {
        int bottomRowStart = inventory.getSize() - 9;
        ChestType next = getNextPrivateChest(rank, current);
        if (next != current) {
            inventory.setItem(bottomRowStart + 8, createNavItem(next));
        }
    }

    private ItemStack createNavItem(ChestType next) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eSuivant");
            meta.setLore(List.of("§7Cliquez pour ouvrir " + next.getTitle()));
            meta.getPersistentDataContainer().set(navKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Contenu à persister : sans les boutons de navigation. */
    private ItemStack[] extractStorage(Inventory inventory) {
        ItemStack[] raw = inventory.getContents();
        ItemStack[] out = new ItemStack[raw.length];
        for (int i = 0; i < raw.length; i++) {
            ItemStack stack = raw[i];
            out[i] = isNavItem(stack) ? null : (stack == null ? null : stack.clone());
        }
        return out;
    }

    static ItemStack[] resizeContents(ItemStack[] src, int targetSize) {
        ItemStack[] out = new ItemStack[targetSize];
        if (src == null) return out;
        int copy = Math.min(src.length, targetSize);
        for (int i = 0; i < copy; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }

    static List<ItemStack> collectOverflow(ItemStack[] src, int keptSize) {
        List<ItemStack> overflow = new ArrayList<>();
        if (src == null || src.length <= keptSize) return overflow;
        for (int i = keptSize; i < src.length; i++) {
            if (src[i] != null && !src[i].getType().isAir()) {
                overflow.add(src[i].clone());
            }
        }
        return overflow;
    }

    private static boolean isEmpty(ItemStack[] contents) {
        if (contents == null) return true;
        for (ItemStack s : contents) {
            if (s != null && !s.getType().isAir()) return false;
        }
        return true;
    }

    private ItemStack[] getCached(UUID uuid, ChestType type) {
        Map<ChestType, ItemStack[]> map = cache.get(uuid);
        return map == null ? null : map.get(type);
    }

    private void updateCache(UUID uuid, ChestType type, ItemStack[] contents) {
        cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, contents);
    }

    private static ItemStack[] cloneContents(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }

    private static String saveKey(UUID uuid, ChestType type) {
        return uuid + ":" + type.getKey();
    }
}
