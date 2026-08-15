package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.collections.CollectionCategory;
import fr.cantale.plugin.collections.CollectionReward;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class CollectionTiersUI extends BaseUI {

    private final CollectionCategory category;
    private final String key;

    public CollectionTiersUI(CantalePlugin plugin, Player player, CollectionCategory category, String key) {
        super(plugin, player, "§8[§6Cantale§8] §7" + key, 4);
        this.category = category;
        this.key = key;
    }

    @Override
    protected void init() {
        fillEmpty();
        long amt = plugin.getCollectionManager().get(player.getUniqueId(), category.id(), key);
        for (int i = 0; i < category.tiers().size() && i < 21; i++) {
            int need = category.tiers().get(i);
            boolean done = amt >= need;
            boolean claimed = plugin.getCollectionManager().isClaimed(player.getUniqueId(), category.id(), key, i);
            CollectionReward r = i < category.rewards().size() ? category.rewards().get(i) : null;
            Material icon = claimed ? Material.LIME_DYE : (done ? Material.YELLOW_DYE : Material.GRAY_DYE);
            setItem(10 + i, icon, "§ePalier " + (i + 1),
                    "§7Requis : §f" + need,
                    "§7Toi : §f" + amt,
                    claimed ? "§aRéclamé" : (done ? "§eClique pour réclamer" : "§cPas encore"),
                    r == null ? "§8" : ("§6" + r.cantox() + " Cantox"
                            + (r.key() != null ? " §8+ §f" + r.key().name() : "")
                            + (r.lives() > 0 ? " §8+ §c" + r.lives() + " vie" : "")
                            + (r.spawner() != null ? " §8+ §dSpawner " + r.spawner().frenchName() : "")));
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
        int i = slot - 10;
        if (i < 0 || i >= category.tiers().size()) return;
        plugin.getCollectionManager().claim(player, category.id(), key, i);
        open();
    }
}
