package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionRank;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Menu d'actions sur un membre : promote / demote / kick.
 */
public class MemberActionUI extends BaseUI {

    private final Faction faction;
    private final UUID targetUuid;
    private final String targetName;

    public MemberActionUI(CantalePlugin plugin, Player player, Faction faction, UUID target, String targetName) {
        super(plugin, player, "§8[§6Cantale§8] §7Actions — " + targetName, 3);
        this.faction = faction;
        this.targetUuid = target;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        fillEmpty();

        FactionRank rank = faction.getMemberRank(targetUuid);
        setItem(4, Material.PLAYER_HEAD, "§6§l" + targetName,
            "§7Grade actuel : §6" + rank.getDisplayName());

        boolean isLeader = faction.isLeader(player.getUniqueId());
        FactionRank myRank = faction.getMemberRank(player.getUniqueId());

        if (isLeader) {
            setItem(11, Material.GOLDEN_HELMET, "§a§l▲ Promouvoir",
                "§7Augmenter le grade",
                "§7Recrue → Membre → Officier");
            setItem(13, Material.IRON_HELMET, "§e§l▼ Rétrograder",
                "§7Diminuer le grade");
            setItem(15, Material.BARRIER, "§c§l✕ Expulser",
                "§7Retirer définitivement",
                "§7" + targetName + " de la faction");
        } else if (myRank.isAtLeast(FactionRank.OFFICER)) {
            setItem(13, Material.BARRIER, "§c§l✕ Expulser",
                "§7Retirer " + targetName,
                "§7de la faction");
        } else {
            setItem(13, Material.PAPER, "§7Aucune action disponible",
                "§7Vous n'avez pas la permission.");
        }

        addNavigationBar(true, false);
    }

    @Override
    protected void handleClick(int slot) {
        switch (slot) {
            case 11 -> { close(); player.performCommand("f promote " + targetName); }
            case 13 -> {
                if (faction.isLeader(player.getUniqueId())) {
                    close();
                    player.performCommand("f demote " + targetName);
                } else {
                    close();
                    player.performCommand("f kick " + targetName);
                }
            }
            case 15 -> { close(); player.performCommand("f kick " + targetName); }
            case 49 -> close();
        }
    }
}
