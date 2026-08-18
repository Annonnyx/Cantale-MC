package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.quests.QuestPeriod;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class QuestMainUI extends BaseUI {

    public QuestMainUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Quêtes", 3);
    }

    @Override
    protected void init() {
        fillEmpty();
        setItem(11, Material.DIAMOND_SWORD, "§a🗡️ Quêtes Solo",
                "§7Quêtes individuelles",
                "§7• Journalières, Hebdomadaires, Saisonnières",
                "§eClic pour ouvrir le menu Solo");
        setItem(15, Material.SHIELD, "§b🛡️ Quêtes de Faction",
                "§7Defis et courses de faction",
                "§7• Journalières, Hebdomadaires, Saisonnières",
                "§eClic pour ouvrir le menu Faction");
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        NavigationManager nav = plugin.getUIManager().getNavigationManager();
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
        switch (slot) {
            case 11 -> nav.openUI(player, new QuestCategorySelectUI(plugin, player, fr.cantale.plugin.quests.QuestScope.SOLO));
            case 15 -> nav.openUI(player, new QuestCategorySelectUI(plugin, player, fr.cantale.plugin.quests.QuestScope.FACTION));
            default -> {
            }
        }
    }
}
