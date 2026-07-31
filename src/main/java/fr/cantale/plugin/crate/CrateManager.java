package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class CrateManager {

    private static final NamespacedKey CRATE_TYPE_KEY = new NamespacedKey("cantale", "crate_type");
    private final CantalePlugin plugin;
    private final CustomItemManager itemManager;
    private final CrateRewardTable rewardTable;

    public CrateManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getCustomItemManager();
        this.rewardTable = new CrateRewardTable(itemManager);
    }

    public ItemStack createCrateItem(CrateType type) {
        Material material = switch (type) {
            case VOTE -> Material.PAPER;
            case RARE -> Material.TRIPWIRE_HOOK;
            case EPIC -> Material.SUNFLOWER;
            case MYTHIC -> Material.GOLD_NUGGET;
            case LEGENDARY -> Material.WRITABLE_BOOK;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getItemName()));
            meta.setLore(List.of(
                    ChatColor.translateAlternateColorCodes('&', type.getDefaultLore()),
                    "",
                    ChatColor.YELLOW + "Clic-droit pour ouvrir"
            ));
            meta.getPersistentDataContainer().set(CRATE_TYPE_KEY, PersistentDataType.STRING, type.name());

            int modelData = switch (type) {
                case VOTE -> 100;
                case RARE -> 101;
                case EPIC -> 102;
                case MYTHIC -> 103;
                case LEGENDARY -> 104;
            };
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    public CrateType getCrateType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(CRATE_TYPE_KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return CrateType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isCrateItem(ItemStack item) {
        return getCrateType(item) != null;
    }

    /**
     * Ouvre une caisse avec animation.
     */
    public void openCrate(Player player, CrateType type) {
        openCrate(player, type, type);
    }

    /**
     * Ouvre une caisse avec animation, en conservant la caisse d'origine pour
     * déterminer si la grande ascension (Vote → Légendaire) est réussie.
     */
    private void openCrate(Player player, CrateType type, CrateType originalType) {
        if (type == CrateType.LEGENDARY) {
            // Le grade Chèvre est accordé si le joueur a gravi toute la chaîne depuis Vote (étape par étape).
            if (originalType == CrateType.VOTE) {
                grantGoatRank(player);
            } else {
                plugin.getLegendaryTicketManager().onTicketWon(player);
            }
            return;
        }

        // Vérifier si la caisse monte d'un cran en rareté
        CrateType upgradedType = rewardTable.rollUpgrade(type);
        if (upgradedType != null) {
            handleUpgrade(player, type, upgradedType, originalType);
            return;
        }

        Location loc = player.getLocation();
        player.sendMessage("§8[§6Cantale§8] §7Ouverture de §r" + type.getItemName() + " §7...");

        // Animation: 3 secondes
        final int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            tick[0]++;

            if (tick[0] <= 6) {
                player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f + tick[0] * 0.1f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&7Récompense en cours de sélection... &e" + getSpinner(tick[0])));
            } else {
                task.cancel();
                finishOpen(player, type, loc);
            }
        }, 0L, 10L);
    }

    private void handleUpgrade(Player player, CrateType from, CrateType to, CrateType originalType) {
        Location loc = player.getLocation();

        // Ascension : la caisse se transforme en caisse physique supérieure
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&d&l✦ ASCENSION ✦"),
                ChatColor.translateAlternateColorCodes('&', "&7" + from.getDisplayName() + " &f→ &7" + to.getDisplayName()),
                10, 60, 20
        );
        player.sendMessage("§8[§6Cantale§8] §d✦ Ascension ! §7Ton " + from.getItemName() + " §7se transforme en " + to.getItemName() + "§7 !");
        player.sendMessage("§8[§6Cantale§8] §7Clic-droit dessus pour l'ouvrir.");

        ItemStack upgradedCrate = createCrateItem(to);
        var leftover = player.getInventory().addItem(upgradedCrate);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(loc, drop);
        }

        // Vote ne monte plus directement jusqu'à Légendaire : ce cas n'est plus atteignable.
    }

    private void grantGoatRank(Player player) {
        Location loc = player.getLocation();
        player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5);

        var currentRank = plugin.getRankManager().getRank(player);
        if (currentRank != fr.cantale.plugin.rank.PlayerRank.CHEVRE) {
            plugin.getRankManager().setRank(player.getUniqueId(), fr.cantale.plugin.rank.PlayerRank.CHEVRE);
        }

        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&6&lGRADE CHÈVRE !"),
                ChatColor.translateAlternateColorCodes('&', "&eLa chance t'a élu GØAT de Cantale !"),
                10, 100, 20
        );
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§8§m--------------------------------------------------");
        Bukkit.broadcastMessage("§6§l⚡ ASCENSION LÉGENDAIRE ⚡");
        Bukkit.broadcastMessage("§e" + player.getName() + " §7a ouvert un simple §fCadeau du Roi §7et a obtenu le grade §6§lChèvre§7 !");
        Bukkit.broadcastMessage("§8§m--------------------------------------------------");
    }

    private void finishOpen(Player player, CrateType type, Location loc) {
        CrateReward reward = rewardTable.roll(type);

        // Cantox
        if (reward.cantox() > 0) {
            plugin.getEconomyManager().deposit(player, reward.cantox());
        }

        java.util.List<ItemStack> surplus = new java.util.ArrayList<>();

        // Vies
        for (int i = 0; i < reward.lives(); i++) {
            var leftover = player.getInventory().addItem(itemManager.createCustomItem(CustomItemType.VIE));
            surplus.addAll(leftover.values());
        }

        // Items
        if (reward.hasItems()) {
            for (ItemStack item : reward.items()) {
                if (item == null) continue;
                var leftover = player.getInventory().addItem(item.clone());
                surplus.addAll(leftover.values());
            }
        }

        // Stocker le surplus dans le coffre privé, sinon jeter par terre
        if (!surplus.isEmpty()) {
            java.util.List<ItemStack> stillSurplus = plugin.getPrivateChestService().storeItemsInPrivateChest(player.getUniqueId(), surplus);
            for (ItemStack drop : stillSurplus) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItemNaturally(loc, drop);
                }
            }
        }

        // Effets
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5);

        // Message
        String display = reward.message() != null
                ? reward.message()
                : buildRewardMessage(reward);
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&6Récompense !"),
                ChatColor.translateAlternateColorCodes('&', display),
                10, 70, 20
        );
        player.sendMessage("§8[§6Cantale§8] §7Tu as obtenu : §r" + display);

        // Annonce publique uniquement pour les caisses mythique et légendaire
        if (type == CrateType.MYTHIC || type == CrateType.LEGENDARY) {
            Bukkit.broadcastMessage("§8[§6Cantale§8] §e" + player.getName() + " §7a ouvert un §r" + type.getItemName() + " §7et a obtenu : §r" + display);
        }
    }

    private String buildRewardMessage(CrateReward reward) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e").append(reward.name());
        if (reward.cantox() > 0) {
            sb.append(" §7(§e").append(reward.cantox()).append(" Cantox§7)");
        }
        if (reward.lives() > 0) {
            sb.append(" §c+").append(reward.lives()).append(" Vie(s)");
        }
        if (reward.hasItems()) {
            sb.append(" §7+ ").append(reward.items().size()).append(" item(s)");
        }
        return sb.toString();
    }

    private String getSpinner(int tick) {
        return switch (tick % 4) {
            case 0 -> "/";
            case 1 -> "—";
            case 2 -> "\\";
            default -> "|";
        };
    }
}
