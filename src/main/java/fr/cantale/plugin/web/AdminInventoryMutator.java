package fr.cantale.plugin.web;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.privatechest.ChestType;
import fr.cantale.plugin.privatechest.PrivateChestService;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.UUID;

/**
 * Mutations inventaire / PC / EC / effets pour l'admin web.
 * Payload compact : {@code container}, {@code container:slot}, {@code container:slot:inner}.
 */
final class AdminInventoryMutator {

    private final CantalePlugin plugin;

    AdminInventoryMutator(CantalePlugin plugin) {
        this.plugin = plugin;
    }

    String clearContainer(UUID uuid, String name, String payload) {
        String target = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if (target.isBlank()) return "cible clear manquante (ex: all, inventory, ender, pc1)";

        UUID id = resolveUuid(uuid, name);
        if (id == null) return "joueur introuvable";
        Player online = resolveOnline(id, name);
        PrivateChestService pcs = plugin.getPrivateChestService();

        return switch (target) {
            case "all" -> {
                // Best-effort : hors-ligne on clear au moins PC (+ EC legacy).
                if (online != null) {
                    clearInventoryParts(online, true, true, true);
                    clearEffects(online, null);
                }
                clearEnder(id, online, pcs);
                String pcsErr = clearPcs(id, pcs, true, true, true);
                if (online == null && pcsErr != null) {
                    yield "hors-ligne : PC indisponible, inventaire/effets non clearables";
                }
                yield null;
            }
            case "inventory", "inv" -> clearInventoryParts(online, true, true, true);
            case "storage" -> clearInventoryParts(online, true, false, false);
            case "armor" -> clearInventoryParts(online, false, true, false);
            case "offhand" -> clearInventoryParts(online, false, false, true);
            case "ender", "ec", "ender_chest" -> clearEnder(id, online, pcs);
            case "pc1" -> clearPcs(id, pcs, true, false, false);
            case "pc2" -> clearPcs(id, pcs, false, true, false);
            case "pc3" -> clearPcs(id, pcs, false, false, true);
            case "pc_all", "pc" -> clearPcs(id, pcs, true, true, true);
            case "effects" -> clearEffects(online, null);
            default -> "cible clear inconnue: " + target;
        };
    }

    /**
     * Supprime un slot : {@code storage:12}, {@code pc1:5}, {@code ender:3:2} (item dans shulker).
     */
    String removeSlot(UUID uuid, String name, String payload) {
        if (payload == null || payload.isBlank()) return "payload slot manquant (ex: storage:12)";
        String[] parts = payload.trim().toLowerCase(Locale.ROOT).split(":");
        if (parts.length < 2) return "format: container:slot[:shulker_slot]";

        String container = parts[0];
        int slot;
        Integer inner = null;
        try {
            slot = Integer.parseInt(parts[1]);
            if (parts.length >= 3) inner = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "slot invalide";
        }

        UUID id = resolveUuid(uuid, name);
        if (id == null) return "joueur introuvable";
        Player online = resolveOnline(id, name);
        PrivateChestService pcs = plugin.getPrivateChestService();

        return switch (container) {
            case "storage" -> mutatePlayerArray(online, true, false, false, slot, inner);
            case "armor" -> mutatePlayerArray(online, false, true, false, slot, inner);
            case "offhand" -> mutatePlayerArray(online, false, false, true, slot, inner);
            case "ender", "ec", "ender_chest" -> mutateEnder(id, online, pcs, slot, inner);
            case "pc1" -> mutatePc(id, pcs, ChestType.PC1, slot, inner);
            case "pc2" -> mutatePc(id, pcs, ChestType.PC2, slot, inner);
            case "pc3" -> mutatePc(id, pcs, ChestType.PC3, slot, inner);
            default -> "container inconnu: " + container;
        };
    }

    /** Vide le contenu d'un shulker sans supprimer la boîte : {@code storage:12}. */
    String clearShulker(UUID uuid, String name, String payload) {
        if (payload == null || payload.isBlank()) return "payload manquant (ex: storage:12)";
        String[] parts = payload.trim().toLowerCase(Locale.ROOT).split(":");
        if (parts.length < 2) return "format: container:slot";
        int slot;
        try {
            slot = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "slot invalide";
        }
        String container = parts[0];
        UUID id = resolveUuid(uuid, name);
        if (id == null) return "joueur introuvable";
        Player online = resolveOnline(id, name);
        PrivateChestService pcs = plugin.getPrivateChestService();

        return switch (container) {
            case "storage" -> clearShulkerInPlayer(online, true, false, false, slot);
            case "armor" -> clearShulkerInPlayer(online, false, true, false, slot);
            case "offhand" -> clearShulkerInPlayer(online, false, false, true, slot);
            case "ender", "ec", "ender_chest" -> clearShulkerInEnder(id, online, pcs, slot);
            case "pc1" -> clearShulkerInPc(id, pcs, ChestType.PC1, slot);
            case "pc2" -> clearShulkerInPc(id, pcs, ChestType.PC2, slot);
            case "pc3" -> clearShulkerInPc(id, pcs, ChestType.PC3, slot);
            default -> "container inconnu: " + container;
        };
    }

    /** {@code type:amplifier:seconds} — ex. {@code speed:1:120}. */
    String giveEffect(UUID uuid, String name, String payload) {
        Player online = resolveOnline(resolveUuid(uuid, name), name);
        if (online == null) return "joueur hors-ligne";
        if (payload == null || payload.isBlank()) return "payload effet manquant (ex: speed:0:60)";
        String[] parts = payload.trim().toLowerCase(Locale.ROOT).split(":");
        if (parts.length < 1) return "effet invalide";
        PotionEffectType type = resolveEffect(parts[0]);
        if (type == null) return "effet inconnu: " + parts[0];
        int amplifier = 0;
        int seconds = 60;
        try {
            if (parts.length >= 2) amplifier = Integer.parseInt(parts[1]);
            if (parts.length >= 3) seconds = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "ampli/durée invalides";
        }
        if (amplifier < 0) amplifier = 0;
        if (seconds < 1) seconds = 1;
        if (seconds > 3600) seconds = 3600;
        online.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true, true));
        return null;
    }

    String clearEffect(UUID uuid, String name, String payload) {
        Player online = resolveOnline(resolveUuid(uuid, name), name);
        if (online == null) return "joueur hors-ligne";
        if (payload == null || payload.isBlank() || "all".equalsIgnoreCase(payload.trim())) {
            return clearEffects(online, null);
        }
        PotionEffectType type = resolveEffect(payload.trim());
        if (type == null) return "effet inconnu: " + payload;
        return clearEffects(online, type);
    }

    private String clearInventoryParts(Player online, boolean storage, boolean armor, boolean offhand) {
        if (online == null) return "joueur hors-ligne (inventaire)";
        PlayerInventory inv = online.getInventory();
        if (storage) {
            ItemStack[] empty = new ItemStack[inv.getStorageContents().length];
            inv.setStorageContents(empty);
        }
        if (armor) inv.setArmorContents(new ItemStack[4]);
        if (offhand) inv.setItemInOffHand(null);
        return null;
    }

    private String clearEnder(UUID id, Player online, PrivateChestService pcs) {
        if (online != null) {
            online.getEnderChest().clear();
            return null;
        }
        if (pcs != null) {
            pcs.adminClearChest(id, ChestType.EC);
            return null;
        }
        return "ender chest indisponible hors-ligne";
    }

    private String clearPcs(UUID id, PrivateChestService pcs, boolean p1, boolean p2, boolean p3) {
        if (pcs == null) return "service PC indisponible";
        if (p1) pcs.adminClearChest(id, ChestType.PC1);
        if (p2) pcs.adminClearChest(id, ChestType.PC2);
        if (p3) pcs.adminClearChest(id, ChestType.PC3);
        return null;
    }

    private String clearEffects(Player online, PotionEffectType only) {
        if (online == null) return "joueur hors-ligne (effets)";
        if (only == null) {
            for (PotionEffect effect : online.getActivePotionEffects()) {
                online.removePotionEffect(effect.getType());
            }
        } else {
            online.removePotionEffect(only);
        }
        return null;
    }

    private String mutatePlayerArray(Player online, boolean storage, boolean armor, boolean offhand,
                                     int slot, Integer inner) {
        if (online == null) return "joueur hors-ligne";
        PlayerInventory inv = online.getInventory();
        if (storage) {
            ItemStack[] arr = inv.getStorageContents();
            String err = mutateArray(arr, slot, inner);
            if (err != null) return err;
            inv.setStorageContents(arr);
            return null;
        }
        if (armor) {
            ItemStack[] arr = inv.getArmorContents();
            String err = mutateArray(arr, slot, inner);
            if (err != null) return err;
            inv.setArmorContents(arr);
            return null;
        }
        if (offhand) {
            if (slot != 0) return "offhand slot doit être 0";
            if (inner == null) {
                inv.setItemInOffHand(null);
                return null;
            }
            ItemStack stack = inv.getItemInOffHand();
            ItemStack updated = removeFromShulker(stack, inner);
            if (updated == null && stack != null) return "pas un shulker ou slot interne invalide";
            inv.setItemInOffHand(updated);
            return null;
        }
        return "cible inventaire invalide";
    }

    private String mutateEnder(UUID id, Player online, PrivateChestService pcs, int slot, Integer inner) {
        if (online != null) {
            ItemStack[] arr = online.getEnderChest().getContents();
            String err = mutateArray(arr, slot, inner);
            if (err != null) return err;
            online.getEnderChest().setContents(arr);
            return null;
        }
        if (pcs == null) return "ender chest indisponible hors-ligne";
        ItemStack[] arr = pcs.peekStorage(id, ChestType.EC);
        String err = mutateArray(arr, slot, inner);
        if (err != null) return err;
        pcs.adminReplaceStorage(id, ChestType.EC, arr);
        return null;
    }

    private String mutatePc(UUID id, PrivateChestService pcs, ChestType type, int slot, Integer inner) {
        if (pcs == null) return "service PC indisponible";
        ItemStack[] arr = pcs.peekStorage(id, type);
        String err = mutateArray(arr, slot, inner);
        if (err != null) return err;
        pcs.adminReplaceStorage(id, type, arr);
        return null;
    }

    private String mutateArray(ItemStack[] arr, int slot, Integer inner) {
        if (arr == null || slot < 0 || slot >= arr.length) return "slot hors limites";
        if (inner == null) {
            arr[slot] = null;
            return null;
        }
        ItemStack updated = removeFromShulker(arr[slot], inner);
        if (updated == null) return "pas un shulker ou slot interne invalide";
        arr[slot] = updated;
        return null;
    }

    private String clearShulkerInPlayer(Player online, boolean storage, boolean armor, boolean offhand, int slot) {
        if (online == null) return "joueur hors-ligne";
        PlayerInventory inv = online.getInventory();
        if (storage) {
            ItemStack[] arr = inv.getStorageContents();
            String err = emptyShulkerAt(arr, slot);
            if (err != null) return err;
            inv.setStorageContents(arr);
            return null;
        }
        if (armor) {
            ItemStack[] arr = inv.getArmorContents();
            String err = emptyShulkerAt(arr, slot);
            if (err != null) return err;
            inv.setArmorContents(arr);
            return null;
        }
        ItemStack stack = inv.getItemInOffHand();
        ItemStack emptied = emptyShulker(stack);
        if (emptied == null) return "pas un shulker";
        inv.setItemInOffHand(emptied);
        return null;
    }

    private String clearShulkerInEnder(UUID id, Player online, PrivateChestService pcs, int slot) {
        if (online != null) {
            ItemStack[] arr = online.getEnderChest().getContents();
            String err = emptyShulkerAt(arr, slot);
            if (err != null) return err;
            online.getEnderChest().setContents(arr);
            return null;
        }
        if (pcs == null) return "ender chest indisponible hors-ligne";
        ItemStack[] arr = pcs.peekStorage(id, ChestType.EC);
        String err = emptyShulkerAt(arr, slot);
        if (err != null) return err;
        pcs.adminReplaceStorage(id, ChestType.EC, arr);
        return null;
    }

    private String clearShulkerInPc(UUID id, PrivateChestService pcs, ChestType type, int slot) {
        if (pcs == null) return "service PC indisponible";
        ItemStack[] arr = pcs.peekStorage(id, type);
        String err = emptyShulkerAt(arr, slot);
        if (err != null) return err;
        pcs.adminReplaceStorage(id, type, arr);
        return null;
    }

    private static String emptyShulkerAt(ItemStack[] arr, int slot) {
        if (arr == null || slot < 0 || slot >= arr.length) return "slot hors limites";
        ItemStack emptied = emptyShulker(arr[slot]);
        if (emptied == null) return "pas un shulker";
        arr[slot] = emptied;
        return null;
    }

    private static ItemStack emptyShulker(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof BlockStateMeta bsm)) return null;
        if (!bsm.hasBlockState() || !(bsm.getBlockState() instanceof ShulkerBox box)) return null;
        box.getInventory().clear();
        bsm.setBlockState(box);
        ItemStack clone = stack.clone();
        clone.setItemMeta(bsm);
        return clone;
    }

    /** Retourne la shulker mise à jour, ou null si échec. Si inner retire le dernier item utile, garde la boîte. */
    private static ItemStack removeFromShulker(ItemStack stack, int innerSlot) {
        if (stack == null || !(stack.getItemMeta() instanceof BlockStateMeta bsm)) return null;
        if (!bsm.hasBlockState() || !(bsm.getBlockState() instanceof ShulkerBox box)) return null;
        ItemStack[] contents = box.getInventory().getContents();
        if (innerSlot < 0 || innerSlot >= contents.length) return null;
        contents[innerSlot] = null;
        box.getInventory().setContents(contents);
        bsm.setBlockState(box);
        ItemStack clone = stack.clone();
        clone.setItemMeta(bsm);
        return clone;
    }

    private static PotionEffectType resolveEffect(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        PotionEffectType byKey = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (byKey != null) return byKey;
        // fallback noms legacy
        return PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
    }

    private UUID resolveUuid(UUID uuid, String name) {
        if (uuid != null) return uuid;
        if (name != null && !name.isBlank()) {
            return org.bukkit.Bukkit.getOfflinePlayer(name).getUniqueId();
        }
        return null;
    }

    private Player resolveOnline(UUID uuid, String name) {
        if (uuid != null) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) return p;
        }
        if (name != null && !name.isBlank()) {
            Player p = org.bukkit.Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) return p;
        }
        return null;
    }
}
