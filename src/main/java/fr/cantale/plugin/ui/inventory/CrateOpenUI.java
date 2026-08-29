package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateManager;
import fr.cantale.plugin.crate.CrateReward;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Menu coffre animé pour l'ouverture du Trésor Public (RARE).
 * Les items défilent au centre puis la récompense réelle est révélée.
 */
public class CrateOpenUI extends BaseUI {

    private static final int CENTER = 13;
    private static final int SPIN_TICKS = 28; // ~2.8s à 2 ticks
    private static final long PERIOD = 2L;

    private static final Material[] SPIN_POOL = {
            Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT, Material.EMERALD,
            Material.GOLDEN_APPLE, Material.EXPERIENCE_BOTTLE, Material.ENDER_PEARL,
            Material.TRIPWIRE_HOOK, Material.CHEST, Material.NETHERITE_INGOT
    };

    private final CrateManager crateManager;
    private final CrateType type;
    private final CrateReward reward;
    private BukkitTask spinTask;
    private boolean finished;
    private boolean rewardDelivered;

    public CrateOpenUI(CantalePlugin plugin, Player player, CrateManager crateManager,
                       CrateType type, CrateReward reward) {
        super(plugin, player, "&8⚔ &9Trésor Public &8⚔", 3);
        this.crateManager = crateManager;
        this.type = type;
        this.reward = reward;
    }

    @Override
    protected void init() {
        fillFrame();
        setItem(CENTER, Material.CHEST, "§9§lOuverture...", "§7La clef tourne dans la serrure...");
        startSpin();
    }

    private void fillFrame() {
        Material pane = Material.BLUE_STAINED_GLASS_PANE;
        for (int i = 0; i < size; i++) {
            if (i == CENTER) continue;
            setItem(i, pane, " ");
        }
        // Accents coins
        setItem(0, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        setItem(8, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        setItem(18, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        setItem(26, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
    }

    private void startSpin() {
        final int[] tick = {0};
        spinTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || finished) {
                cancelSpin();
                return;
            }

            tick[0]++;
            if (tick[0] <= SPIN_TICKS) {
                Material mat = SPIN_POOL[ThreadLocalRandom.current().nextInt(SPIN_POOL.length)];
                ItemStack preview = new ItemStack(mat);
                ItemMeta meta = preview.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GRAY + "???");
                    meta.setLore(List.of(ChatColor.DARK_GRAY + "En cours de tirage..."));
                    preview.setItemMeta(meta);
                }
                inventory.setItem(CENTER, preview);
                float pitch = 0.5f + (tick[0] / (float) SPIN_TICKS) * 1.5f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, pitch);
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&',
                        "&7Récompense en cours de sélection... &e" + spinner(tick[0])));
            } else {
                reveal();
            }
        }, 0L, PERIOD);
    }

    private void reveal() {
        cancelSpin();
        finished = true;

        ItemStack display = buildRewardDisplay();
        inventory.setItem(CENTER, display);

        // Contours verts pour la révélation
        Material win = Material.LIME_STAINED_GLASS_PANE;
        for (int i : new int[]{4, 12, 14, 22}) {
            setItem(i, win, "§a§l✦");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        deliverReward();

        // Ferme le menu après un court délai pour laisser voir la récompense
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
            }
        }, 60L);
    }

    private ItemStack buildRewardDisplay() {
        ItemStack display;
        if (reward.hasItems() && reward.items().get(0) != null) {
            display = reward.items().get(0).clone();
        } else if (reward.lives() > 0) {
            display = new ItemStack(Material.TOTEM_OF_UNDYING);
        } else if (reward.cantox() > 0) {
            display = new ItemStack(Material.GOLD_INGOT);
        } else {
            display = new ItemStack(Material.CHEST);
        }

        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            String title = reward.message() != null ? reward.message() : "§e" + reward.name();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Récompense du Trésor Public");
            if (reward.cantox() > 0) {
                lore.add(ChatColor.YELLOW + "+" + reward.cantox() + " Cantox");
            }
            if (reward.lives() > 0) {
                lore.add(ChatColor.RED + "+" + reward.lives() + " Vie(s)");
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private void deliverReward() {
        if (rewardDelivered) return;
        rewardDelivered = true;
        crateManager.deliverReward(player, type, reward, player.getLocation());
        crateManager.clearOpening(player);
    }

    private void cancelSpin() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
    }

    private static String spinner(int tick) {
        return switch (tick % 4) {
            case 0 -> "/";
            case 1 -> "—";
            case 2 -> "\\";
            default -> "|";
        };
    }

    @Override
    protected void handleClick(int slot) {
        // Menu purement visuel — aucun clic utile
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        if (!event.getPlayer().equals(player)) return;

        // Ne PAS délivrer ni relâcher le verrou ici : un shift-clic droit / ÉCHAP
        // fermait le menu et donnait la récompense immédiatement, ce qui permettait
        // d'enchaîner toute une stack de clefs sans animation. Le spin continue
        // en arrière-plan ; reveal() délivre au bout du délai prévu.
        super.onClose(event);
    }
}
