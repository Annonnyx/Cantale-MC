package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.util.VisualPacketUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Affiche au client pioche/hache/pelle/cisailles selon le bloc visé.
 * Ne modifie jamais l'inventaire serveur.
 * <p>
 * Important : on n'applique le CustomModelData que sur la forme pioche,
 * sinon le resource pack force la même texture multi-outil sur hache/pelle.
 */
public class MultiCantoolVisualAdapter implements Listener {
    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;
    private final Map<UUID, Material> lastVisual = new HashMap<>();

    public MultiCantoolVisualAdapter(CantalePlugin plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[Cantale] Multi-Cantool visual adapter activé.");
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateVisual(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        updateVisual(event.getPlayer());
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> updateVisual(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastVisual.remove(event.getPlayer().getUniqueId());
    }

    public void updateVisual(Player player) {
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
        Material visualMat;
        if (target == null || target.getType().isAir()) {
            visualMat = type.getMaterial();
        } else {
            visualMat = getVisualMaterial(target.getType(), type);
        }

        Material current = lastVisual.get(uuid);
        if (visualMat.equals(current)) return;

        VisualPacketUtil.sendVisualSlot(player, EquipmentSlot.HAND, createVisualItem(visualMat, hand));
        lastVisual.put(uuid, visualMat);
        plugin.getLogger().fine("[MultiCantool] " + player.getName() + " → " + visualMat.name());
    }

    private void restoreIfNeeded(Player player) {
        if (!lastVisual.containsKey(player.getUniqueId())) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        VisualPacketUtil.sendVisualSlot(player, EquipmentSlot.HAND,
                hand != null ? hand : new ItemStack(Material.AIR));
        lastVisual.remove(player.getUniqueId());
    }

    private Material getVisualMaterial(Material blockMaterial, CustomItemType type) {
        String matName = blockMaterial.name();
        boolean isNetherite = type.name().contains("NETHERITE");
        boolean isDiamond = type.name().contains("DIAMOND");

        if (isShearsName(matName)) {
            return Material.SHEARS;
        }
        if (isWoodName(matName)) {
            if (isNetherite) return Material.NETHERITE_AXE;
            if (isDiamond) return Material.DIAMOND_AXE;
            return Material.IRON_AXE;
        }
        if (isSoftName(matName)) {
            if (isNetherite) return Material.NETHERITE_SHOVEL;
            if (isDiamond) return Material.DIAMOND_SHOVEL;
            return Material.IRON_SHOVEL;
        }
        if (isNetherite) return Material.NETHERITE_PICKAXE;
        if (isDiamond) return Material.DIAMOND_PICKAXE;
        return Material.IRON_PICKAXE;
    }

    private static boolean isShearsName(String matName) {
        return matName.contains("LEAVES") || matName.contains("WOOL") || matName.contains("CARPET")
                || matName.contains("VINE") || matName.contains("FLOWER") || matName.contains("MUSHROOM")
                || matName.contains("FERN") || matName.contains("TALL_GRASS") || matName.contains("SHORT_GRASS")
                || matName.contains("LILY") || matName.contains("MOSS_BLOCK") || matName.contains("SPORE")
                || matName.contains("HANGING") || matName.contains("AZALEA") || matName.contains("BLOSSOM");
    }

    private static boolean isWoodName(String matName) {
        return matName.contains("LOG") || matName.contains("WOOD") || matName.contains("STEM")
                || matName.contains("PLANK") || matName.contains("FENCE") || matName.contains("DOOR");
    }

    private static boolean isSoftName(String matName) {
        return matName.contains("DIRT") || matName.contains("GRASS") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("CLAY") || matName.contains("PODZOL")
                || matName.contains("MYCELIUM") || matName.contains("FARMLAND") || matName.contains("SOUL_SAND")
                || matName.contains("SOUL_SOIL") || matName.contains("ROOTED") || matName.contains("MOSS");
    }

    private ItemStack createVisualItem(Material material, ItemStack original) {
        ItemStack visual = new ItemStack(material);
        if (original == null || !original.hasItemMeta()) return visual;

        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta visualMeta = visual.getItemMeta();
        if (visualMeta == null || originalMeta == null) return visual;

        if (originalMeta.hasDisplayName()) {
            visualMeta.setDisplayName(originalMeta.getDisplayName());
        }
        if (originalMeta.hasLore()) {
            visualMeta.setLore(originalMeta.getLore());
        }
        if (originalMeta.hasEnchants()) {
            originalMeta.getEnchants().forEach((ench, level) -> visualMeta.addEnchant(ench, level, true));
        }

        // CMD sur toutes les formes : le resource pack peut ainsi afficher la texture
        // multi-outil quelle que soit la forme (pioche/hache/pelle/cisailles).
        if (originalMeta.hasCustomModelData()) {
            visualMeta.setCustomModelData(originalMeta.getCustomModelData());
        }

        visual.setItemMeta(visualMeta);
        return visual;
    }
}
