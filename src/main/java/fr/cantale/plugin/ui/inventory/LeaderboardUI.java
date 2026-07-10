package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class LeaderboardUI extends BaseUI {

    public LeaderboardUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Classements", 3);
    }

    @Override
    protected void init() {
        fillEmpty();

        setItem(4, Material.BOOK, "§6§lClassements Généraux", "§7Sélectionnez une catégorie à afficher");

        setItem(10, Material.DIAMOND_SWORD, "§c§lTop Kills", "§7Les joueurs ayant éliminé", "§7le plus d'adversaires.", "", "§eCliquez pour voir");
        setItem(12, Material.SKELETON_SKULL, "§4§lTop Morts", "§7Les joueurs ayant péri", "§7le plus grand nombre de fois.", "", "§eCliquez pour voir");
        setItem(14, Material.GOLD_INGOT, "§6§lTop Richesse", "§7Les joueurs possédant", "§7le plus de Cantox.", "", "§eCliquez pour voir");
        setItem(16, Material.SHIELD, "§b§lTop Factions", "§7Les factions les plus", "§7puissantes et riches.", "", "§eCliquez pour voir");

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
        switch (slot) {
            case 10 -> nav.openUI(player, new LeaderboardCategoryUI(plugin, player, "KILLS"));
            case 12 -> nav.openUI(player, new LeaderboardCategoryUI(plugin, player, "DEATHS"));
            case 14 -> nav.openUI(player, new LeaderboardCategoryUI(plugin, player, "RICHEST"));
            case 16 -> nav.openUI(player, new LeaderboardCategoryUI(plugin, player, "FACTIONS"));
        }
    }
}
