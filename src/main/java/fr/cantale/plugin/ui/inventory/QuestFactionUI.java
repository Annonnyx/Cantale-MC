package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.quests.GeneratedQuest;
import fr.cantale.plugin.quests.QuestEngine;
import fr.cantale.plugin.quests.QuestPeriod;
import fr.cantale.plugin.quests.QuestScope;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestFactionUI extends BaseUI {

    private List<GeneratedQuest> quests = List.of();

    public QuestFactionUI(CantalePlugin plugin, Player player) {
        super(plugin, player, "§8[§6Cantale§8] §7Quêtes faction", 6);
    }

    @Override
    protected void init() {
        fillEmpty();
        QuestEngine engine = plugin.getQuestEngine();
        List<GeneratedQuest> all = new ArrayList<>();
        for (QuestPeriod p : QuestPeriod.values()) {
            all.addAll(engine.pool(QuestScope.FACTION, p));
        }
        quests = all;
        Faction fac = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        int slot = 0;
        for (GeneratedQuest q : quests) {
            if (slot >= 45) break;
            Integer winner = engine.winner(q.id());
            int ours = fac == null ? 0 : engine.progress("f:" + fac.getId(), q.id());
            int pct = q.amount() <= 0 ? 0 : Math.min(100, ours * 100 / q.amount());
            List<Map.Entry<Integer, Integer>> rank = engine.factionRanking(q.id());
            String lead = "§7Personne";
            if (!rank.isEmpty()) {
                Faction leadFac = plugin.getFactionManager().getFaction(rank.get(0).getKey());
                String name = leadFac != null ? leadFac.getName() : ("#" + rank.get(0).getKey());
                int leadPct = q.amount() <= 0 ? 0 : Math.min(100, rank.get(0).getValue() * 100 / q.amount());
                lead = "§e" + name + " §7(" + leadPct + "%)";
            }
            Faction winnerFac = winner == null ? null : plugin.getFactionManager().getFaction(winner);
            String winnerLine = winner == null
                    ? "§aCourse en cours"
                    : "§6Gagné par §e" + (winnerFac != null ? winnerFac.getName() : "#" + winner);
            Material icon = winner != null ? Material.GOLD_BLOCK : Material.WRITABLE_BOOK;
            setItem(slot, icon, "§b" + q.title(),
                    "§7" + q.period().french(),
                    "§7Objectif : §f" + q.amount(),
                    "§7Ta fac : §f" + ours + " §8(" + pct + "%)",
                    winnerLine,
                    "§7Leader : " + lead,
                    "§7Pouvoir : §b+" + engine.config().powerFor(q.period()));
            slot++;
        }
        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == size - 9) plugin.getUIManager().goBack(player);
        else if (slot == size - 5) close();
        else if (slot == size - 1) open();
    }
}
