package fr.cantale.plugin.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Envoi de packets d'équipement purement visuels (Paper 1.21+).
 * Inventaire serveur inchangé.
 */
public class VisualPacketUtil {

    public static boolean isAvailable() {
        return true;
    }

    public static void sendVisualSlot(Player player, EquipmentSlot slot, ItemStack item) {
        ItemStack safe = item != null ? item : new ItemStack(org.bukkit.Material.AIR);
        // Paper 1.21.11 : signature (LivingEntity, EquipmentSlot, ItemStack)
        player.sendEquipmentChange(player, slot, safe);
    }
}
