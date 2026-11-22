package fr.cantale.plugin.ui.inventory;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.factions.Faction;
import fr.cantale.plugin.factions.FactionWarp;
import fr.cantale.plugin.factions.FactionWarpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Warps de faction publics (FW). Clic gauche = TP, clic droit = supprimer (officiers+).
 */
public class FactionWarpsUI extends BaseUI {

    private final Faction faction;
    private final List<FactionWarp> warps = new ArrayList<>();
    private final boolean canManage;

    public FactionWarpsUI(CantalePlugin plugin, Player player, Faction faction) {
        super(plugin, player, "§8[§6Cantale§8] §7Warps — " + faction.getName(), 6);
        this.faction = faction;
        this.canManage = plugin.getFactionWarpManager().canManage(player, faction);
    }

    @Override
    protected void init() {
        fillEmpty();
        warps.clear();
        warps.addAll(plugin.getFactionWarpManager().getByFaction(faction.getId()));

        setItem(4, Material.ENDER_PEARL, "§6§lWarps publics (FW)",
            "§7" + warps.size() + "/" + faction.getMaxWarps() + " warps",
            "§7Formule : §e1 §7si pouvoir < 60,",
            "§7sinon §e1 + (pouvoir − 60) / 30",
            "§7Tout le serveur : §e/fw <nom>",
            "§7▶ Clic gauche §7: Téléporter",
            canManage ? "§7▶ Clic droit §7: Supprimer" : "§8Suppression : officiers+");

        int slot = 9;
        for (FactionWarp warp : warps) {
            if (slot >= 45) break;
            setItem(slot, Material.COMPASS, "§e" + warp.getName(),
                "§7Faction : §e" + warp.getFactionName(),
                "§7▶ Clic gauche §7: /fw " + warp.getName(),
                canManage ? "§7▶ Clic droit §7: Supprimer" : "§8Lecture seule");
            slot++;
        }

        if (warps.isEmpty()) {
            setItem(22, Material.PAPER, "§7Aucun warp public",
                "§7Officiers : §e/fw create <nom>",
                "§7dans un claim de la faction.");
        }

        if (canManage) {
            setItem(48, Material.LIME_WOOL, "§a§l+ Créer un FW",
                "§7/fw create <nom>",
                "§7Dans un claim, officiers+",
                "§7Cliquez puis tapez le nom");
            setItem(50, Material.RED_WOOL, "§c§lSupprimer",
                "§7Clic droit sur un warp",
                "§7ou §e/fw delete <nom>");
        } else {
            setItem(48, Material.GRAY_WOOL, "§cCréer un FW",
                "§7Officiers et chef uniquement",
                "§7/fw create <nom>");
        }
        setItem(47, Material.BOOK, "§eListe du serveur",
            "§7/fw",
            "§7Tous les warps de faction publics");
        addNavigationBar(true, true);
    }

    private boolean clientDelete;

    @Override
    protected void handleClick(int slot) {
        handleClick(slot, null);
    }

    @Override
    protected void handleClientClick(int slot, boolean right, boolean shift) {
        clientDelete = right || shift;
        try {
            handleClick(slot, null);
        } finally {
            clientDelete = false;
        }
    }

    @Override
    protected void handleClick(int slot, InventoryClickEvent event) {
        if (slot == 45) { plugin.getUIManager().goBack(player); return; }
        if (slot == 49) { close(); return; }
        if (slot == 53) { init(); return; }
        if (slot == 47) {
            close();
            player.performCommand("fw");
            return;
        }
        if (slot == 48) {
            close();
            if (!canManage) {
                player.sendMessage(FactionWarpManager.PREFIX + "§cSeuls les officiers et le chef peuvent créer un FW.");
                return;
            }
            player.sendMessage(FactionWarpManager.PREFIX + "§7Placez-vous dans un claim puis tapez §e/fw create <nom>§7.");
            player.sendMessage(Component.text("§e[Cliquer pour préremplir /fw create ]")
                    .clickEvent(ClickEvent.suggestCommand("/fw create ")));
            return;
        }
        if (slot == 50) {
            player.sendMessage(FactionWarpManager.PREFIX + "§7Clic droit sur un warp de la liste pour le supprimer, ou tapez §e/fw delete <nom>§7.");
            return;
        }

        int index = slot - 9;
        if (index < 0 || index >= warps.size()) return;
        FactionWarp warp = warps.get(index);
        boolean deleteClick = clientDelete || (event != null && (event.isRightClick() || event.isShiftClick()));
        if (deleteClick) {
            if (!canManage) {
                player.sendMessage(FactionWarpManager.PREFIX + "§cSeuls les officiers et le chef peuvent supprimer un FW.");
                return;
            }
            plugin.getUIManager().getNavigationManager().openUI(player,
                    new ConfirmDialogUI(plugin, player, "§7Supprimer le FW",
                            "Supprimer le warp public " + warp.getName() + " ?",
                            confirmed -> {
                                if (confirmed) plugin.getFactionWarpManager().delete(player, warp.getName());
                            }));
            return;
        }
        close();
        player.performCommand("fw " + warp.getName());
    }
}
