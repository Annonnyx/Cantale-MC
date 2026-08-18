package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CantalameManager {

    private final CantalePlugin plugin;
    private final NamespacedKey cantalameKey;
    private final NamespacedKey killsKey;
    private final NamespacedKey victimsKey;

    private final NamespacedKey dailyKillsKey;

    public CantalameManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.cantalameKey = new NamespacedKey(plugin, "cantalame");
        this.killsKey = new NamespacedKey(plugin, "cantalame_kills");
        this.victimsKey = new NamespacedKey(plugin, "cantalame_victims");
        this.dailyKillsKey = new NamespacedKey(plugin, "cantalame_daily");
    }

    private Enchantment getEnchantment(String key) {
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    public int getKillCount(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (!container.has(cantalameKey, PersistentDataType.STRING)) return 0;
        
        return container.getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    public Set<UUID> getVictims(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return new HashSet<>();
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (!container.has(cantalameKey, PersistentDataType.STRING)) return new HashSet<>();
        
        String victimsString = container.getOrDefault(victimsKey, PersistentDataType.STRING, "");
        Set<UUID> victims = new HashSet<>();
        
        if (!victimsString.isEmpty()) {
            for (String uuidStr : victimsString.split(",")) {
                try {
                    victims.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        return victims;
    }

    public void addKill(ItemStack item, Player victim) {
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (!container.has(cantalameKey, PersistentDataType.STRING)) return;
        
        String today = java.time.LocalDate.now().toString();
        String dailyData = container.getOrDefault(dailyKillsKey, PersistentDataType.STRING, "");
        
        String storedDate = "";
        Map<UUID, Integer> dailyCounts = new HashMap<>();
        if (!dailyData.isEmpty() && dailyData.contains("|")) {
            String[] parts = dailyData.split("\\|", 2);
            storedDate = parts[0];
            if (storedDate.equals(today) && parts.length > 1) {
                for (String entry : parts[1].split(";")) {
                    if (entry.contains(":")) {
                        String[] kv = entry.split(":");
                        try {
                            dailyCounts.put(UUID.fromString(kv[0]), Integer.parseInt(kv[1]));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        
        int currentDaily = dailyCounts.getOrDefault(victim.getUniqueId(), 0);
        if (currentDaily >= 3) {
            // Déjà tué 3 fois aujourd'hui avec cette épée : pas d'incrément
            return;
        }
        
        dailyCounts.put(victim.getUniqueId(), currentDaily + 1);
        int killCount = Math.min(100, getKillCount(item) + 1);
        
        // Sauvegarder les données quotidiennes
        StringBuilder sb = new StringBuilder(today).append("|");
        dailyCounts.forEach((u, c) -> sb.append(u.toString()).append(":").append(c).append(";"));
        
        container.set(killsKey, PersistentDataType.INTEGER, killCount);
        container.set(dailyKillsKey, PersistentDataType.STRING, sb.toString());
        
        item.setItemMeta(meta);
        
        // Appliquer les améliorations
        applyUpgrades(item, killCount);
    }

    public void applyUpgrades(ItemStack item, int killCount) {
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        
        // Calculer les palliers
        int tier = Math.min(killCount, 100);
        
        // Effacer tous les enchantements existants
        for (Enchantment ench : meta.getEnchants().keySet()) {
            meta.removeEnchant(ench);
        }
        
        // Appliquer les améliorations en fonction du pallier
        if (tier >= 5) {
            meta.addEnchant(getEnchantment("sharpness"), 1, true);
        }
        if (tier >= 10) {
            meta.addEnchant(getEnchantment("sharpness"), 2, true);
        }
        if (tier >= 15) {
            meta.addEnchant(getEnchantment("sharpness"), 3, true);
            meta.addEnchant(getEnchantment("looting"), 1, true);
        }
        if (tier >= 20) {
            meta.addEnchant(getEnchantment("sharpness"), 4, true);
            meta.addEnchant(getEnchantment("looting"), 2, true);
        }
        if (tier >= 25) {
            meta.addEnchant(getEnchantment("sharpness"), 5, true);
            meta.addEnchant(getEnchantment("looting"), 3, true);
            meta.addEnchant(getEnchantment("fire_aspect"), 1, true);
        }
        if (tier >= 35) {
            meta.addEnchant(getEnchantment("sweeping_edge"), 3, true);
        }
        if (tier >= 45) {
            meta.addEnchant(getEnchantment("fire_aspect"), 2, true);
            meta.addEnchant(getEnchantment("mending"), 1, true);
        }
        if (tier >= 55) {
            meta.addEnchant(getEnchantment("unbreaking"), 3, true);
        }
        if (tier >= 65) {
            meta.addEnchant(getEnchantment("knockback"), 2, true);
        }
        if (tier >= 75) {
            meta.addEnchant(getEnchantment("sharpness"), 10, true);
        }
        if (tier >= 85) {
            meta.addEnchant(getEnchantment("smite"), 5, true);
            meta.addEnchant(getEnchantment("bane_of_arthropods"), 5, true);
        }
        if (tier >= 95) {
            meta.addEnchant(getEnchantment("sweeping_edge"), 5, true);
        }
        if (tier >= 100) {
            // Épée surpuissante
            meta.addEnchant(getEnchantment("sharpness"), 10, true);
            meta.addEnchant(getEnchantment("looting"), 5, true);
            meta.addEnchant(getEnchantment("fire_aspect"), 3, true);
            meta.addEnchant(getEnchantment("sweeping_edge"), 10, true);
            meta.addEnchant(getEnchantment("unbreaking"), 7, true);
        }
        
        // Mettre à jour le nom avec le pallier
        String tierName = getTierName(tier);
        meta.setDisplayName("§6Cantalame §7[" + tierName + "]");
        
        // Mettre à jour le lore
        meta.setLore(java.util.List.of(
            "§7Kills: §e" + tier + "/100 §7(max 3/j/joueur)",
            "§7Puissance: " + getPowerDescription(tier)
        ));
        
        item.setItemMeta(meta);
    }

    private String getTierName(int tier) {
        if (tier < 5) return "§cDébutant";
        if (tier < 10) return "§eNovice";
        if (tier < 15) return "§aApprenti";
        if (tier < 20) return "§2Compétent";
        if (tier < 25) return "§bExpérimenté";
        if (tier < 35) return "§3Expert";
        if (tier < 45) return "§5Maître";
        if (tier < 55) return "§dGrand Maître";
        if (tier < 65) return "§6Légende";
        if (tier < 75) return "§fMythique";
        if (tier < 85) return "§cDivin";
        if (tier < 95) return "§4Démoniaque";
        if (tier < 100) return "§8Infernal";
        return "§l§4§lSURPUISSANT";
    }

    private String getPowerDescription(int tier) {
        if (tier < 5) return "§cFaible";
        if (tier < 10) return "§eMoyen";
        if (tier < 15) return "§aCorrect";
        if (tier < 20) return "§2Bon";
        if (tier < 25) return "§bFort";
        if (tier < 35) return "§3Très fort";
        if (tier < 45) return "§5Puissant";
        if (tier < 55) return "§dDestructeur";
        if (tier < 65) return "§6Terrifiant";
        if (tier < 75) return "§fApocalyptique";
        if (tier < 85) return "§cDivin";
        if (tier < 95) return "§4Démoniaque";
        if (tier < 100) return "§8Infernal";
        return "§l§4§lSURPUISSANT";
    }

    public ItemStack createCantalame() {
        ItemStack item = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        // Marquer comme Cantalame
        meta.getPersistentDataContainer().set(cantalameKey, PersistentDataType.STRING, "true");
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, 0);
        meta.getPersistentDataContainer().set(victimsKey, PersistentDataType.STRING, "");
        
        meta.setDisplayName("§6Cantalame §7[§cDébutant§7]");
        meta.setLore(java.util.List.of(
            "§7Kills uniques: §e0/100",
            "§7Puissance: §cFaible"
        ));
        
        meta.setUnbreakable(true);

        item.setItemMeta(meta);

        // Identité Cantale (tag PDC + hash anti-fake + CustomModelData) : sans elle,
        // l'épée échappait aux protections enclume/meule et n'avait pas sa texture.
        new CustomItemManager().applyCustomIdentity(item, CustomItemType.CANTALAME);

        return item;
    }

    public boolean isCantalame(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        return container.has(cantalameKey, PersistentDataType.STRING);
    }
}
