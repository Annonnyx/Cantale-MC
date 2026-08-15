package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.collections.CollectionCategory;
import org.bukkit.entity.Player;

public class CollectionMainUI extends BaseUI {

    public CollectionMainUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Collections", 4);
    }

    @Override
    protected void init() {
        fillEmpty();
        int slot = 10;
        for (CollectionCategory cat : plugin.getCollectionManager().config().categories().values()) {
            if (cat.hidden()) continue;
            if (slot == 17) slot = 19;
            setItem(slot, cat.icon(), "§e" + cat.label(),
                    "§7" + cat.keys().size() + " sous-catégorie(s)",
                    "§7Paliers long terme");
            slot++;
        }
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == size - 9) {
            plugin.getUIManager().goBack(player);
            return;
        }
        if (slot == size - 5) {
            close();
            return;
        }
        if (slot == size - 1) {
            open();
            return;
        }
        int i = 0;
        for (CollectionCategory cat : plugin.getCollectionManager().config().categories().values()) {
            if (cat.hidden()) continue;
            int s = 10 + i;
            if (i >= 7) s = 19 + (i - 7);
            if (s == slot) {
                plugin.getUIManager().getNavigationManager()
                        .openUI(player, new CollectionCategoryUI(plugin, player, cat));
                return;
            }
            i++;
        }
    }
}
