package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.quests.GeneratedQuest;
import fr.cantale.plugin.quests.QuestEngine;
import fr.cantale.plugin.quests.QuestPeriod;
import fr.cantale.plugin.quests.QuestScope;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestSoloUI extends BaseUI {

    private final QuestPeriod period;
    private List<GeneratedQuest> quests = List.of();

    public QuestSoloUI(CantalePlugin plugin, Player player, QuestPeriod period) {
        super(plugin, player, "§8[§6Cantale§8] §7" + period.french(), 4);
        this.period = period;
    }

    @Override
    protected void init() {
        fillEmpty();
        QuestEngine engine = plugin.getQuestEngine();
        quests = engine.pool(QuestScope.SOLO, period);
        String accepted = engine.acceptedId(player.getUniqueId(), period);
        int slot = 10;
        for (GeneratedQuest q : quests) {
            if (slot == 17) slot = 19;
            boolean done = engine.isCompleted(player.getUniqueId().toString(), q.id());
            boolean active = q.id().equals(accepted);
            int prog = engine.progress(player.getUniqueId().toString(), q.id());
            Material icon = done ? Material.LIME_CONCRETE : (active ? Material.YELLOW_CONCRETE : Material.BOOK);
            String status = done ? "§aTerminée" : (active ? "§eEn cours — clique pour rien" : "§7Clique pour accepter");
            setItem(slot, icon, (active ? "§e" : "§f") + q.title(),
                    "§7Objectif : §f" + q.amount(),
                    "§7Progression : §f" + prog + "§8/§f" + q.amount(),
                    status,
                    q.bonusCantox() > 0 ? "§6Bonus : " + q.bonusCantox() + " Cantox" : "§8",
                    q.bonusLives() > 0 ? "§cBonus : +1 vie" : "§8");
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
        int index = indexFromSlot(slot);
        if (index < 0 || index >= quests.size()) return;
        plugin.getQuestEngine().acceptSolo(player, quests.get(index).id());
        open();
    }

    private static int indexFromSlot(int slot) {
        if (slot >= 10 && slot <= 16) return slot - 10;
        if (slot >= 19 && slot <= 25) return 7 + (slot - 19);
        return -1;
    }
}
