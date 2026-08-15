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
        setItem(11, Material.SUNFLOWER, "§eJournalières",
                "§7Une à la fois", "§7Récompense : §fclef rare",
                "§7Set complet : §eclé épique");
        setItem(13, Material.CLOCK, "§6Hebdomadaires",
                "§7Une à la fois", "§7Récompense : §eclé épique",
                "§7Set complet : §dclé mythique");
        setItem(15, Material.NETHER_STAR, "§dSaisonnières",
                "§7Une à la fois", "§7Récompense : §dclé mythique",
                "§7Set complet : §6ticket légendaire");
        setItem(4, Material.SHIELD, "§bQuêtes de faction",
                "§7Courses partagées", "§7La plus rapide gagne");
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
            case 4 -> nav.openUI(player, new QuestFactionUI(plugin, player));
            case 11 -> nav.openUI(player, new QuestSoloUI(plugin, player, QuestPeriod.DAILY));
            case 13 -> nav.openUI(player, new QuestSoloUI(plugin, player, QuestPeriod.WEEKLY));
            case 15 -> nav.openUI(player, new QuestSoloUI(plugin, player, QuestPeriod.SEASONAL));
            default -> {
            }
        }
    }
}
