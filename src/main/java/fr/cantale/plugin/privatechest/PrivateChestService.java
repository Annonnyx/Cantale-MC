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
import java.util.List;
import java.util.Map;
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

    public PrivateChestService(CantalePlugin plugin) {
        this.plugin = plugin;
        this.chestDAO = new PrivateChestDAO(plugin);
        this.navKey = new org.bukkit.NamespacedKey(plugin, "pc_nav");
    }

    public int getPrivateChestCount(PlayerRank rank) {
        return switch (rank) {
            case NONE, AVENTURIER -> 1;
            case VIP -> 2;
            case CHEVRE, MODERATOR, ADMIN, OWNER -> 3;
        };
    }

    public int getPrivateChestSize(PlayerRank rank) {
        return switch (rank) {
            case NONE -> 27;
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
        if (online != null && online.isOnline()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PrivateChestHolder holder && holder.getType() == type) {
                online.closeInventory();
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

        int size = getPrivateChestSize(rank);
        boolean hasNavigation = getPrivateChestCount(rank) > 1;
        PrivateChestHolder holder = new PrivateChestHolder(type);
        Inventory inventory = Bukkit.createInventory(holder, size, type.getTitle());
        holder.setInventory(inventory);
        UUID uuid = player.getUniqueId();

        ItemStack[] cached = getCached(uuid, type);
        if (cached != null) {
            applyContents(player, inventory, cached, size, hasNavigation, rank, type);
            player.openInventory(inventory);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ChestSnapshot snapshot = chestDAO.loadChest(uuid, type, size);
            ItemStack[] contents = snapshot.contents() != null ? snapshot.contents() : new ItemStack[size];
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                applyContents(player, inventory, contents, size, hasNavigation, rank, type);
                updateCache(uuid, type, extractStorage(inventory));
                player.openInventory(inventory);
            });
        });
    }

    private void applyContents(Player player, Inventory inventory, ItemStack[] raw,
                               int size, boolean hasNavigation, PlayerRank rank, ChestType type) {
        ItemStack[] resized = resizeContents(raw, size);
        inventory.setContents(resized);

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

        if (hasNavigation) {
            addNavigationButtons(inventory, rank, type);
        }
    }

    public void saveChest(Player player, Inventory inventory, ChestType type, boolean isStaff) {
        if (type == ChestType.EC) {
            // Vanilla — Paper persiste tout seul
            return;
        }

        ItemStack[] storageContents = extractStorage(inventory);
        UUID uuid = player.getUniqueId();
        updateCache(uuid, type, storageContents.clone());

        String key = saveKey(uuid, type);
        long gen = saveGenerations.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        ItemStack[] snapshot = cloneContents(storageContents);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicLong current = saveGenerations.get(key);
            if (current == null || current.get() != gen) {
                return; // une save plus récente existe
            }
            chestDAO.saveChest(uuid, type, snapshot);
        });
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
