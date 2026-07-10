package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Liste des membres de la faction. Clic gauche = promote, clic droit = demote (leader uniquement),
 * shift+clic = kick.
 */
public class FactionMembersUI extends BaseUI {

    private final Faction faction;
    private final List<UUID> memberList = new ArrayList<>();

    public FactionMembersUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Membres — " + faction.getName(), 6);
        this.faction = faction;
    }

    @Override
    protected void init() {
        fillEmpty();
        memberList.clear();
        memberList.addAll(faction.getMembers());

        setItem(4, Material.PLAYER_HEAD, "§6§l" + faction.getName(),
            "§7Membres : §e" + faction.getMemberCount(),
            "§7Power : §e" + faction.getPower());

        int slot = 9;
        for (UUID uuid : memberList) {
            if (slot >= 45) break;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            FactionRank rank = faction.getMemberRank(uuid);
            boolean online = offline.isOnline();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offline);
                String displayName = (faction.isLeader(uuid) ? "§6👑 " : "§e") + offline.getName();
                meta.setDisplayName(displayName);
                List<String> lore = new ArrayList<>();
                lore.add("§7Grade : §6" + rank.getDisplayName());
                lore.add("§7Statut : " + (online ? "§aen ligne" : "§7hors-ligne"));
                lore.add("");
                if (faction.isLeader(player.getUniqueId()) && !faction.isLeader(uuid)) {
                    lore.add("§a▶ Clic gauche §7: Promouvoir");
                    lore.add("§e▶ Clic droit §7: Rétrograder");
                    lore.add("§c▶ Shift+Clic §7: Expulser");
                } else if (faction.getMemberRank(player.getUniqueId()).isAtLeast(FactionRank.OFFICER)
                        && !faction.isLeader(uuid) && !uuid.equals(player.getUniqueId())) {
                    lore.add("§c▶ Shift+Clic §7: Expulser");
                }
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            setItem(slot, head);
            slot++;
        }

        addNavigationBar(true, true);
    }

    @Override
    protected void handleClick(int slot) {
        if (slot == 45) { plugin.getUIManager().goBack(player); return; }
        if (slot == 49) { close(); return; }
        if (slot == 53) { init(); return; }

        int index = slot - 9;
        if (index < 0 || index >= memberList.size()) return;
        UUID target = memberList.get(index);
        if (target.equals(player.getUniqueId())) {
            player.sendMessage("§cVous ne pouvez pas agir sur vous-même via ce menu.");
            return;
        }
        String targetName = Bukkit.getOfflinePlayer(target).getName();
        if (targetName == null) return;

        // Les actions sont déléguées au /f command qui gère permissions et logique
        // Note : impossible de connaître le type de clic via slot seul, donc on propose un menu d'actions
        plugin.getUIManager().getNavigationManager().openUI(player, new MemberActionUI(plugin, player, faction, target, targetName));
    }
}
