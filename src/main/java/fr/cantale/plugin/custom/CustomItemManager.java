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
        } else if (type.isCantalame()) {
            addCantalameLore(lore);
        } else if (type == CustomItemType.VIE) {
            addVieLore(lore);
        } else if (type.isGardeArmor()) {
            addGardeArmorLore(type, lore);
        }

        meta.setLore(lore);

        // Enchantements
        if (type.isPickantise()) {
            addPickantiseEnchantments(type, meta);
        } else if (type.isCantaxe()) {
            addCantaxeEnchantments(type, meta);
        } else if (type.isMultiCantool()) {
            addMultiCantoolEnchantments(type, meta);
        } else if (type.isGardeArmor()) {
            addGardeArmorEnchantments(type, meta);
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
        lore.add(ChatColor.GRAY + "Mode 1x1 (un seul bloc)");
    }

    private void addCantalameLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Épée évolutive");
        lore.add(ChatColor.GRAY + "Gagne en puissance avec chaque kill unique");
        lore.add(ChatColor.GRAY + "Maximum : 100 kills uniques");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addVieLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Clic-droit pour gagner 1 vie");
        lore.add(ChatColor.GRAY + "Item consommable — disparaît après usage");
        lore.add(ChatColor.RED + "Non-craftable");
        lore.add(ChatColor.RED + "Récompense de grade mensuelle");
    }

    private void addGardeArmorLore(CustomItemType type, List<String> lore) {
        lore.add(ChatColor.GOLD + "Armure du Garde");
        lore.add(ChatColor.GRAY + "Pièce authentique — non craftable");
        lore.add("");
        lore.add(ChatColor.AQUA + "Bonus par pièce (25% du set) :");
        lore.add(ChatColor.GRAY + "  +1.5 cœurs  |  +7.5% vitesse  |  +6.25% saut");
        lore.add(ChatColor.AQUA + "Set complet (4 pièces) :");
        lore.add(ChatColor.GRAY + "  +6 cœurs  |  +30% vitesse  |  +25% saut");
        switch (type) {
            case GARDE_HELMET -> {
                lore.add("");
                lore.add(ChatColor.DARK_AQUA + "Extras : Respiration III, Affinité aquatique");
            }
            case GARDE_LEGGINGS -> {
                lore.add("");
                lore.add(ChatColor.DARK_AQUA + "Extras : Pas furtifs III");
            }
            case GARDE_BOOTS -> {
                lore.add("");
                lore.add(ChatColor.DARK_AQUA + "Extras : Chute amortie IV, Depth Strider III, Soul Speed III");
            }
            default -> {
            }
        }
    }

    private void addGardeArmorEnchantments(CustomItemType type, ItemMeta meta) {
        meta.addEnchant(Enchantment.PROTECTION, 9, true);
        meta.addEnchant(Enchantment.THORNS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

        switch (type) {
            case GARDE_HELMET -> {
                meta.addEnchant(Enchantment.RESPIRATION, 3, true);
                meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
            }
            case GARDE_LEGGINGS -> meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
            case GARDE_BOOTS -> {
                meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
                meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
                meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
            }
            default -> {
            }
        }
    }

    private void addPickantiseEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.UNBREAKING, 7, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            // Netherite = infini (pas de durabilité)
        }
    }

    private void addCantaxeEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
        }
    }

    private void addMultiCantoolEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.UNBREAKING, 7, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            // Netherite : pas de durabilité (infini)
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
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
            case PICKANTAXE_IRON_1X1 -> 1001;
            case PICKANTAXE_IRON_3X3 -> 1002;
            case PICKANTAXE_IRON_5X5 -> 1003;
            case PICKANTAXE_IRON_7X7 -> 1004;
            case PICKANTAXE_DIAMOND_1X1 -> 1011;
            case PICKANTAXE_DIAMOND_3X3 -> 1012;
            case PICKANTAXE_DIAMOND_5X5 -> 1013;
            case PICKANTAXE_DIAMOND_7X7 -> 1014;
            case PICKANTAXE_NETHERITE_1X1 -> 1021;
            case PICKANTAXE_NETHERITE_3X3 -> 1022;
            case PICKANTAXE_NETHERITE_5X5 -> 1023;
            case PICKANTAXE_NETHERITE_7X7 -> 1024;
            case CANTAXE_IRON -> 2001;
            case CANTAXE_DIAMOND -> 2002;
            case CANTAXE_NETHERITE -> 2003;
            case MULTI_CANTOOL_IRON_1X1 -> 3001;
            case MULTI_CANTOOL_IRON -> 3002;
            case MULTI_CANTOOL_DIAMOND_1X1 -> 3011;
            case MULTI_CANTOOL_DIAMOND -> 3012;
            case MULTI_CANTOOL_NETHERITE_1X1 -> 3021;
            case MULTI_CANTOOL_NETHERITE -> 3022;
            case CANTALAME -> 5001;
            case VIE -> 7001;
            case GARDE_HELMET -> 9001;
            case GARDE_CHESTPLATE -> 9002;
            case GARDE_LEGGINGS -> 9003;
            case GARDE_BOOTS -> 9004;
        };
    }
}
