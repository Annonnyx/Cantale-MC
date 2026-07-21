package fr.cantale.plugin.privatechest;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.rank.PlayerRank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PrivateChestService {

    private final CantalePlugin plugin;
    private final PrivateChestDAO chestDAO;
    // Cache local des contenus de coffre pour éviter les problèmes de synchronisation BDD
    private final Map<UUID, Map<ChestType, ItemStack[]>> cache = new ConcurrentHashMap<>();

    public PrivateChestService(CantalePlugin plugin) {
        this.plugin = plugin;
        this.chestDAO = new PrivateChestDAO(plugin);
    }

    /**
     * Nombre de PC (coffres privés) disponibles selon le grade.
     */
    public int getPrivateChestCount(PlayerRank rank) {
        return switch (rank) {
            case NONE -> 1;
            case AVENTURIER -> 1;
            case VIP -> 2;
            case CHEVRE, MODERATOR, ADMIN, OWNER -> 3;
        };
    }

    /**
     * Taille d'un coffre privé selon le grade.
     */
    public int getPrivateChestSize(PlayerRank rank) {
        return switch (rank) {
            case NONE -> 27;
            case AVENTURIER -> 54;
            case VIP, CHEVRE, MODERATOR, ADMIN, OWNER -> 54;
        };
    }

    /**
     * Taille de l'ender chest selon le grade.
     */
    public int getEnderChestSize(PlayerRank rank) {
        return (rank == PlayerRank.CHEVRE || rank == PlayerRank.MODERATOR || rank == PlayerRank.ADMIN || rank == PlayerRank.OWNER) ? 54 : 27;
    }

    /**
     * Indique si un joueur peut utiliser un type de coffre.
     */
    public boolean canAccess(PlayerRank rank, ChestType type) {
        return switch (type) {
            case EC -> true;
            case PC1 -> getPrivateChestCount(rank) >= 1;
            case PC2 -> getPrivateChestCount(rank) >= 2;
            case PC3 -> getPrivateChestCount(rank) >= 3;
        };
    }

    /**
     * Renvoie le type suivant accessible par le joueur (cycle PC1 → PC2 → PC3 → PC1).
     */
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
     * Ouvre un inventaire de coffre privé pour le joueur, avec barre de navigation si nécessaire.
     */
    public void openChest(Player player, ChestType type, boolean isStaff) {
        PlayerRank rank = isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);

        if (!canAccess(rank, type)) {
            player.sendMessage("§8[§6Cantale§8] §cVous n'avez pas accès à ce coffre.");
            return;
        }

        int size = type == ChestType.EC ? getEnderChestSize(rank) : getPrivateChestSize(rank);
        boolean hasNavigation = type != ChestType.EC && getPrivateChestCount(rank) > 1;
        int storageSize = hasNavigation ? size - 9 : size;

        Inventory inventory = Bukkit.createInventory(null, size, type.getTitle());
        UUID uuid = player.getUniqueId();

        // On charge d'abord depuis le cache local si disponible, sinon depuis la BDD
        ItemStack[] cached = cache.get(uuid) != null ? cache.get(uuid).get(type) : null;
        if (cached != null && cached.length == storageSize) {
            inventory.setContents(cached);
            if (hasNavigation) addNavigationButtons(inventory, rank, type);
            player.openInventory(inventory);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ChestSnapshot snapshot = chestDAO.loadChest(uuid, type, storageSize);
            ItemStack[] contents = snapshot.contents();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (contents != null && contents.length == storageSize) {
                    inventory.setContents(contents);
                    updateCache(uuid, type, contents.clone());
                }
                if (hasNavigation) {
                    addNavigationButtons(inventory, rank, type);
                }
                if (!player.isOnline()) return;
                player.openInventory(inventory);
            });
        });
    }

    /**
     * Sauvegarde le contenu d'un inventaire ouvert et met à jour le cache local.
     */
    public void saveChest(Player player, Inventory inventory, ChestType type, boolean isStaff) {
        PlayerRank rank = isStaff ? PlayerRank.CHEVRE : plugin.getRankManager().getRank(player);
        int size = type == ChestType.EC ? getEnderChestSize(rank) : getPrivateChestSize(rank);
        boolean hasNavigation = type != ChestType.EC && getPrivateChestCount(rank) > 1;
        int storageSize = hasNavigation ? size - 9 : size;

        ItemStack[] contents = inventory.getContents();
        ItemStack[] storageContents = new ItemStack[storageSize];
        System.arraycopy(contents, 0, storageContents, 0, storageSize);

        updateCache(player.getUniqueId(), type, storageContents.clone());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            chestDAO.saveChest(player.getUniqueId(), type, storageContents);
        });
    }

    /**
     * Tente d'ajouter un item dans le coffre privé d'un joueur (PC1 en priorité).
     * Retourne les items qui n'ont pas pu être stockés.
     */
    public java.util.List<ItemStack> storeItemsInPrivateChest(UUID uuid, java.util.List<ItemStack> items) {
        java.util.List<ItemStack> remaining = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) return remaining;

        PlayerRank rank = plugin.getRankManager().getRank(uuid);
        int pcCount = getPrivateChestCount(rank);
        if (pcCount <= 0) {
            remaining.addAll(items);
            return remaining;
        }

        // Recharger les coffres depuis la BDD si pas en cache
        Map<ChestType, ItemStack[]> playerCache = cache.computeIfAbsent(uuid, k -> new HashMap<>());
        for (ChestType type : new ChestType[]{ChestType.PC1, ChestType.PC2, ChestType.PC3}) {
            if (playerCache.containsKey(type)) continue;
            if (!canAccess(rank, type)) continue;
            int size = getPrivateChestSize(rank);
            ChestSnapshot snapshot = chestDAO.loadChest(uuid, type, size);
            playerCache.put(type, snapshot.contents());
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
                    if (contents[i].isSimilar(toStore) && contents[i].getAmount() + toStore.getAmount() <= contents[i].getMaxStackSize()) {
                        contents[i].setAmount(contents[i].getAmount() + toStore.getAmount());
                        stored = true;
                        break;
                    }
                }
                if (stored) break;
            }

            if (!stored) {
                remaining.add(toStore);
            }
        }

        // Sauvegarder tous les coffres modifiés
        for (ChestType type : new ChestType[]{ChestType.PC1, ChestType.PC2, ChestType.PC3}) {
            if (playerCache.containsKey(type)) {
                final ItemStack[] contents = playerCache.get(type);
                chestDAO.saveChest(uuid, type, contents);
            }
        }

        return remaining;
    }

    private void updateCache(UUID uuid, ChestType type, ItemStack[] contents) {
        cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, contents);
    }

    /**
     * Renvoie le type de coffre associé au titre d'inventaire, ou null.
     */
    public ChestType resolveTypeFromTitle(String title) {
        for (ChestType type : ChestType.values()) {
            if (type.getTitle().equals(title)) return type;
        }
        return null;
    }

    private void addNavigationButtons(Inventory inventory, PlayerRank rank, ChestType current) {
        int size = inventory.getSize();
        int bottomRowStart = size - 9;

        ChestType next = getNextPrivateChest(rank, current);
        if (next != current) {
            inventory.setItem(bottomRowStart + 8, createNavItem(org.bukkit.Material.ARROW, "§eSuivant", "§7Cliquez pour ouvrir " + next.getTitle()));
        }
    }

    private org.bukkit.inventory.ItemStack createNavItem(org.bukkit.Material material, String name, String lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
