package fr.cantale.plugin.custom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CustomItemManager {

    private static final String CUSTOM_ITEM_KEY = "cantale_custom_item";
    private static final String AUTH_KEY = "cantale_auth";
    // Server secret for anti-fake verification — change this for your server!
    private static final String SERVER_SECRET = "Cantale_2024_xK9mP2vQ";

    public ItemStack createCustomItem(CustomItemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GOLD + type.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Item custom Cantale");

        if (type.isPickantise()) {
            addPickantiseLore(type, lore);
        } else if (type.isCantaxe()) {
            addCantaxeLore(type, lore);
        } else if (type.isMultiCantool()) {
            addMultiCantoolLore(type, lore);
        } else if (type.isStatioLytras()) {
            addStatioLytrasLore(lore);
        } else if (type.isCantalame()) {
            addCantalameLore(lore);
        } else if (type == CustomItemType.VIE) {
            addVieLore(lore);
        } else if (type.isRuneFortification()) {
            addRuneFortificationLore(lore);
        }

        meta.setLore(lore);

        // Enchantements
        if (type.isPickantise()) {
            addPickantiseEnchantments(type, meta);
        } else if (type.isCantaxe()) {
            addCantaxeEnchantments(type, meta);
        } else if (type.isMultiCantool()) {
            addMultiCantoolEnchantments(type, meta);
        }

        // CustomModelData pour le resource pack
        int modelData = getCustomModelData(type);
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }

        // Marquer comme item custom avec type
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("cantale", "custom_item"),
                org.bukkit.persistence.PersistentDataType.STRING,
                type.name()
        );

        // Anti-fake: ajouter un hash de vérification
        String authHash = generateAuthHash(type.name());
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("cantale", AUTH_KEY),
                org.bukkit.persistence.PersistentDataType.STRING,
                authHash
        );

        item.setItemMeta(meta);
        return item;
    }

    private void addPickantiseLore(CustomItemType type, List<String> lore) {
        lore.add(ChatColor.AQUA + "Zone de minage : " + type.getMiningRadius() + "x" + type.getMiningRadius());
        lore.add(ChatColor.GRAY + "Enchantable (Fortune, Silk Touch, Unbreaking)");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addCantaxeLore(CustomItemType type, List<String> lore) {
        lore.add(ChatColor.AQUA + "One-shot tous les arbres");
        lore.add(ChatColor.AQUA + "Mine comme une efficacité max");
        lore.add(ChatColor.GRAY + "Enchantable (Silk Touch, Unbreaking)");
    }

    private void addMultiCantoolLore(CustomItemType type, List<String> lore) {
        lore.add(ChatColor.AQUA + "Outil multifonction");
        lore.add(ChatColor.GRAY + "S'adapte au block : Pioche + Hache + Pelle + Cisailles");
        if (type.getMiningRadius() == 1) {
            lore.add(ChatColor.GRAY + "Mode 1x1 (un seul bloc)");
        } else {
            lore.add(ChatColor.GRAY + "Zone 3x3 / Arbre entier");
        }
    }

    private void addStatioLytrasLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Vol créatif (pas de boost)");
        lore.add(ChatColor.RED + "Uniquement dans claims faction");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addCantalameLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Épée évolutive");
        lore.add(ChatColor.GRAY + "Gagne en puissance avec chaque kill unique");
        lore.add(ChatColor.GRAY + "Maximum : 100 kills uniques");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addRuneFortificationLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Rune de Fortification");
        lore.add(ChatColor.GRAY + "Clic-droit sur un block pour le fortifier");
        lore.add(ChatColor.GRAY + "Le block devient incassable par les non-factions");
        lore.add(ChatColor.GRAY + "Consomme 1 rune par block");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addVieLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Clic-droit pour gagner 1 vie");
        lore.add(ChatColor.GRAY + "Item consommable — disparaît après usage");
        lore.add(ChatColor.RED + "Non-craftable");
        lore.add(ChatColor.RED + "Récompense de grade mensuelle");
    }

    private void addPickantiseEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.DURABILITY, 5, true);
            meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.DURABILITY, 7, true);
            meta.addEnchant(Enchantment.DIG_SPEED, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            meta.addEnchant(Enchantment.DIG_SPEED, 10, true);
            // Netherite = infini (pas de durabilité)
        }
    }

    private void addCantaxeEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.DIG_SPEED, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            meta.addEnchant(Enchantment.DIG_SPEED, 10, true);
        }
    }

    private void addMultiCantoolEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.DURABILITY, 5, true);
            meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.DURABILITY, 7, true);
            meta.addEnchant(Enchantment.DIG_SPEED, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            // Netherite : pas de durabilité (infini)
            meta.addEnchant(Enchantment.DIG_SPEED, 10, true);
        }
    }

    public boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey("cantale", "custom_item"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    /**
     * Verify that a custom item is authentic (not faked via NBT editing).
     * Backward-compatible : si le hash est manquant (item legacy cr\u00e9\u00e9 avant
     * le syst\u00e8me anti-fake), on le migre automatiquement.
     * Si le hash est PR\u00c9SENT mais incorrect, on rejette (faux item).
     */
    public boolean isAuthenticCustomItem(ItemStack item) {
        if (!isCustomItem(item)) return false;
        String typeName = getCustomItemTypeRaw(item);
        if (typeName == null) return false;

        String storedHash = item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("cantale", AUTH_KEY),
                org.bukkit.persistence.PersistentDataType.STRING
        );

        // Legacy item : pas de hash → auto-migration
        if (storedHash == null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("cantale", AUTH_KEY),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    generateAuthHash(typeName)
            );
            item.setItemMeta(meta);
            return true;
        }

        String expectedHash = generateAuthHash(typeName);
        return expectedHash.equals(storedHash);
    }

    private String getCustomItemTypeRaw(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("cantale", "custom_item"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    public CustomItemType getCustomItemType(ItemStack item) {
        if (!isCustomItem(item)) return null;
        // Verify authenticity before returning type
        if (!isAuthenticCustomItem(item)) return null;
        String typeName = getCustomItemTypeRaw(item);
        try {
            return CustomItemType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Generate a SHA-256 hash from the type name + server secret.
     * This makes it impossible to forge custom items without knowing the secret.
     */
    private String generateAuthHash(String typeName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((typeName + ":" + SERVER_SECRET).getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Use first 16 chars for compactness
        } catch (NoSuchAlgorithmException e) {
            return "error";
        }
    }

    /**
     * Get the CustomModelData ID for resource pack texture overrides.
     */
    private int getCustomModelData(CustomItemType type) {
        return switch (type) {
            case PICKANTAXE_IRON_1X1 -> 1;
            case PICKANTAXE_IRON_3X3 -> 2;
            case PICKANTAXE_IRON_5X5 -> 3;
            case PICKANTAXE_IRON_7X7 -> 4;
            case PICKANTAXE_DIAMOND_1X1 -> 5;
            case PICKANTAXE_DIAMOND_3X3 -> 6;
            case PICKANTAXE_DIAMOND_5X5 -> 7;
            case PICKANTAXE_DIAMOND_7X7 -> 8;
            case PICKANTAXE_NETHERITE_1X1 -> 9;
            case PICKANTAXE_NETHERITE_3X3 -> 10;
            case PICKANTAXE_NETHERITE_5X5 -> 11;
            case PICKANTAXE_NETHERITE_7X7 -> 12;
            case CANTAXE_IRON -> 13;
            case CANTAXE_DIAMOND -> 14;
            case CANTAXE_NETHERITE -> 15;
            case MULTI_CANTOOL_IRON_1X1 -> 16;
            case MULTI_CANTOOL_IRON -> 17;
            case MULTI_CANTOOL_DIAMOND_1X1 -> 18;
            case MULTI_CANTOOL_DIAMOND -> 19;
            case MULTI_CANTOOL_NETHERITE_1X1 -> 20;
            case MULTI_CANTOOL_NETHERITE -> 21;
            case STATIO_LYTRAS -> 22;
            case CANTALAME -> 23;
            case RUNE_FORTIFICATION -> 24;
            case VIE -> 25;
        };
    }
}
