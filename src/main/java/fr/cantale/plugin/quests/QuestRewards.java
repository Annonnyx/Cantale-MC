package fr.cantale.plugin.quests;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.crate.CrateType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class QuestRewards {

    private QuestRewards() {}

    public static void givePeriodKey(CantalePlugin plugin, Player player, QuestPeriod period) {
        CrateType type = switch (period) {
            case DAILY -> CrateType.RARE;
            case WEEKLY -> CrateType.EPIC;
            case SEASONAL -> CrateType.MYTHIC;
        };
        giveCrate(plugin, player, type);
    }

    public static void giveCrate(CantalePlugin plugin, Player player, CrateType type) {
        ItemStack key = plugin.getCrateManager().createCrateItem(type);
        var leftover = player.getInventory().addItem(key);
        leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        player.sendMessage("§8[§6Quêtes§8] §aTu reçois : §r" + type.getItemName());
    }
}
