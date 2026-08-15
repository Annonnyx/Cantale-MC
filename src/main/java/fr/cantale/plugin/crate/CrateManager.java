package fr.cantale.plugin.crate;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import fr.cantale.plugin.ui.inventory.CrateOpenUI;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CrateManager {

    private static final NamespacedKey CRATE_TYPE_KEY = new NamespacedKey("cantale", "crate_type");
    private final CantalePlugin plugin;
    private final CustomItemManager itemManager;
    private final CrateRewardTable rewardTable;
    private final CrateConfig crateConfig;
    private final Set<UUID> openingPlayers = new HashSet<>();

    public CrateManager(CantalePlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getCustomItemManager();
        this.rewardTable = new CrateRewardTable(plugin, itemManager);
        this.crateConfig = new CrateConfig(plugin);
        this.crateConfig.load();
    }

    public CrateConfig getCrateConfig() {
        return crateConfig;
    }

    public void reloadConfig() {
        crateConfig.load();
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

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', type.getDefaultLore()));
            lore.add("");
            for (String line : type.getUsageLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

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

    public boolean isOpening(Player player) {
        return openingPlayers.contains(player.getUniqueId());
    }

    public void clearOpening(Player player) {
        openingPlayers.remove(player.getUniqueId());
    }

    public CrateRewardTable getRewardTable() {
        return rewardTable;
    }

    public void openCrate(Player player, CrateType type) {
        openCrate(player, type, type, player.getLocation(), OpenStyle.AUTO);
    }

    public void openMailbox(Player player, CrateType type) {
        openCrate(player, type, type, player.getLocation(), OpenStyle.INSTANT);
    }

    public void openWithChestUI(Player player, CrateType type) {
        openCrate(player, type, type, player.getLocation(), OpenStyle.CHEST_UI);
    }

    /** MYTHIC uniquement — offrande aquatique après jet dans l'eau. */
    public void openWaterOffer(Player player, CrateType type, Location waterLoc) {
        openCrate(player, type, type, waterLoc, OpenStyle.WATER_MYTHIC);
    }

    /** EPIC uniquement — rituel sur balise / table d'enchantement (≠ eau). */
    public void openRitual(Player player, CrateType type, Location altarLoc) {
        openCrate(player, type, type, altarLoc, OpenStyle.RITUAL_EPIC);
    }

    public void openTicket(Player player, CrateType type) {
        openCrate(player, type, type, player.getLocation(), OpenStyle.TICKET);
    }

    private enum OpenStyle {
        AUTO, INSTANT, CHEST_UI, WATER_MYTHIC, RITUAL_EPIC, TICKET
    }

    private void openCrate(Player player, CrateType type, CrateType originalType, Location effectLoc, OpenStyle style) {
        if (isOpening(player)) {
            player.sendMessage("§8[§6Cantale§8] §cTu ouvres déjà une caisse...");
            return;
        }

        if (type == CrateType.LEGENDARY || style == OpenStyle.TICKET) {
            if (plugin.getCollectionManager() != null) {
                plugin.getCollectionManager().onCrateOpened(player.getUniqueId());
            }
            if (originalType == CrateType.VOTE) {
                grantGoatRank(player);
            } else {
                plugin.getLegendaryTicketManager().onTicketWon(player);
            }
            return;
        }

        CrateType upgradedType = rewardTable.rollUpgrade(type);
        if (upgradedType != null) {
            handleUpgrade(player, type, upgradedType, effectLoc);
            return;
        }

        OpenStyle resolved = style == OpenStyle.AUTO ? resolveStyle(type) : style;

        switch (resolved) {
            case INSTANT -> openInstant(player, type, effectLoc);
            case CHEST_UI -> openChestUI(player, type);
            case WATER_MYTHIC -> openWithWaterEffects(player, type, effectLoc);
            case RITUAL_EPIC -> openWithRitualEffects(player, type, effectLoc);
            default -> openWithActionBarAnimation(player, type, effectLoc);
        }
    }

    private OpenStyle resolveStyle(CrateType type) {
        return switch (type.getInteraction()) {
            case MAILBOX -> OpenStyle.INSTANT;
            case RIGHT_CLICK_ANIMATED -> OpenStyle.CHEST_UI;
            case THROW_WATER -> OpenStyle.WATER_MYTHIC;
            case RITUAL_ALTAR -> OpenStyle.RITUAL_EPIC;
            case RIGHT_CLICK_TICKET -> OpenStyle.TICKET;
        };
    }

    private void openInstant(Player player, CrateType type, Location loc) {
        openingPlayers.add(player.getUniqueId());
        player.sendMessage("§8[§6Cantale§8] §7Le courrier royal s'ouvre...");
        player.playSound(loc, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 15, 0.4, 0.4, 0.4);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                clearOpening(player);
                return;
            }
            finishOpen(player, type, loc);
            clearOpening(player);
        }, 15L);
    }

    private void openChestUI(Player player, CrateType type) {
        openingPlayers.add(player.getUniqueId());
        CrateReward reward = rewardTable.roll(type);
        player.sendMessage("§8[§6Cantale§8] §7Tu ouvres le §r" + type.getItemName() + "§7...");
        new CrateOpenUI(plugin, player, this, type, reward).open();
    }

    private void openWithWaterEffects(Player player, CrateType type, Location loc) {
        openingPlayers.add(player.getUniqueId());
        player.sendMessage("§8[§6Cantale§8] §dTa Pièce Mythique plonge dans l'eau...");
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&d&lOFFRANDE MYTHIQUE"),
                ChatColor.translateAlternateColorCodes('&', "&7L'eau accepte ta pièce..."),
                5, 40, 10
        );
        player.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);
        player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.2, 0), 40, 0.4, 0.3, 0.4);
        loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 0.3, 0), 20, 0.3, 0.3, 0.3, 0.02);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.8, 0), 15, 0.4, 0.4, 0.4);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                clearOpening(player);
                return;
            }
            finishOpen(player, type, player.getLocation());
            clearOpening(player);
        }, 25L);
    }

    private void openWithRitualEffects(Player player, CrateType type, Location loc) {
        openingPlayers.add(player.getUniqueId());
        player.sendMessage("§8[§6Cantale§8] §5✦ Ta Médaille du Tournoi est consacrée sur l'autel...");
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&5&lRITUEL ÉPIQUE"),
                ChatColor.translateAlternateColorCodes('&', "&7L'autel écoute ton offrande..."),
                5, 40, 10
        );
        player.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.6f);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 40, 0.4, 0.6, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1.0, 0), 50, 0.5, 0.5, 0.5, 0.5);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.3, 0), 25, 0.3, 0.4, 0.3, 0.02);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                clearOpening(player);
                return;
            }
            finishOpen(player, type, player.getLocation());
            clearOpening(player);
        }, 25L);
    }

    private void openWithActionBarAnimation(Player player, CrateType type, Location loc) {
        openingPlayers.add(player.getUniqueId());
        player.sendMessage("§8[§6Cantale§8] §7Ouverture de §r" + type.getItemName() + " §7...");

        final int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                clearOpening(player);
                return;
            }
            tick[0]++;

            if (tick[0] <= 6) {
                player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f + tick[0] * 0.1f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&',
                        "&7Récompense en cours de sélection... &e" + getSpinner(tick[0])));
            } else {
                task.cancel();
                finishOpen(player, type, loc);
                clearOpening(player);
            }
        }, 0L, 10L);
    }

    private void handleUpgrade(Player player, CrateType from, CrateType to, Location loc) {
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&d&l✦ ASCENSION ✦"),
                ChatColor.translateAlternateColorCodes('&', "&7" + from.getDisplayName() + " &f→ &7" + to.getDisplayName()),
                10, 60, 20
        );
        player.sendMessage("§8[§6Cantale§8] §d✦ Ascension ! §7Ton " + from.getItemName() + " §7se transforme en " + to.getItemName() + "§7 !");
        player.sendMessage("§8[§6Cantale§8] " + to.getUsageHint());

        ItemStack upgradedCrate = createCrateItem(to);
        var leftover = player.getInventory().addItem(upgradedCrate);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(loc, drop);
        }
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

    void finishOpen(Player player, CrateType type, Location loc) {
        CrateReward reward = rewardTable.roll(type);
        deliverReward(player, type, reward, loc);
    }

    public void deliverReward(Player player, CrateType type, CrateReward reward, Location loc) {
        if (plugin.getCollectionManager() != null) {
            plugin.getCollectionManager().onCrateOpened(player.getUniqueId());
        }
        if (reward.cantox() > 0) {
            plugin.getEconomyManager().deposit(player, reward.cantox());
        }

        List<ItemStack> surplus = new ArrayList<>();

        for (int i = 0; i < reward.lives(); i++) {
            var leftover = player.getInventory().addItem(itemManager.createCustomItem(CustomItemType.VIE));
            surplus.addAll(leftover.values());
        }

        if (reward.hasItems()) {
            for (ItemStack item : reward.items()) {
                if (item == null) continue;
                var leftover = player.getInventory().addItem(item.clone());
                surplus.addAll(leftover.values());
            }
        }

        if (!surplus.isEmpty()) {
            List<ItemStack> stillSurplus = plugin.getPrivateChestService().storeItemsInPrivateChest(player.getUniqueId(), surplus);
            for (ItemStack drop : stillSurplus) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItemNaturally(loc, drop);
                }
            }
        }

        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5);

        String display = reward.message() != null
                ? reward.message()
                : buildRewardMessage(reward);
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&6Récompense !"),
                ChatColor.translateAlternateColorCodes('&', display),
                10, 70, 20
        );
        player.sendMessage("§8[§6Cantale§8] §7Tu as obtenu : §r" + display);

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

    public void sendUsageHint(Player player, CrateType type) {
        player.sendMessage("§8[§6Cantale§8] " + type.getUsageHint());
    }
}
