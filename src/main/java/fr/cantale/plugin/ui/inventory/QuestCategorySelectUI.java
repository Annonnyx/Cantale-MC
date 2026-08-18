package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.quests.QuestPeriod;
import fr.cantale.plugin.quests.QuestScope;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class QuestCategorySelectUI extends BaseUI {

    private final QuestScope scope;

    public QuestCategorySelectUI(CantalePlugin plugin, Player player, QuestScope scope) {
        super(plugin, player, "§8[§6Cantale§8] §7Quêtes " + (scope == QuestScope.SOLO ? "Solo" : "Faction"), 3);
        this.scope = scope;
    }

    @Override
    protected void init() {
        fillEmpty();
        if (scope == QuestScope.SOLO) {
            setItem(11, Material.SUNFLOWER, "§eJournalières",
                    "§7Récompense : §fclé rare",
                    "§7Set complet : §eclé épique",
                    "§eClic pour afficher");
            setItem(13, Material.CLOCK, "§6Hebdomadaires",
                    "§7Récompense : §eclé épique",
                    "§7Set complet : §dclé mythique",
                    "§eClic pour afficher");
            setItem(15, Material.NETHER_STAR, "§dSaisonnières",
                    "§7Récompense : §dclé mythique",
                    "§7Set complet : §6ticket légendaire",
                    "§eClic pour afficher");
        } else {
            setItem(11, Material.SUNFLOWER, "§eJournalières Faction",
                    "§7Pouvoir : §b+1/quête",
                    "§7Récompenses collectives Cantox",
                    "§eClic pour afficher");
            setItem(13, Material.CLOCK, "§6Hebdomadaires Faction",
                    "§7Pouvoir : §b+7/quête",
                    "§7Récompenses collectives & Spawner chance",
                    "§eClic pour afficher");
            setItem(15, Material.NETHER_STAR, "§dSaisonnières Faction",
                    "§7Pouvoir : §b+200/quête",
                    "§7Grands trophées de saison",
                    "§eClic pour afficher");
        }
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

        QuestPeriod p = switch (slot) {
            case 11 -> QuestPeriod.DAILY;
            case 13 -> QuestPeriod.WEEKLY;
            case 15 -> QuestPeriod.SEASONAL;
            default -> null;
        };

        if (p != null) {
            if (scope == QuestScope.SOLO) {
                nav.openUI(player, new QuestSoloUI(plugin, player, p));
            } else {
                nav.openUI(player, new QuestFactionUI(plugin, player, p));
            }
        }
    }
}
