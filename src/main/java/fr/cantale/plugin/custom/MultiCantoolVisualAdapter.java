package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.NMSUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MultiCantoolVisualAdapter {
    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final Map<UUID, Material> lastVisual = new HashMap<>();

    public MultiCantoolVisualAdapter(CantalePlugin plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    public void start() {
        if (!NMSUtil.isAvailable()) {
            plugin.getLogger().warning("[Cantale] NMS not available, Multi-Cantool visual adapter disabled.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void processPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!customItemManager.isCustomItem(hand)) {
            restoreIfNeeded(player);
            return;
        }

        CustomItemType type = customItemManager.getCustomItemType(hand);
        if (type == null || !type.isMultiCantool()) {
            restoreIfNeeded(player);
            return;
        }

        Block target = player.getTargetBlockExact(5);
        Material visualMat = (target == null) ? null : getVisualMaterial(target.getType(), type);

        Material current = lastVisual.get(uuid);

        if (visualMat == null) {
            if (current != null) {
                restore(player);
                lastVisual.remove(uuid);
            }
        } else if (!visualMat.equals(current)) {
            ItemStack visualItem = createVisualItem(visualMat, hand);
            int nmsSlot = 36 + player.getInventory().getHeldItemSlot();
            NMSUtil.sendVisualSlot(player, nmsSlot, visualItem);
            lastVisual.put(uuid, visualMat);
        }
    }

    private void restoreIfNeeded(Player player) {
        if (lastVisual.containsKey(player.getUniqueId())) {
            restore(player);
            lastVisual.remove(player.getUniqueId());
        }
    }

    private void restore(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int nmsSlot = 36 + player.getInventory().getHeldItemSlot();
        NMSUtil.sendVisualSlot(player, nmsSlot, hand != null ? hand : new ItemStack(Material.AIR));
    }

    private Material getVisualMaterial(Material blockMaterial, CustomItemType type) {
        String matName = blockMaterial.name();
        boolean isNetherite = type.name().contains("NETHERITE");
        boolean isDiamond = type.name().contains("DIAMOND");

        if (matName.contains("LEAVES") || matName.contains("WOOL") || matName.contains("CARPET")
                || matName.contains("VINE") || matName.contains("FLOWER") || matName.contains("MUSHROOM")
                || matName.contains("FERN") || matName.contains("TALL_GRASS") || matName.contains("SHORT_GRASS")
                || matName.contains("LILY") || matName.contains("MOSS_BLOCK") || matName.contains("SPORE")
                || matName.contains("HANGING") || matName.contains("AZALEA") || matName.contains("BLOSSOM")) {
            return Material.SHEARS;
        }

        if (matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM")
                || matName.contains("PLANK") || matName.contains("FENCE") || matName.contains("DOOR")) {
            if (isNetherite) return Material.NETHERITE_AXE;
            if (isDiamond) return Material.DIAMOND_AXE;
            return Material.IRON_AXE;
        }

        if (matName.contains("DIRT") || matName.contains("GRASS") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("CLAY") || matName.contains("PODZOL")
                || matName.contains("MYCELIUM") || matName.contains("FARMLAND") || matName.contains("SOUL_SAND")
                || matName.contains("SOUL_SOIL") || matName.contains("ROOTED") || matName.contains("MOSS")) {
            if (isNetherite) return Material.NETHERITE_SHOVEL;
            if (isDiamond) return Material.DIAMOND_SHOVEL;
            return Material.IRON_SHOVEL;
        }

        if (isNetherite) return Material.NETHERITE_PICKAXE;
        if (isDiamond) return Material.DIAMOND_PICKAXE;
        return Material.IRON_PICKAXE;
    }

    private ItemStack createVisualItem(Material material, ItemStack original) {
        ItemStack visual = new ItemStack(material);
        if (original != null && original.hasItemMeta()) {
            ItemMeta originalMeta = original.getItemMeta();
            ItemMeta visualMeta = visual.getItemMeta();
            if (visualMeta != null) {
                if (originalMeta.hasDisplayName()) {
                    visualMeta.setDisplayName(originalMeta.getDisplayName());
                }
                if (originalMeta.hasLore()) {
                    visualMeta.setLore(originalMeta.getLore());
                }
                if (originalMeta.hasEnchants()) {
                    originalMeta.getEnchants().forEach((ench, level) -> {
                        if (visualMeta.addEnchant(ench, level, true)) { /* ok */ }
                    });
                }
                visual.setItemMeta(visualMeta);
            }
        }
        return visual;
    }
}
