package fr.cantale.plugin.siege;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class SiegeBanners {

    public static final String KEY = "siege_banner";

    private SiegeBanners() {
    }

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = banner.getItemMeta();
        meta.displayName(Component.text("Bannière de siège", NamedTextColor.RED));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        banner.setItemMeta(meta);
        return banner;
    }

    public static boolean isSiegeBanner(Plugin plugin, ItemStack stack) {
        if (plugin == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        if (!stack.getType().name().endsWith("BANNER")) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
