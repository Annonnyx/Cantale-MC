package fr.cantale.plugin.vote;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.privatechest.PrivateChest;
import fr.cantale.plugin.privatechest.PrivateChestDAO;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loot table aléatoire pondérée.
 *
 * Tiers :
 *  - COMMUN     60% : 100 Cantox OU stack ressources de base OU pommes dorées
 *  - RARE       25% : 250 Cantox OU Epée Fer enchantée OU Plastron Fer enchanté
 *  - EPIQUE     12% : 500 Cantox OU Epée Diamant enchantée OU Plastron Diamant enchanté
 *  - LEGENDAIRE  3% : 1500 Cantox OU Plastron Diamant OP OU Item Vie (utilisable 1x)
 *
 * Chance spéciale vie : 0.3% (3 / 1000) indépendante du tier.
 *
 * Bonus streak :
 *  - 7+ jours consécutifs  → tier garanti minimum RARE
 *  - 30+ jours consécutifs → tier garanti minimum EPIQUE
 */
public class VoteRewardManager {

    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final Random random = new Random();

    public VoteRewardManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.customItemManager = plugin.getCustomItemManager();
    }

    /**
     * Génère une récompense aléatoire en fonction de la streak du joueur.
     * @param streakDays nombre de jours consécutifs de votes (>= 1)
     */
    public VoteReward roll(int streakDays) {
        // Chance ultra-faible de vie indépendante du tier : 0.3% (3/1000)
        if (random.nextInt(1000) < 3) {
            return new VoteReward(VoteReward.Tier.LEGENDAIRE, 0, List.of(), "1 Vie");
        }

        VoteReward.Tier minTier = VoteReward.Tier.COMMUN;
        if (streakDays >= 30) minTier = VoteReward.Tier.EPIQUE;
        else if (streakDays >= 7) minTier = VoteReward.Tier.RARE;

        VoteReward.Tier tier = rollTier(minTier);
        return buildReward(tier);
    }

    /** Tirage pondéré : COMMUN 60 / RARE 25 / EPIQUE 12 / LEGENDAIRE 3. */
    private VoteReward.Tier rollTier(VoteReward.Tier minTier) {
        int r = random.nextInt(100); // 0-99
        VoteReward.Tier rolled;
        if (r < 60) rolled = VoteReward.Tier.COMMUN;
        else if (r < 85) rolled = VoteReward.Tier.RARE;
        else if (r < 97) rolled = VoteReward.Tier.EPIQUE;
        else rolled = VoteReward.Tier.LEGENDAIRE;

        // Force le tier minimum si la streak l'exige
        if (rolled.ordinal() < minTier.ordinal()) return minTier;
        return rolled;
    }

    private VoteReward buildReward(VoteReward.Tier tier) {
        return switch (tier) {
            case COMMUN -> rollCommun();
            case RARE -> rollRare();
            case EPIQUE -> rollEpique();
            case LEGENDAIRE -> rollLegendaire();
        };
    }

    private VoteReward rollCommun() {
        int r = random.nextInt(4);
        if (r == 0) {
            return new VoteReward(VoteReward.Tier.COMMUN, 100, List.of(), "100 Cantox");
        }
        if (r == 1) {
            Material[] mats = { Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT,
                    Material.OAK_LOG, Material.COBBLESTONE, Material.WHEAT, Material.BREAD };
            Material chosen = mats[random.nextInt(mats.length)];
            int qty = 16 + random.nextInt(17); // 16-32
            return new VoteReward(VoteReward.Tier.COMMUN, 0,
                    List.of(new ItemStack(chosen, qty)),
                    qty + "× " + prettyName(chosen));
        }
        // 50% pommes dorées (r == 2 ou 3)
        int qty = 2 + random.nextInt(4); // 2-5
        return new VoteReward(VoteReward.Tier.COMMUN, 0,
                List.of(new ItemStack(Material.GOLDEN_APPLE, qty)),
                qty + "× Pomme dorée");
    }

    private VoteReward rollRare() {
        int r = random.nextInt(3);
        if (r == 0) {
            return new VoteReward(VoteReward.Tier.RARE, 250, List.of(), "250 Cantox");
        } else if (r == 1) {
            ItemStack sword = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = sword.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DAMAGE_ALL, 2, true); // Sharpness II
                meta.addEnchant(Enchantment.DURABILITY, 1, true); // Unbreaking I
                sword.setItemMeta(meta);
            }
            return new VoteReward(VoteReward.Tier.RARE, 0, List.of(sword), "Epee en fer (Tranchant II)");
        } else {
            ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
            ItemMeta meta = chest.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2, true); // Protection II
                meta.addEnchant(Enchantment.DURABILITY, 1, true); // Unbreaking I
                chest.setItemMeta(meta);
            }
            return new VoteReward(VoteReward.Tier.RARE, 0, List.of(chest), "Plastron en fer (Protection II)");
        }
    }

    private VoteReward rollEpique() {
        int r = random.nextInt(3);
        if (r == 0) {
            return new VoteReward(VoteReward.Tier.EPIQUE, 500, List.of(), "500 Cantox");
        } else if (r == 1) {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = sword.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DAMAGE_ALL, 3, true); // Sharpness III
                meta.addEnchant(Enchantment.DURABILITY, 2, true); // Unbreaking II
                meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true); // Fire Aspect I
                sword.setItemMeta(meta);
            }
            return new VoteReward(VoteReward.Tier.EPIQUE, 0, List.of(sword), "Epee en diamant (Tranchant III, Fire Aspect)");
        } else {
            ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemMeta meta = chest.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 3, true); // Protection III
                meta.addEnchant(Enchantment.DURABILITY, 2, true); // Unbreaking II
                chest.setItemMeta(meta);
            }
            return new VoteReward(VoteReward.Tier.EPIQUE, 0, List.of(chest), "Plastron en diamant (Protection III)");
        }
    }

    private VoteReward rollLegendaire() {
        int r = random.nextInt(3);
        if (r == 0) {
            return new VoteReward(VoteReward.Tier.LEGENDAIRE, 1500, List.of(), "1 500 Cantox");
        } else if (r == 1) {
            ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemMeta meta = chest.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true); // Protection IV
                meta.addEnchant(Enchantment.DURABILITY, 3, true); // Unbreaking III
                meta.addEnchant(Enchantment.MENDING, 1, true); // Mending
                chest.setItemMeta(meta);
            }
            return new VoteReward(VoteReward.Tier.LEGENDAIRE, 0, List.of(chest), "Plastron en diamant (Protection IV, Mending)");
        } else {
            return new VoteReward(VoteReward.Tier.LEGENDAIRE, 0, List.of(), "1 Vie");
        }
    }

    /**
     * Délivre une récompense au joueur (en ligne uniquement).
     * Ordre : inventaire → coffre privé → drop au sol.
     */
    public void deliverTo(Player player, VoteReward reward) {
        if (reward.getCantox() > 0) {
            plugin.getEconomyManager().addBalance(player.getUniqueId(), reward.getCantox());
        }

        List<ItemStack> allItems = new ArrayList<>();

        // Gestion spéciale : récompense "1 Vie" → donne l'item physique custom
        if ("1 Vie".equals(reward.getSummary())) {
            allItems.add(customItemManager.createCustomItem(CustomItemType.VIE));
        }
        allItems.addAll(reward.getItems());

        for (ItemStack item : allItems) {
            if (item == null) continue;
            // 1. Essayer l'inventaire
            var leftoverInv = player.getInventory().addItem(item.clone());
            if (leftoverInv.isEmpty()) continue;

            // 2. Inventaire plein → essayer le coffre privé
            boolean pcSuccess = false;
            try {
                PrivateChestDAO dao = new PrivateChestDAO(plugin);
                PrivateChest pc = dao.loadPrivateChest(player.getUniqueId());
                var leftoverPc = pc.getInventory().addItem(leftoverInv.values().toArray(new ItemStack[0]));
                if (leftoverPc.isEmpty()) {
                    dao.savePrivateChest(player.getUniqueId(), pc.getContents());
                    pcSuccess = true;
                    player.sendMessage("§8[§6Vote§8] §7Item envoyé dans votre §eCoffre Privé §7(faute de place).");
                } else {
                    // PC plein aussi → save ce qui a tenu, drop le reste
                    dao.savePrivateChest(player.getUniqueId(), pc.getContents());
                    leftoverInv = leftoverPc;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Vote] Erreur coffre privé pour " + player.getName() + ": " + e.getMessage());
            }

            // 3. Drop au sol si PC aussi plein ou erreur
            if (!pcSuccess) {
                for (ItemStack drop : leftoverInv.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    private String prettyName(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Génère une copie déterministe de la récompense (utilisé pour les votes pending offline rejouer). */
    public List<ItemStack> reconstructItemsFromSummary(String summary) {
        // Approche pragmatique : on stocke le summary, on regénère ce qui est possible
        // Pour l'instant on délivre seulement le Cantox via summary parsing simple si applicable
        List<ItemStack> items = new ArrayList<>();
        if (summary == null) return items;
        // Si le summary commence par un nombre + " Cantox", on le délivrera via cantox uniquement (pas d'item)
        return items;
    }
}
