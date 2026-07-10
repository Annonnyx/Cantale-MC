package fr.cantale.plugin.listeners;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.custom.CustomItemManager;
import fr.cantale.plugin.custom.CustomItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents players from using vanilla Totem of Undying.
 * Only custom VIE items (with PDC auth) are allowed to function.
 * Vanilla totems are removed from inventories, drops, and prevented from resurrecting.
 */
public class TotemBlockListener implements Listener {

    private final CantalePlugin plugin;
    private final CustomItemManager customItemManager;

    public TotemBlockListener(CantalePlugin plugin) {
        this.plugin = plugin;
        this.customItemManager = plugin.getCustomItemManager();
    }

    /**
     * Block vanilla totem resurrection.
     * Custom VIE items are NOT totems — they don't trigger resurrection.
     * If a player has a vanilla totem in hand/offhand, cancel the resurrection.
     * If a player has a custom VIE item in the resurrecting hand, also cancel
     * and consume the item so death proceeds normally (handled by PlayerDeathEvent).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Block vanilla totem
        if (isVanillaTotem(mainHand) || isVanillaTotem(offHand)) {
            event.setCancelled(true);
            plugin.getLogger().info("Blocked vanilla totem resurrection for " + player.getName());
            return;
        }

        // Block custom VIE item from acting as a totem — consume it and let death happen
        org.bukkit.inventory.EquipmentSlot hand = event.getHand();
        if (hand != null) {
            ItemStack item = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                    ? offHand : mainHand;
            if (isCustomVieItem(item)) {
                event.setCancelled(true);
                // Consume the VIE item so it doesn't trigger again
                item.setAmount(item.getAmount() - 1);
                plugin.getLogger().info("Blocked custom VIE totem resurrection for " + player.getName());
            }
        }
    }

    /**
     * Remove vanilla totems from player inventory on join.
     * Custom VIE items (with PDC) are kept.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean removed = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isVanillaTotem(item)) {
                item.setAmount(0);
                removed = true;
            }
        }
        // Also check ender chest
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isVanillaTotem(item)) {
                item.setAmount(0);
                removed = true;
            }
        }

        if (removed) {
            player.sendMessage("§8[§6Cantale§8] §cLes Totems of Undying vanilla ne sont pas autorisés. Ils ont été retirés de votre inventaire.");
            plugin.getLogger().info("Removed vanilla totems from " + player.getName());
        }
    }

    /**
     * Block right-click use of vanilla totem.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isVanillaTotem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§8[§6Cantale§8] §cLes Totems of Undying vanilla sont interdits sur Cantale.");
        }
    }

    /**
     * Check if an item is a vanilla Totem of Undying (NOT a custom VIE item).
     * Returns true only for totems WITHOUT the custom PDC tag.
     */
    private boolean isVanillaTotem(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != org.bukkit.Material.TOTEM_OF_UNDYING) return false;

        // If it's a custom VIE item, it's NOT a vanilla totem
        if (isCustomVieItem(item)) return false;

        // Vanilla totem — blocked
        return true;
    }

    /**
     * Check if an item is a custom VIE (life) item.
     */
    private boolean isCustomVieItem(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != org.bukkit.Material.TOTEM_OF_UNDYING) return false;
        if (!customItemManager.isCustomItem(item)) return false;
        CustomItemType type = customItemManager.getCustomItemType(item);
        return type == CustomItemType.VIE;
    }
}
