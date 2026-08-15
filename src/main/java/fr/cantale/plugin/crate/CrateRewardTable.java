package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tables de butin des caisses Cantale.
 */
public class CrateRewardTable {

    private final CantalePlugin plugin;
    private final CustomItemManager itemManager;
    private final Random random = ThreadLocalRandom.current();

    public CrateRewardTable(CantalePlugin plugin, CustomItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    /**
     * Tire une récompense dans la table de la rareté demandée.
     */
    public CrateReward roll(CrateType type) {
        CrateReward spawner = tryRollSpawner(type);
        if (spawner != null) return spawner;

        CrateReward rune = tryRollRune(type);
        if (rune != null) return rune;

        List<CrateReward> table = getTable(type);
        double total = table.stream().mapToDouble(CrateReward::weight).sum();
        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (CrateReward reward : table) {
            cumulative += reward.weight();
            if (roll <= cumulative) return reward;
        }
        return table.get(table.size() - 1);
    }

    /**
     * Chance extra (tables existantes inchangées) : Rare 5 %, Épique 3 %, Mythique/Légendaire 1,5 %.
     */
    private CrateReward tryRollSpawner(CrateType type) {
        if (plugin == null || plugin.getSpawnerManager() == null) return null;
        var spawners = plugin.getSpawnerManager();
        double chance = spawners.crateChancePercent(type);
        if (chance <= 0) return null;
        if (random.nextDouble() * 100.0 >= chance) return null;
        ItemStack item = spawners.rollCrateSpawner(type);
        if (item == null) return null;
        return new CrateReward("Spawner", 0, 0, List.of(item), 0, "§eTu as obtenu un spawner !");
    }

    /**
     * Chance extra (tables existantes inchangées) : Rare 2 %, Épique 4 %, Mythique 6,5 %.
     * Vote et Légendaire (ticket Discord) : 0.
     */
    private CrateReward tryRollRune(CrateType type) {
        double chance = runeCrateChancePercent(type);
        if (chance <= 0) return null;
        if (random.nextDouble() * 100.0 >= chance) return null;
        ItemStack rune = itemManager.createCustomItem(CustomItemType.RUNE_FORTIFICATION);
        return new CrateReward("Rune de Fortification", 0, 0, List.of(rune), 0,
                "§bTu as obtenu une Rune de Fortification !");
    }

    private double runeCrateChancePercent(CrateType type) {
        return switch (type) {
            case VOTE -> 0;
            case RARE -> 0; // runes désormais réservées aux caisses Épique et plus
            case EPIC -> plugin.getConfig().getDouble("runes.crate.epic-chance-percent", 2.0);
            case MYTHIC -> plugin.getConfig().getDouble("runes.crate.mythic-chance-percent", 4.0);
            case LEGENDARY -> plugin.getConfig().getDouble("runes.crate.legendary-chance-percent", 6.5);
        };
    }

    /**
     * Détermine si une caisse monte d'un cran en rareté.
     * Retourne la rareté immédiatement supérieure, ou null si pas d'ascension.
     * La grande ascension Vote → Légendaire se fait étape par étape.
     */
    public CrateType rollUpgrade(CrateType type) {
        double roll = random.nextDouble() * 100.0;
        return switch (type) {
            case VOTE -> {
                // Vote ne peut monter que d'un seul cran : Rare
                if (roll < 4.0) yield CrateType.RARE;   // Cadeau du Roi → Trésor Public
                yield null;
            }
            case RARE -> {
                if (roll < 5.0) yield CrateType.EPIC;   // Trésor Public → Médaille du Tournoi
                yield null;
            }
            case EPIC -> {
                if (roll < 5.0) yield CrateType.MYTHIC; // Médaille du Tournoi → Pièce Mythique
                yield null;
            }
            case MYTHIC -> {
                if (roll < 4.0) yield CrateType.LEGENDARY; // Pièce Mythique → Ticket Légendaire
                yield null;
            }
            case LEGENDARY -> null;
        };
    }

    private List<CrateReward> getTable(CrateType type) {
        return switch (type) {
            case VOTE -> voteTable();
            case RARE -> rareTable();
            case EPIC -> epicTable();
            case MYTHIC -> mythicTable();
            case LEGENDARY -> legendaryTable();
        };
    }

    // ─── Vote (Cadeau du Roi) ───
    private List<CrateReward> voteTable() {
        List<CrateReward> table = new ArrayList<>();
        table.add(reward("Bourse Royale", 25, randomCantox(100, 250), List.of(), 0));
        table.add(reward("Pochette de Cuivre", 25, randomCantox(250, 500), foodStack(), 0));
        table.add(reward("Sceau d'Or", 20, randomCantox(500, 750), oreStack(), 0));
        table.add(reward("Faveur du Roi", 10, randomCantox(750, 1000), List.of(new ItemStack(Material.GOLDEN_APPLE, 2)), 0));
        table.add(reward("Coffre de Lames", 5, 1500, List.of(itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_IRON_1X1)), 0));
        table.add(reward("Cadeau Exceptionnel", 1, 1000, List.of(itemManager.createCustomItem(CustomItemType.VIE)), 1, "§5+1 Vie et 1000 Cantox !"));
        table.add(reward("Chance Légendaire", 0.5, 2500, List.of(itemManager.createCustomItem(CustomItemType.CANTALAME)), 0, "§6Une Cantalame t'obéit !"));
        table.add(reward("Livre Mending", 8, 0, List.of(enchantedBook(Enchantment.MENDING, 1)), 0, "§bLivre enchanté : Mending !"));
        table.add(reward("Livre Silk Touch", 6, 0, List.of(enchantedBook(Enchantment.SILK_TOUCH, 1)), 0, "§bLivre enchanté : Silk Touch !"));
        return table;
    }

    // ─── Rare (Trésor Public) ───
    private List<CrateReward> rareTable() {
        List<CrateReward> table = new ArrayList<>();
        table.add(reward("Bourse de Marchand", 20, randomCantox(500, 1500), foodStack(), 0));
        table.add(reward("Coffre de Lames", 18, 2000, List.of(itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_IRON_1X1)), 0));
        table.add(reward("Pic du Mineur", 15, 2500, List.of(itemManager.createCustomItem(CustomItemType.PICKANTAXE_IRON_1X1)), 0));
        table.add(reward("Minerai Brut", 12, 1000, bigOreStack(), 0));
        table.add(reward("Bénédiction Rare", 10, 5000, List.of(itemManager.createCustomItem(CustomItemType.CANTAXE_IRON)), 0));
        table.add(reward("Trésor du Peuple", 6, 8000, List.of(itemManager.createCustomItem(CustomItemType.VIE)), 1, "§5+1 Vie et 8000 Cantox !"));
        table.add(reward("Fortune Oubliée", 3, 12000, List.of(itemManager.createCustomItem(CustomItemType.CANTALAME)), 0, "§6Une Cantalame t'obéit !"));
        table.add(reward("Légende Rapprochée", 1, 15000, List.of(
                itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_DIAMOND_1X1),
                itemManager.createCustomItem(CustomItemType.VIE)
        ), 1, "§5Multi-Cantool Diamant + 1 Vie + 15000 Cantox !"));
        table.add(reward("Livre Mending", 6, 0, List.of(enchantedBook(Enchantment.MENDING, 1)), 0, "§bLivre enchanté : Mending !"));
        table.add(reward("Livre Silk Touch", 5, 0, List.of(enchantedBook(Enchantment.SILK_TOUCH, 1)), 0, "§bLivre enchanté : Silk Touch !"));
        return table;
    }

    // ─── Épique (Médaille sur l'autel) ───
    private List<CrateReward> epicTable() {
        List<CrateReward> table = new ArrayList<>();
        table.add(reward("Prix d'Honneur", 20, randomCantox(1000, 3000), bigOreStack(), 0));
        table.add(reward("Mérite du Combattant", 20, 4000, List.of(itemManager.createCustomItem(CustomItemType.CANTAXE_IRON)), 0));
        table.add(reward("Pic de Maître", 18, 8000, List.of(itemManager.createCustomItem(CustomItemType.PICKANTAXE_DIAMOND_1X1)), 0));
        table.add(reward("Multi-Outil Épique", 15, 10000, List.of(itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_DIAMOND_1X1)), 0));
        table.add(reward("Bénédiction Épique", 12, 15000, List.of(
                itemManager.createCustomItem(CustomItemType.CANTAXE_DIAMOND),
                new ItemStack(Material.DIAMOND, 8 + random.nextInt(9))
        ), 0));
        table.add(reward("Trophée du Champion", 8, 20000, List.of(
                itemManager.createCustomItem(CustomItemType.VIE),
                new ItemStack(Material.GOLDEN_APPLE, 4)
        ), 1, "§5+1 Vie, pommes dorées et 20000 Cantox !"));
        table.add(reward("Gloire du Tournoi", 5, 30000, List.of(
                itemManager.createCustomItem(CustomItemType.CANTALAME),
                new ItemStack(Material.GOLDEN_APPLE, 6)
        ), 0, "§6Cantalame + pommes dorées + 30000 Cantox !"));
        table.add(reward("Victoire Épique", 2, 50000, List.of(
                itemManager.createCustomItem(CustomItemType.CANTALAME),
                itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_NETHERITE_1X1),
                itemManager.createCustomItem(CustomItemType.VIE)
        ), 1, "§6Cantalame + Multi-Cantool Netherite + 1 Vie + 50000 Cantox !"));
        table.add(reward("Livre Mending", 5, 0, List.of(enchantedBook(Enchantment.MENDING, 1)), 0, "§bLivre enchanté : Mending !"));
        table.add(reward("Livre Silk Touch", 4, 0, List.of(enchantedBook(Enchantment.SILK_TOUCH, 1)), 0, "§bLivre enchanté : Silk Touch !"));
        return table;
    }

    // ─── Mythique (Pièce jetée dans la fontaine) ───
    private List<CrateReward> mythicTable() {
        List<CrateReward> table = new ArrayList<>();
        table.add(reward("Vœu Mineur", 20, randomCantox(3000, 8000), bigOreStack(), 0));
        table.add(reward("Vœu d'Abondance", 20, 12000, List.of(
                new ItemStack(Material.DIAMOND, 16 + random.nextInt(17)),
                new ItemStack(Material.GOLDEN_APPLE, 4 + random.nextInt(5))
        ), 0));
        table.add(reward("Vœu de Puissance", 18, 20000, List.of(
                itemManager.createCustomItem(CustomItemType.PICKANTAXE_DIAMOND_3X3)
        ), 0));
        table.add(reward("Vœu de Vol", 17, 35000, List.of(
                itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_NETHERITE_1X1),
                itemManager.createCustomItem(CustomItemType.CANTAXE_DIAMOND)
        ), 0));
        table.add(reward("Vœu Légendaire", 15, 60000, List.of(
                itemManager.createCustomItem(CustomItemType.CANTALAME),
                itemManager.createCustomItem(CustomItemType.CANTAXE_NETHERITE),
                itemManager.createCustomItem(CustomItemType.VIE)
        ), 1, "§6Cantalame + Cantaxe Netherite + 1 Vie + 60000 Cantox !"));
        table.add(reward("Vœu Suprême", 10, 100000, List.of(
                itemManager.createCustomItem(CustomItemType.CANTALAME),
                itemManager.createCustomItem(CustomItemType.MULTI_CANTOOL_NETHERITE_1X1),
                itemManager.createCustomItem(CustomItemType.PICKANTAXE_NETHERITE_5X5),
                itemManager.createCustomItem(CustomItemType.VIE),
                itemManager.createCustomItem(CustomItemType.VIE)
        ), 2, "§6§lCANTALAME + MULTI-CANTOOL NETHERITE + PICKANTAXE NETHERITE 5x5 + 2 VIES + 100000 CANTOX !"));
        table.add(reward("Livre Mending", 4, 0, List.of(enchantedBook(Enchantment.MENDING, 1)), 0, "§bLivre enchanté : Mending !"));
        table.add(reward("Livre Silk Touch", 3, 0, List.of(enchantedBook(Enchantment.SILK_TOUCH, 1)), 0, "§bLivre enchanté : Silk Touch !"));
        return table;
    }

    // ─── Légendaire (Ticket Staff) ───
    private List<CrateReward> legendaryTable() {
        List<CrateReward> table = new ArrayList<>();
        table.add(reward("Audience Royale", 100, 0, List.of(), 0, "§6Ouvre un ticket sur Discord pour recevoir l'item de ton choix !"));
        table.add(reward("Livre Mending", 3, 0, List.of(enchantedBook(Enchantment.MENDING, 1)), 0, "§bLivre enchanté : Mending !"));
        table.add(reward("Livre Silk Touch", 2, 0, List.of(enchantedBook(Enchantment.SILK_TOUCH, 1)), 0, "§bLivre enchanté : Silk Touch !"));
        return table;
    }

    // ─── Helpers ───
    private CrateReward reward(String name, double weight, int cantox, List<ItemStack> items, int lives) {
        return new CrateReward(name, weight, cantox, items, lives, null);
    }

    private CrateReward reward(String name, double weight, int cantox, List<ItemStack> items, int lives, String message) {
        return new CrateReward(name, weight, cantox, items, lives, message);
    }

    private ItemStack enchantedBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        var meta = book.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchantment, level, true);
            book.setItemMeta(storageMeta);
        }
        return book;
    }

    private int randomCantox(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private List<ItemStack> foodStack() {
        return List.of(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(4)));
    }

    private List<ItemStack> oreStack() {
        Material[] ores = { Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.COPPER_INGOT, Material.REDSTONE };
        return List.of(new ItemStack(ores[random.nextInt(ores.length)], 4 + random.nextInt(13)));
    }

    private List<ItemStack> bigOreStack() {
        Material[] ores = { Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.COPPER_INGOT, Material.REDSTONE_BLOCK };
        return List.of(new ItemStack(ores[random.nextInt(ores.length)], 16 + random.nextInt(25)));
    }

    private List<ItemStack> diamondGear() {
        return List.of(
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.DIAMOND_PICKAXE),
                new ItemStack(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.DIAMOND_HELMET)
        );
    }
}
