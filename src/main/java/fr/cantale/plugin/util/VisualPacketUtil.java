package fr.cantale.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * Utilitaire d'envoi de packets visuels.
 * <p>
 * En Paper 1.21.4, l'envoi de changement d'item en main se fait via
 * {@link Player#sendEquipmentChange(EquipmentSlot, ItemStack)} qui reste
 * purement visuel côté client sans toucher à l'inventaire serveur.
 */
public class VisualPacketUtil {

    private static final boolean AVAILABLE;

    static {
        boolean available;
        try {
            Player.class.getMethod("sendEquipmentChange", LivingEntity.class, EquipmentSlot.class, ItemStack.class);
            available = true;
        } catch (NoSuchMethodException e) {
            Bukkit.getLogger().log(Level.WARNING, "[Cantale] Player#sendEquipmentChange unavailable, visual adapter disabled.");
            available = false;
        }
        AVAILABLE = available;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Met à jour visuellement le contenu d'un slot d'équipement pour le joueur.
     *
     * @param player le joueur concerné
     * @param slot   le slot d'équipement (ex: {@link EquipmentSlot#HAND})
     * @param item   l'item à afficher (peut être null ou AIR)
     */
    public static void sendVisualSlot(Player player, EquipmentSlot slot, ItemStack item) {
        if (!AVAILABLE) return;
        player.sendEquipmentChange(player, slot, item != null ? item : new ItemStack(org.bukkit.Material.AIR));
    }
}
