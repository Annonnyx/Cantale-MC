package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.collections.CollectionCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class CollectionCategoryUI extends BaseUI {

    private final CollectionCategory category;

    public CollectionCategoryUI(CantalePlugin plugin, Player player, CollectionCategory category) {
        super(plugin, player, "§8[§6Cantale§8] §7" + category.label(), 6);
        this.category = category;
    }

    @Override
    protected void init() {
        fillEmpty();
        int slot = 0;
        for (String key : category.keys()) {
            if (slot >= 45) break;
            long amt = plugin.getCollectionManager().get(player.getUniqueId(), category.id(), key);
            int next = nextTier(amt);
            setItem(slot, Material.PAPER, "§e" + key,
                    "§7Progression : §f" + amt,
                    next >= 0 ? "§7Prochain palier : §f" + category.tiers().get(next) : "§aTous les paliers atteints",
                    "§7Clique pour voir / réclamer");
            slot++;
        }
        addNavigationBar(true, true);
    }

    private int nextTier(long amt) {
        for (int i = 0; i < category.tiers().size(); i++) {
            if (amt < category.tiers().get(i)) return i;
        }
        return -1;
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
        if (slot < 0 || slot >= category.keys().size()) return;
        String key = category.keys().get(slot);
        plugin.getUIManager().getNavigationManager()
                .openUI(player, new CollectionTiersUI(plugin, player, category, key));
    }
}
