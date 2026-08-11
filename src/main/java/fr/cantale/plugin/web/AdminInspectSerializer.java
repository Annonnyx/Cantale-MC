package fr.cantale.plugin.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.List;

/** Sérialise inventaires / effets pour l'admin web (JSON compact, slots non vides seulement). */
final class AdminInspectSerializer {

    private static final int MAX_NEST = 2;

    private AdminInspectSerializer() {}

    static JsonArray items(ItemStack[] contents) {
        return items(contents, 0);
    }

    private static JsonArray items(ItemStack[] contents, int depth) {
        JsonArray arr = new JsonArray();
        if (contents == null) return arr;
        for (int i = 0; i < contents.length; i++) {
            JsonObject slot = item(contents[i], i, depth);
            if (slot != null) arr.add(slot);
        }
        return arr;
    }

    static JsonObject item(ItemStack stack, int slot) {
        return item(stack, slot, 0);
    }

    private static JsonObject item(ItemStack stack, int slot, int depth) {
        if (stack == null || stack.getType().isAir()) return null;
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        o.addProperty("material", stack.getType().name());
        o.addProperty("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            o.addProperty("name", ChatColor.stripColor(meta.getDisplayName()));
        }
        if (depth < MAX_NEST && meta instanceof BlockStateMeta bsm
                && bsm.hasBlockState()
                && bsm.getBlockState() instanceof ShulkerBox box) {
            o.addProperty("container", "shulker");
            o.add("contents", items(box.getInventory().getContents(), depth + 1));
        } else if (depth < MAX_NEST && meta instanceof BundleMeta bundle) {
            o.addProperty("container", "bundle");
            List<ItemStack> nested = bundle.getItems();
            JsonArray arr = new JsonArray();
            for (int i = 0; i < nested.size(); i++) {
                JsonObject nestedItem = item(nested.get(i), i, depth + 1);
                if (nestedItem != null) arr.add(nestedItem);
            }
            o.add("contents", arr);
        }
        return o;
    }

    static JsonArray effects(Collection<PotionEffect> active) {
        JsonArray arr = new JsonArray();
        if (active == null) return arr;
        for (PotionEffect effect : active) {
            if (effect == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("type", effect.getType().getKey().getKey());
            o.addProperty("amplifier", effect.getAmplifier());
            o.addProperty("duration_ticks", effect.getDuration());
            arr.add(o);
        }
        return arr;
    }
}
