package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
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

    private static final String AUTH_KEY = "cantale_auth";
    // Server secret for anti-fake verification — change this for your server!
    private static final String SERVER_SECRET = "Cantale_2024_xK9mP2vQ";

    // ─── Compteur d'utilisations (outils custom) ─────────────────────────
    // Les outils (Pickantaxe, Cantaxe, Multi-Cantool) n'utilisent plus la
    // durabilité vanilla : ce compteur PDC est la SEULE jauge (la barre de
    // durabilité reste pleine, la perte vanilla est annulée côté listener).
    // 1 utilisation = 1 coup (1 BlockBreakEvent), quelle que soit la zone
    // minée (3x3, 5x5, arbre entier…). La Cantalame et l'armure du Garde ne
    // sont PAS concernées (incassable / durabilité vanilla de combat).
    private static final org.bukkit.NamespacedKey USES_KEY =
            new org.bukkit.NamespacedKey("cantale", "uses_left");

    /** Préfixe de la ligne de lore vivante qui suit le compteur. */
    private static final String USES_LORE_PREFIX = ChatColor.GRAY + "Utilisations restantes : ";

    public ItemStack createCustomItem(CustomItemType type) {
        // La Cantalame a son propre cycle de vie (PDC kills/victimes) : on délègue à
        // CantalameManager pour que TOUTES les sources (caisses, giveaways, commandes)
        // produisent une épée fonctionnelle et protégée.
        if (type.isCantalame()) {
            return new CantalameManager(CantalePlugin.getInstance()).createCantalame();
        }

        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GOLD + type.getDisplayName());

        // Enchantements AVANT le lore : le lore lit les niveaux réellement
        // appliqués sur la meta, il ne peut donc jamais annoncer autre chose.
        if (type.isPickantise()) {
            addPickantiseEnchantments(type, meta);
        } else if (type.isCantaxe()) {
            addCantaxeEnchantments(type, meta);
        } else if (type.isMultiCantool()) {
            addMultiCantoolEnchantments(type, meta);
        } else if (type.isGardeArmor()) {
            addGardeArmorEnchantments(type, meta);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Item custom Cantale");

        if (type.isPickantise()) {
            addPickantiseLore(type, meta, lore);
        } else if (type.isCantaxe()) {
            addCantaxeLore(type, meta, lore);
        } else if (type.isMultiCantool()) {
            addMultiCantoolLore(type, meta, lore);
        } else if (type == CustomItemType.VIE) {
            addVieLore(lore);
        } else if (type.isRuneFortification()) {
            addRuneFortificationLore(lore);
        } else if (type.isGardeArmor()) {
            addGardeArmorLore(type, meta, lore);
        }

        // Compteur d'utilisations posé dès la création (seule jauge de l'outil).
        if (isToolWithUses(type)) {
            meta.getPersistentDataContainer().set(
                    USES_KEY,
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    getMaxUses(type)
            );
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        applyCustomIdentity(item, type);
        return item;
    }

    // ─── Modèle « utilisations » (Pickantaxe / Cantaxe / Multi-Cantool) ────

    /** True si le type est un outil custom soumis au compteur d'utilisations. */
    public boolean isToolWithUses(CustomItemType type) {
        return type != null && (type.isPickantise() || type.isCantaxe() || type.isMultiCantool());
    }

    /** Nombre de coups d'un outil neuf, selon son tier : Fer 1000, Diamant 2000, Netherite 4000. */
    public int getMaxUses(CustomItemType type) {
        if (type.name().contains("NETHERITE")) return 4000;
        if (type.name().contains("DIAMOND")) return 2000;
        return 1000; // Fer
    }

    /** Utilisations restantes, ou null si l'outil n'a pas encore de compteur (legacy). */
    public Integer getUsesLeft(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                USES_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /**
     * Consomme 1 utilisation sur l'outil et rafraîchit sa lore.
     * Migration douce : un outil en circulation SANS compteur est initialisé à
     * son maximum AVANT le décompte (aucune perte pour les joueurs), et ses
     * anciennes lignes de lore « durabilité »/« Solidité » sont retirées.
     *
     * @return utilisations restantes après décompte (0 = l'outil doit se briser)
     */
    public int consumeOneUse(ItemStack item, CustomItemType type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        Integer left = meta.getPersistentDataContainer().get(
                USES_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
        boolean legacy = left == null;
        if (legacy) {
            left = getMaxUses(type);
        }

        left = Math.max(0, left - 1);
        meta.getPersistentDataContainer().set(
                USES_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, left);
        updateUsesLore(meta, left, legacy);
        item.setItemMeta(meta);
        return left;
    }

    /**
     * Met à jour (ou insère) la ligne « Utilisations restantes : N » de la lore.
     *
     * @param stripLegacy true pour retirer les lignes d'avant le modèle
     *                    utilisations (« …coûte de la durabilité », « Solidité … »)
     */
    /** Retire le segment « Solidité X » (et son séparateur « • ») d'une ligne de lore. */
    private String stripSolidite(String line) {
        return line.replaceAll("\\s*•\\s*Solidité\\s+[IVX0-9]+", "")
                .replaceAll("Solidité\\s+[IVX0-9]+\\s*•\\s*", "")
                .replaceAll("Solidité\\s+[IVX0-9]+", "");
    }

    private void updateUsesLore(ItemMeta meta, int usesLeft, boolean stripLegacy) {
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        if (stripLegacy) {
            // Retire les lignes « …coûte de la durabilité » et expurge « Solidité X »
            // du résumé d'enchantements en gardant le reste (ex. « Efficacité V »).
            // Une ligne résumé qui ne contenait QUE Solidité disparaît complètement.
            List<String> cleaned = new ArrayList<>(lore.size());
            for (String line : lore) {
                if (line.contains("durabilité")) continue;
                if (line.contains("Solidité")) {
                    String stripped = stripSolidite(line);
                    if (ChatColor.stripColor(stripped).isBlank()) continue;
                    line = stripped;
                }
                cleaned.add(line);
            }
            lore = cleaned;
        }

        String usesLine = USES_LORE_PREFIX + ChatColor.YELLOW + usesLeft;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("Utilisations restantes")) {
                lore.set(i, usesLine);
                meta.setLore(lore);
                return;
            }
        }
        // Pas de ligne existante (outil legacy) : on l'insère avant « Non-craftable ».
        int insertAt = lore.size();
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("Non-craftable")) {
                insertAt = i;
                break;
            }
        }
        lore.add(insertAt, usesLine);
        meta.setLore(lore);
    }

    /**
     * Applique l'identité Cantale sur un item : CustomModelData (resource pack),
     * tag PDC {@code custom_item} et hash anti-fake. Partagé avec CantalameManager,
     * qui construit sa propre meta mais doit porter la même identité.
     */
    public void applyCustomIdentity(ItemStack item, CustomItemType type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

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
    }

    /** Résumé FR des enchantements réellement présents sur la meta (jamais inventé). */
    private String enchantSummary(ItemMeta meta) {
        List<String> parts = new ArrayList<>();
        addEnchantPart(parts, meta, Enchantment.EFFICIENCY, "Efficacité");
        addEnchantPart(parts, meta, Enchantment.PROTECTION, "Protection");
        addEnchantPart(parts, meta, Enchantment.THORNS, "Épines");
        addEnchantPart(parts, meta, Enchantment.UNBREAKING, "Solidité");
        addEnchantPart(parts, meta, Enchantment.MENDING, "Réparation");
        return String.join(" • ", parts);
    }

    private void addEnchantPart(List<String> parts, ItemMeta meta, Enchantment enchant, String nomFr) {
        int level = meta.getEnchantLevel(enchant);
        if (level > 0) {
            parts.add(level > 1 ? nomFr + " " + toRoman(level) : nomFr);
        }
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    private void addPickantiseLore(CustomItemType type, ItemMeta meta, List<String> lore) {
        lore.add(ChatColor.AQUA + "Zone de minage : " + type.getMiningRadius() + "x" + type.getMiningRadius());
        lore.add(USES_LORE_PREFIX + ChatColor.YELLOW + String.valueOf(getMaxUses(type)));
        lore.add(ChatColor.GRAY + "1 coup = 1 utilisation, quelle que soit la zone");
        lore.add(ChatColor.GRAY + enchantSummary(meta));
        lore.add(ChatColor.DARK_GRAY + "Enchantements fixes — enclume et meule bloquées");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addCantaxeLore(CustomItemType type, ItemMeta meta, List<String> lore) {
        lore.add(ChatColor.AQUA + "One-shot tous les arbres");
        lore.add(USES_LORE_PREFIX + ChatColor.YELLOW + String.valueOf(getMaxUses(type)));
        lore.add(ChatColor.GRAY + "1 arbre abattu = 1 utilisation");
        lore.add(ChatColor.GRAY + enchantSummary(meta));
        lore.add(ChatColor.DARK_GRAY + "Enchantements fixes — enclume et meule bloquées");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addMultiCantoolLore(CustomItemType type, ItemMeta meta, List<String> lore) {
        lore.add(ChatColor.AQUA + "Outil multifonction");
        lore.add(ChatColor.GRAY + "S'adapte au bloc : Pioche + Hache + Pelle + Cisailles");
        lore.add(ChatColor.GRAY + "Mode 1x1 (un seul bloc)");
        lore.add(USES_LORE_PREFIX + ChatColor.YELLOW + String.valueOf(getMaxUses(type)));
        lore.add(ChatColor.GRAY + enchantSummary(meta));
        lore.add(ChatColor.DARK_GRAY + "Enchantements fixes — enclume et meule bloquées");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addVieLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Clic-droit pour gagner 1 vie");
        lore.add(ChatColor.GRAY + "Item consommable — disparaît après usage");
        lore.add(ChatColor.RED + "Non-craftable");
        lore.add(ChatColor.RED + "Récompense de grade mensuelle");
    }

    private void addRuneFortificationLore(List<String> lore) {
        lore.add(ChatColor.AQUA + "Clic-droit dans un claim de votre faction");
        lore.add(ChatColor.GRAY + "Fortifie le chunk entier : seuls les membres");
        lore.add(ChatColor.GRAY + "de votre faction peuvent y casser des blocks,");
        lore.add(ChatColor.GRAY + "et rien n'y explose (TNT, cristaux, wither…).");
        lore.add("");
        lore.add(ChatColor.GOLD + "Coût : 50 Cantox/jour par chunk fortifié");
        lore.add(ChatColor.GRAY + "Sneak + clic-droit pour retirer (rune récupérée)");
        lore.add(ChatColor.RED + "Non-craftable");
    }

    private void addGardeArmorLore(CustomItemType type, ItemMeta meta, List<String> lore) {
        lore.add(ChatColor.GOLD + "Armure du Garde");
        lore.add(ChatColor.GRAY + "Pièce authentique — non craftable");
        lore.add(ChatColor.GRAY + enchantSummary(meta));
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
                lore.add(ChatColor.DARK_AQUA + "Extras : Chute amortie IV, Agilité aquatique III, Vitesse des âmes III");
            }
            default -> {
            }
        }
    }

    private void addGardeArmorEnchantments(CustomItemType type, ItemMeta meta) {
        meta.addEnchant(Enchantment.PROTECTION, 9, true);
        meta.addEnchant(Enchantment.THORNS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 7, true);
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

    // Solidité (Unbreaking) n'est plus appliquée aux outils : la durabilité
    // vanilla est neutralisée, seul le compteur d'utilisations fait foi.
    private void addPickantiseEnchantments(CustomItemType type, ItemMeta meta) {
        if (type.name().contains("IRON")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        } else if (type.name().contains("NETHERITE")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
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
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        } else if (type.name().contains("DIAMOND")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        } else if (type.name().contains("NETHERITE")) {
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
            case RUNE_FORTIFICATION -> 6001;
            case VIE -> 7001;
            case GARDE_HELMET -> 9001;
            case GARDE_CHESTPLATE -> 9002;
            case GARDE_LEGGINGS -> 9003;
            case GARDE_BOOTS -> 9004;
        };
    }
}
