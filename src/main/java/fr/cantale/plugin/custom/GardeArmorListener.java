package fr.cantale.plugin.custom;

import fr.cantale.plugin.CantalePlugin;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Applique / retire les bonus de set Armure du Garde via AttributeModifier joueur.
 * Chaque pièce = 25 % du set complet (+12 HP, +30 % speed, +25 % jump).
 */
public class GardeArmorListener implements Listener {

    private static final double FULL_HEALTH = 12.0;   // +6 cœurs
    private static final double FULL_SPEED = 0.30;
    private static final double FULL_JUMP = 0.25;
    private static final double PIECE_FRACTION = 0.25;

    private static final NamespacedKey KEY_HEALTH = new NamespacedKey("cantale", "garde_health");
    private static final NamespacedKey KEY_SPEED = new NamespacedKey("cantale", "garde_speed");
    private static final NamespacedKey KEY_JUMP = new NamespacedKey("cantale", "garde_jump");

    private final CustomItemManager customItemManager;

    public GardeArmorListener(CantalePlugin plugin) {
        this.customItemManager = plugin.getCustomItemManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(CantalePlugin.getInstance(),
                () -> refreshBonuses(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearBonuses(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(CantalePlugin.getInstance(),
                () -> refreshBonuses(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearBonuses(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Bukkit.getScheduler().runTaskLater(CantalePlugin.getInstance(),
                () -> refreshBonuses(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTaskLater(CantalePlugin.getInstance(),
                () -> refreshBonuses(player), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTaskLater(CantalePlugin.getInstance(),
                () -> refreshBonuses(player), 1L);
    }

    public void refreshBonuses(Player player) {
        if (player == null || !player.isOnline()) return;
        int pieces = countGardePieces(player.getInventory());
        applyBonuses(player, pieces);
    }

    private int countGardePieces(PlayerInventory inv) {
        int count = 0;
        if (isGardePiece(inv.getHelmet())) count++;
        if (isGardePiece(inv.getChestplate())) count++;
        if (isGardePiece(inv.getLeggings())) count++;
        if (isGardePiece(inv.getBoots())) count++;
        return count;
    }

    private boolean isGardePiece(ItemStack item) {
        if (item == null || !customItemManager.isCustomItem(item)) return false;
        CustomItemType type = customItemManager.getCustomItemType(item);
        return type != null && type.isGardeArmor();
    }

    private void applyBonuses(Player player, int pieces) {
        clearBonuses(player);
        if (pieces <= 0) return;

        double fraction = pieces * PIECE_FRACTION;
        addModifier(player, Attribute.MAX_HEALTH, KEY_HEALTH, FULL_HEALTH * fraction, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(player, Attribute.MOVEMENT_SPEED, KEY_SPEED, FULL_SPEED * fraction, AttributeModifier.Operation.ADD_SCALAR);
        addModifier(player, Attribute.JUMP_STRENGTH, KEY_JUMP, FULL_JUMP * fraction, AttributeModifier.Operation.ADD_SCALAR);

        // Clamp current health if max decreased (shouldn't here, but safe on re-equip)
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void clearBonuses(Player player) {
        removeModifier(player, Attribute.MAX_HEALTH, KEY_HEALTH);
        removeModifier(player, Attribute.MOVEMENT_SPEED, KEY_SPEED);
        removeModifier(player, Attribute.JUMP_STRENGTH, KEY_JUMP);
    }

    private void addModifier(Player player, Attribute attribute, NamespacedKey key,
                             double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || amount == 0) return;
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }

    private void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            instance.removeModifier(existing);
        }
    }
}
